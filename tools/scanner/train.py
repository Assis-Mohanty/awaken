"""Fine-tunes EfficientNet-Lite0 on FoodBD to name the foods on a plate (multi-label: one photo, several foods).

    python train.py --data <FoodBD folder holding train/ valid/ test/ data.yaml> --out <work folder> [--epochs 30]

Preprocessing matches the app (`LiteRtDishClassifier`): the whole photo squashed to 224 x 224, no crop, no aspect fix, pixels
scaled to (v - 127.5) / 127.5. Scores are per-class sigmoids, so the app's 0.15 floor means "at least 15 % sure it is there".
Reports, per split, the app's own measure: is a food that is really on the plate among the 3 best guesses.
"""
import argparse, json, math, random, re, time
from pathlib import Path

import numpy as np
import timm
import torch
import torch.nn.functional as F
from PIL import Image

SIZE, STORE = 224, 256

def class_names(yaml_path):
    m = re.search(r"names:\s*\[(.*?)\]", Path(yaml_path).read_text(encoding="utf-8"), re.S)
    return [n.strip().strip("'\"") for n in m.group(1).split(",")]

def load_split(root, split, n_classes):
    xs, ys = [], []
    for img in sorted((root / split / "images").glob("*.jpg")):
        lbl = root / split / "labels" / (img.stem + ".txt")
        y = np.zeros(n_classes, np.float32)
        if lbl.exists():
            for line in lbl.read_text(encoding="utf-8").splitlines():
                p = line.split()
                if p: y[int(p[0])] = 1.0
        im = Image.open(img).convert("RGB").resize((STORE, STORE), Image.BILINEAR)   # squash, like the app
        xs.append(np.asarray(im)); ys.append(y)
    return torch.from_numpy(np.stack(xs)).permute(0, 3, 1, 2).contiguous(), torch.from_numpy(np.stack(ys))

def prep(x_uint8):
    return (x_uint8.float() - 127.5) / 127.5

def augment(x, dev):
    """x: uint8 batch (B,3,STORE,STORE) on the GPU. Random zoom crop, flip, brightness and contrast."""
    out = []
    for img in x:
        s = int(STORE * random.uniform(0.72, 1.0)); i = random.randint(0, STORE - s); j = random.randint(0, STORE - s)
        c = img[:, i:i + s, j:j + s].unsqueeze(0).float()
        c = F.interpolate(c, size=(SIZE, SIZE), mode="bilinear", align_corners=False, antialias=True)
        if random.random() < 0.5: c = c.flip(3)
        c = (c - 127.5) * random.uniform(0.75, 1.25) + 127.5 + random.uniform(-25, 25)
        out.append(c.clamp(0, 255))
    return (torch.cat(out) - 127.5) / 127.5

def evaluate(model, x, y, dev, bs=128):
    model.eval(); scores = []
    with torch.no_grad(), torch.autocast("cuda", dtype=torch.float16):
        for k in range(0, len(x), bs):
            b = x[k:k + bs].to(dev).float()
            b = F.interpolate(b, size=(SIZE, SIZE), mode="bilinear", align_corners=False, antialias=True)
            scores.append(torch.sigmoid(model(prep_f(b))).float().cpu())
    return torch.cat(scores)

def prep_f(b):
    return (b - 127.5) / 127.5

def metrics(scores, y, floor=0.15):
    top3 = scores.topk(3, dim=1).indices
    hit = torch.gather(y, 1, top3).amax(1)                       # a real food among the 3 best guesses
    shown = (torch.gather(scores, 1, top3) >= floor).float()
    hit_floor = (torch.gather(y, 1, top3) * shown).amax(1)       # same, after the app's score floor
    precision3 = torch.gather(y, 1, top3).mean(1)
    return {"top3_hit": hit.mean().item(), "top3_hit_after_floor": hit_floor.mean().item(), "top3_precision": precision3.mean().item(),
            "shown_share": (shown.amax(1)).mean().item()}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True); ap.add_argument("--out", required=True)
    ap.add_argument("--epochs", type=int, default=30); ap.add_argument("--batch", type=int, default=48); ap.add_argument("--lr", type=float, default=1e-3)
    a = ap.parse_args()
    random.seed(7); torch.manual_seed(7)
    root, out = Path(a.data), Path(a.out); out.mkdir(parents=True, exist_ok=True)
    names = class_names(root / "data.yaml"); n = len(names)
    t0 = time.time()
    data = {s: load_split(root, s, n) for s in ("train", "valid", "test")}
    print({s: tuple(v[0].shape) for s, v in data.items()}, f"loaded in {time.time() - t0:.0f}s", flush=True)
    dev = torch.device("cuda")
    model = timm.create_model("tf_efficientnet_lite0", pretrained=True, num_classes=n, drop_rate=0.25).to(dev)
    # class imbalance: rarer foods get a larger positive weight (capped), so the 10 thin classes are not simply ignored
    freq = data["train"][1].sum(0).clamp(min=1)
    pos_weight = (data["train"][1].shape[0] / freq / 8).clamp(1.0, 6.0).to(dev)
    loss_fn = torch.nn.BCEWithLogitsLoss(pos_weight=pos_weight)
    opt = torch.optim.AdamW(model.parameters(), lr=a.lr, weight_decay=1e-2)
    steps = a.epochs * math.ceil(len(data["train"][0]) / a.batch)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=a.lr, total_steps=steps, pct_start=0.15)
    scaler = torch.amp.GradScaler("cuda")
    xtr, ytr = data["train"]; best = -1.0
    for ep in range(1, a.epochs + 1):
        model.train(); perm = torch.randperm(len(xtr)); run = 0.0
        for k in range(0, len(perm) - a.batch + 1, a.batch):
            idx = perm[k:k + a.batch]
            xb = augment(xtr[idx].to(dev), dev); yb = ytr[idx].to(dev)
            with torch.autocast("cuda", dtype=torch.float16):
                loss = loss_fn(model(xb).float(), yb)
            opt.zero_grad(set_to_none=True); scaler.scale(loss).backward(); scaler.step(opt); scaler.update(); sched.step()
            run += loss.item()
        v = metrics(evaluate(model, *data["valid"], dev), data["valid"][1])
        print(f"epoch {ep:2d}  loss {run / (len(perm) // a.batch):.4f}  valid top3_hit {v['top3_hit']:.3f}  after-floor {v['top3_hit_after_floor']:.3f}  precision {v['top3_precision']:.3f}", flush=True)
        if v["top3_hit_after_floor"] + v["top3_precision"] > best:
            best = v["top3_hit_after_floor"] + v["top3_precision"]; torch.save(model.state_dict(), out / "best.pt")
    model.load_state_dict(torch.load(out / "best.pt"))
    report = {s: metrics(evaluate(model, *data[s], dev), data[s][1]) for s in ("valid", "test")}
    # per-class recall on the held-out test split: how often a food that is on the plate is among the top 3
    sc = evaluate(model, *data["test"], dev); top3 = sc.topk(3, dim=1).indices; yt = data["test"][1]
    present = yt.sum(0); caught = torch.zeros(n)
    for i in range(len(yt)):
        for c in top3[i].tolist():
            if yt[i, c] > 0: caught[c] += 1
    report["test_per_class_recall_at_3"] = {names[c]: [int(present[c].item()), round((caught[c] / present[c]).item(), 2)] for c in range(n) if present[c] > 0}
    (out / "report.json").write_text(json.dumps(report, indent=1), encoding="utf-8")
    (out / "classes.json").write_text(json.dumps(names), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("valid", "test")}, indent=1), flush=True)

if __name__ == "__main__":
    main()
