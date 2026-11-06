/* AWAKEN design mockups: icons and charts. Direction ids: A = Ledger, B = Meridian, C = Moss. */
const ICONS = {
  home: '<path class="d" d="M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/>',
  focus: '<circle class="d" cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/>',
  dumbbell: '<path d="M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12"/>',
  bowl: '<path class="d" d="M4 11h16a8 8 0 0 1-16 0z"/><path d="M9 7c0-1 1-2 1-3M14 7c0-1 1-2 1-3"/>',
  bars: '<path d="M5 20V11M12 20V5M19 20v-7"/>',
  gear: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle class="d" cx="15" cy="7" r="2.2"/><circle class="d" cx="9" cy="17" r="2.2"/>',
  chev: '<path d="M9 6l6 6-6 6"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  play: '<path class="d" d="M8 5l11 7-11 7z"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  flame: '<path class="d" d="M12 3c1 4 5 5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-5 1-9z"/>',
  bolt: '<path class="d" d="M13 3L5 13h6l-1 8 8-10h-6z"/>',
  book: '<path class="d" d="M5 4h10a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3z"/><path d="M5 17a3 3 0 0 1 3-3h10"/>',
  heart: '<path class="d" d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>',
  people: '<circle class="d" cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.5"/><path d="M3 19c0-3 3-5 6-5s6 2 6 5M15 14c3 0 6 1.5 6 5"/>',
  lock: '<rect class="d" x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  phone: '<rect class="d" x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/>',
  clock: '<circle class="d" cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  moon: '<path class="d" d="M20 14A8 8 0 1 1 10 4a6.5 6.5 0 0 0 10 10z"/>',
  steps: '<circle class="d" cx="13" cy="4.5" r="1.6"/><path d="M10 21l2-6 3 3v4M9 12l3-3 3 3 2 1M12 9l-2 4"/>',
  shield: '<path class="d" d="M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  x: '<path d="M6 6l12 12M18 6L6 18"/>',
  skip: '<path class="d" d="M6 5l9 7-9 7z"/><path d="M18 5v14"/>',
  coin: '<circle class="d" cx="12" cy="12" r="9"/><path d="M9 12h6M12 9v6"/>',
  cal: '<rect class="d" x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M9 3v4M15 3v4"/>',
  repeat: '<path d="M4 12a8 8 0 0 1 14-5l2 2M20 12a8 8 0 0 1-14 5l-2-2M20 4v5h-5M4 20v-5h5"/>',
  quit: '<rect class="d" x="5" y="17" width="11" height="3"/><path d="M18 17v3M9 13c0-2 2-2 2-4M13 13c0-2 2-2 2-4"/>',
  widget: '<rect class="d" x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
  theme: '<circle cx="12" cy="12" r="9"/><path class="d" d="M12 3a9 9 0 0 0 0 18z"/>',
  data: '<ellipse class="d" cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3"/>',
  user: '<circle class="d" cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-6 8-6s8 2 8 6"/>',
  bell: '<path class="d" d="M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z"/><path d="M10 21h4"/>',
  sun: '<circle class="d" cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4"/>',
  swap: '<path d="M4 8h14l-3-3M20 16H6l3 3"/>',
};
const ic = (n, s = 22) => `<svg class="ic" viewBox="0 0 24 24" width="${s}" height="${s}" fill="none" stroke="currentColor" aria-hidden="true">${ICONS[n]}</svg>`;

const STATS = [
  { k: 'Strength', ic: 'bolt', v: 14, p: 11 },
  { k: 'Intelligence', ic: 'book', v: 22, p: 19 },
  { k: 'Willpower', ic: 'focus', v: 17, p: 15 },
  { k: 'Vitality', ic: 'heart', v: 12, p: 12 },
  { k: 'Charisma', ic: 'people', v: 8, p: 7 },
];
const XP_WEEKS = [320, 410, 380, 520, 460, 610, 570, 690];

/* Circular progress. */
function ring(D, pct, size, inner) {
  const sw = { A: 3, B: 9, C: 13 }[D];
  const r = (size - sw) / 2 - (D === 'B' ? 6 : 0), c = 2 * Math.PI * r;
  const arc = D === 'B' ? c * 0.75 : c; // B is a 270 degree gauge
  const rot = D === 'B' ? 135 : -90;
  const cap = D === 'A' ? 'butt' : 'round';
  const ticks = D === 'B'
    ? `<circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 2}" fill="none" stroke="var(--ink3)" stroke-width="3" stroke-dasharray="1 7.2" stroke-opacity=".45" transform="rotate(135 ${size / 2} ${size / 2})" stroke-dashoffset="0" pathLength="360" style="stroke-dasharray:1 5.6"/>` : '';
  return `<div class="ringw" style="width:${size}px;height:${size}px"><svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="var(--track)" stroke-width="${sw}" stroke-linecap="${cap}" stroke-dasharray="${arc} ${c}" transform="rotate(${rot} ${size / 2} ${size / 2})"/>
    <circle class="ringv" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="var(--ac)" stroke-width="${sw}" stroke-linecap="${cap}" stroke-dasharray="${arc * pct} ${c}" transform="rotate(${rot} ${size / 2} ${size / 2})"/>${ticks}
  </svg><div class="ringin">${inner}</div></div>`;
}

/* Horizontal progress bar. */
const bar = (pct, cls = '') => `<div class="bar ${cls}"><i style="width:${Math.round(pct * 100)}%"></i></div>`;

/* Five-stat radar. */
function radar(D, size = 250) {
  const cx = size / 2, cy = size / 2 + 4, R = size / 2 - 38, n = 5;
  const pt = (i, f) => { const a = -Math.PI / 2 + (i * 2 * Math.PI) / n; return [cx + Math.cos(a) * R * f, cy + Math.sin(a) * R * f]; };
  const poly = (vals) => vals.map((v, i) => pt(i, v / 25).map((x) => x.toFixed(1)).join(',')).join(' ');
  const grid = [0.25, 0.5, 0.75, 1].map((f) => D === 'C'
    ? `<circle cx="${cx}" cy="${cy}" r="${R * f}" fill="none" stroke="var(--track)" stroke-width="1.5"/>`
    : `<polygon points="${[0, 1, 2, 3, 4].map((i) => pt(i, f).join(',')).join(' ')}" fill="none" stroke="var(--track)" stroke-width="1"/>`).join('');
  const spokes = D === 'B' ? [0, 1, 2, 3, 4].map((i) => `<line x1="${cx}" y1="${cy}" x2="${pt(i, 1)[0]}" y2="${pt(i, 1)[1]}" stroke="var(--track)"/>`).join('') : '';
  const now = STATS.map((s) => s.v), prev = STATS.map((s) => s.p);
  const fill = { A: 'none', B: 'var(--ac)', C: 'var(--ac)' }[D];
  const fo = { A: 0, B: 0.16, C: 0.22 }[D];
  const sw = { A: 1.6, B: 1.8, C: 3 }[D];
  const dots = now.map((v, i) => { const [x, y] = pt(i, v / 25); return `<circle cx="${x}" cy="${y}" r="${D === 'C' ? 4.5 : 3}" fill="${D === 'A' ? 'var(--ink)' : 'var(--ac)'}"/>`; }).join('');
  const labels = STATS.map((s, i) => { const [x, y] = pt(i, 1.2); return `<text x="${x}" y="${y + 4}" text-anchor="middle" class="rl">${s.k.slice(0, 3)}</text>`; }).join('');
  return `<svg class="radar" width="${size}" height="${size + 8}" viewBox="0 0 ${size} ${size + 8}" role="img" aria-label="Stat balance">${grid}${spokes}
    <polygon points="${poly(prev)}" fill="none" stroke="var(--ink3)" stroke-width="1.4" stroke-dasharray="4 4" stroke-linejoin="round"/>
    <polygon points="${poly(now)}" fill="${fill}" fill-opacity="${fo}" stroke="${D === 'A' ? 'var(--ink)' : 'var(--ac)'}" stroke-width="${sw}" stroke-linejoin="round"/>${dots}${labels}</svg>`;
}

/* Weekly XP trend: line (A), bars (B), smooth area (C). */
function trend(D, w = 350, h = 150) {
  const max = 760, pad = 12, n = XP_WEEKS.length, lw = w - pad * 2;
  const X = (i) => pad + (lw / (n - 1)) * i, Y = (v) => h - 22 - (v / max) * (h - 44);
  const labels = XP_WEEKS.map((_, i) => `<text x="${D === 'B' ? pad + (lw / n) * (i + 0.5) : X(i)}" y="${h - 4}" text-anchor="middle" class="rl">W${i + 1}</text>`).join('');
  const last = XP_WEEKS[n - 1];
  if (D === 'B') {
    const bw = (lw / n) * 0.62;
    const bars = XP_WEEKS.map((v, i) => { const x = pad + (lw / n) * (i + 0.5) - bw / 2, y = Y(v); const isLast = i === n - 1;
      return `<rect x="${x}" y="${y}" width="${bw}" height="${h - 22 - y}" rx="4" fill="${isLast ? 'var(--ac)' : 'var(--s3)'}"/>`; }).join('');
    return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="XP per week">${bars}${labels}<text x="${pad + (lw / n) * (n - 0.5)}" y="${Y(last) - 7}" text-anchor="middle" class="rv">${last}</text></svg>`;
  }
  const pts = XP_WEEKS.map((v, i) => [X(i), Y(v)]);
  if (D === 'C') {
    let d = `M${pts[0][0]},${pts[0][1]}`;
    for (let i = 1; i < pts.length; i++) { const [x0, y0] = pts[i - 1], [x1, y1] = pts[i], mx = (x0 + x1) / 2; d += ` C${mx},${y0} ${mx},${y1} ${x1},${y1}`; }
    return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="XP per week"><path d="${d} L${X(n - 1)},${h - 22} L${X(0)},${h - 22}Z" fill="var(--ac)" fill-opacity=".14"/><path d="${d}" fill="none" stroke="var(--ac)" stroke-width="3.5" stroke-linecap="round"/><circle cx="${pts[n - 1][0]}" cy="${pts[n - 1][1]}" r="6" fill="var(--ac)"/><circle cx="${pts[n - 1][0]}" cy="${pts[n - 1][1]}" r="11" fill="var(--ac)" fill-opacity=".22"/>${labels}</svg>`;
  }
  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="XP per week"><line x1="${pad}" y1="${h - 22}" x2="${w - pad}" y2="${h - 22}" stroke="var(--track)"/><polyline points="${pts.map((p) => p.join(',')).join(' ')}" fill="none" stroke="var(--ink)" stroke-width="1.8" stroke-linejoin="round"/><circle cx="${pts[n - 1][0]}" cy="${pts[n - 1][1]}" r="5" fill="var(--ac)"/><text x="${pts[n - 1][0]}" y="${pts[n - 1][1] - 11}" text-anchor="middle" class="rv">${last}</text>${labels}</svg>`;
}

/* 18 weeks x 7 days activity. Deterministic so screenshots stay stable. */
function heat(D) {
  const cell = 14, gap = 3, cols = 18; let s = 7, out = '';
  const rnd = () => { s = (s * 9301 + 49297) % 233280; return s / 233280; };
  for (let c = 0; c < cols; c++) for (let r = 0; r < 7; r++) {
    const bias = c / cols; const v = rnd() + bias * 0.35; const lvl = v < 0.28 ? 0 : v < 0.5 ? 1 : v < 0.72 ? 2 : v < 0.9 ? 3 : 4;
    const x = c * (cell + gap), y = r * (cell + gap), op = [0, 0.28, 0.5, 0.75, 1][lvl];
    const fill = lvl === 0 ? 'var(--track)' : D === 'A' ? 'var(--ink)' : 'var(--ac)';
    out += D === 'C'
      ? `<circle cx="${x + cell / 2}" cy="${y + cell / 2}" r="${cell / 2 - 1}" fill="${fill}" ${lvl ? `fill-opacity="${op}"` : ''}/>`
      : `<rect x="${x}" y="${y}" width="${cell}" height="${cell}" rx="${D === 'A' ? 2 : 3}" fill="${fill}" ${lvl ? `fill-opacity="${op}"` : ''}/>`;
  }
  return `<svg width="${cols * (cell + gap) - gap}" height="${7 * (cell + gap) - gap}" role="img" aria-label="Activity, last 18 weeks">${out}</svg>`;
}

/* Vertical ruler for the height step. */
function ruler(D) {
  let t = ''; const from = 150, to = 190, step = 10;
  for (let v = to; v >= from; v--) {
    const y = (to - v) * step, major = v % 10 === 0, mid = v % 5 === 0;
    const len = major ? 44 : mid ? 28 : 16;
    t += `<line x1="${160 - len}" y1="${y + 6}" x2="160" y2="${y + 6}" stroke="${v === 170 ? 'var(--ac)' : 'var(--ink3)'}" stroke-width="${D === 'C' ? 3 : 1.5}" stroke-linecap="${D === 'C' ? 'round' : 'butt'}"/>`;
    if (major) t += `<text x="${160 - len - 8}" y="${y + 10}" text-anchor="end" class="rl">${v}</text>`;
  }
  const h = (to - from) * step + 12;
  return `<svg width="170" height="${h}" viewBox="0 0 170 ${h}" aria-hidden="true">${t}</svg>`;
}
