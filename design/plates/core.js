/* Shared runtime for the AWAKEN direction mockups: icons, sample data, phone frame, page.
   Each direction registers its screens in AW.dirs. Same content and IA everywhere. */
const AW = { dirs: {} };

AW.paths = {
  today: '<path d="M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"/>',
  focus: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="3.5"/>',
  gym: '<path d="M2.5 12h2M21.5 12h-2M4.5 8v8M19.5 8v8M7.5 5.5v13M16.5 5.5v13M7.5 12h9"/>',
  diet: '<path d="M4 12h16a8 8 0 0 1-16 0zM9.5 8c0-1.5 1.5-1.5 1.5-3M14 8c0-1.5 1.5-1.5 1.5-3"/>',
  progress: '<path d="M4 20V13M10 20V9M16 20V5M4 20h17"/>',
  settings: '<path d="M4 7h8M16 7h4M4 17h4M12 17h8"/><circle cx="14" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>',
  chevron: '<path d="M9.5 6l6 6-6 6"/>',
  down: '<path d="M6 9.5l6 6 6-6"/>',
  back: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  flame: '<path d="M12 3c.8 3.6 5 5.3 5 10a5 5 0 0 1-10 0c0-1.9.9-3.2 2-4.2.1 1.7.9 2.7 2 2.9-.3-3 .1-5.800 1-8.700z"/>',
  lock: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  away: '<circle cx="12" cy="12" r="8.500"/><path d="M12 7v5l3 2"/>',
  cal: '<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M4 10h16M9 3v4M15 3v4"/>',
  program: '<path d="M4 12a8 8 0 0 1 13.700-5.600L20 8.500M20 4v4.500h-4.500M20 12a8 8 0 0 1-13.700 5.600L4 15.500M4 20v-4.500h4.500"/>',
  quit: '<path d="M4 16h13v3H4zM20 16v3M8 12c0-2 2-2 2-4M12.500 12c0-2 2-2 2-4"/>',
  store: '<circle cx="12" cy="12" r="8.500"/><path d="M12 8v8M9.500 10.500h3.800a1.500 1.500 0 0 1 0 3H10"/>',
  health: '<path d="M12 20s-7.500-4.600-7.500-10.200A4.300 4.300 0 0 1 12 7.200a4.300 4.300 0 0 1 7.500 2.600C19.500 15.400 12 20 12 20z"/>',
  profile: '<circle cx="12" cy="8" r="4"/><path d="M4.500 20c.6-3.900 3.700-6 7.500-6s6.900 2.100 7.500 6"/>',
  day: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.600 5.600L7 7M17 17l1.400 1.400M18.400 5.600L17 7M7 17l-1.400 1.400"/>',
  palette: '<circle cx="12" cy="12" r="8.500"/><path d="M12 3.500v17M12 8l5.800-2.500M12 13l8-2.500M12 18l6-3"/>',
  widget: '<rect x="4" y="4" width="7" height="7" rx="1.500"/><rect x="13" y="4" width="7" height="7" rx="1.500"/><rect x="4" y="13" width="7" height="7" rx="1.500"/><rect x="13" y="13" width="7" height="7" rx="1.500"/>',
  data: '<ellipse cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v12c0 1.700 3.100 3 7 3s7-1.300 7-3V6M5 12c0 1.700 3.100 3 7 3s7-1.300 7-3"/>',
  swap: '<path d="M4 8h15l-3.500-3.500M20 16H5l3.500 3.500"/>',
  moon: '<path d="M20 14.500A8 8 0 1 1 9.500 4a6.500 6.500 0 0 0 10.500 10.500z"/>',
  phone: '<rect x="7" y="3" width="10" height="18" rx="2"/><path d="M11 18h2"/>',
  strength: '<path d="M13 3L5 13h6l-1 8 8-10h-6z"/>',
  intelligence: '<path d="M5 4h10a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3zM5 17a3 3 0 0 1 3-3h10"/>',
  willpower: '<circle cx="12" cy="12" r="8.500"/><circle cx="12" cy="12" r="3.500"/>',
  vitality: '<path d="M12 20s-7.500-4.600-7.500-10.200A4.300 4.300 0 0 1 12 7.200a4.300 4.300 0 0 1 7.500 2.600C19.500 15.400 12 20 12 20z"/>',
  charisma: '<circle cx="9" cy="8.500" r="3.500"/><path d="M3 20c.4-3.300 2.800-5 6-5s5.600 1.700 6 5M15.500 5.300a3.500 3.500 0 0 1 0 6.400M17.500 15.300c1.900.7 3.200 2.300 3.500 4.700"/>',
};
AW.ic = (n, s = 24) => `<svg class="ic" viewBox="0 0 24 24" width="${s}" height="${s}" fill="none" stroke="currentColor" aria-hidden="true">${AW.paths[n]}</svg>`;

AW.data = {
  stats: [
    { k: 'Strength', id: 'str', icon: 'strength', v: 14, prev: 11 },
    { k: 'Intelligence', id: 'int', icon: 'intelligence', v: 22, prev: 19 },
    { k: 'Willpower', id: 'wil', icon: 'willpower', v: 17, prev: 15 },
    { k: 'Vitality', id: 'vit', icon: 'vitality', v: 12, prev: 12 },
    { k: 'Charisma', id: 'cha', icon: 'charisma', v: 8, prev: 7 },
  ],
  xpWeeks: [320, 410, 380, 520, 460, 610, 570, 690],
  /* XP per week by stat, in stat order, same totals as xpWeeks. */
  xpByStat: [[90, 120, 60, 40, 10], [110, 150, 80, 50, 20], [80, 170, 70, 40, 20], [150, 190, 100, 60, 20], [120, 180, 90, 50, 20], [170, 230, 120, 70, 20], [150, 220, 110, 70, 20], [190, 260, 140, 80, 20]],
  plan: {
    done: 2, total: 6,
    morning: [{ t: 'Drink a glass of water', s: 'Vitality · Easy', xp: 12, stat: 'vit', done: true, hour: 7 }, { t: 'Plan your top three tasks', s: 'Intelligence · Easy', xp: 18, stat: 'int', done: true, hour: 8.500 }],
    day: [{ t: 'Walk 6,000 steps', s: '4,100 of 6,000 · Easy', xp: 24, stat: 'vit', hour: 14.300 }, { t: 'Read for 20 minutes', s: 'Intelligence · Normal', xp: 36, stat: 'int', hour: 17 }],
    evening: [{ t: 'Phone away from 22:30', s: 'Phone-down night · Normal', xp: 30, stat: 'wil', hour: 22.500 }],
    anytime: [{ t: 'Stay under your app limits', s: 'Instagram 26 of 30 min', xp: 42, stat: 'wil' }],
  },
  /* 18 weeks x 7 days, 0..4, fixed so screenshots stay stable. */
  heat: (() => { let s = 11; const r = () => (s = (s * 9301 + 49297) % 233280) / 233280; const out = [];
    for (let c = 0; c < 18; c++) { const col = []; for (let d = 0; d < 7; d++) { const v = r() + (c / 18) * 0.34; col.push(v < 0.3 ? 0 : v < 0.52 ? 1 : v < 0.74 ? 2 : v < 0.92 ? 3 : 4); } out.push(col); } return out; })(),
};
const D = AW.data;

AW.ring = (size, sw, pct, inner, cls = '', cap = 'round') => {
  const r = (size - sw) / 2, c = 2 * Math.PI * r, m = size / 2;
  return `<div class="ring ${cls}" style="width:${size}px;height:${size}px"><svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true">
    <circle class="ring-t" cx="${m}" cy="${m}" r="${r}" fill="none" stroke-width="${sw}"/>
    <circle class="ring-v" cx="${m}" cy="${m}" r="${r}" fill="none" stroke-width="${sw}" stroke-linecap="${cap}" stroke-dasharray="${c * pct} ${c}" transform="rotate(-90 ${m} ${m})"/></svg><div class="ring-in">${inner}</div></div>`;
};

/* Pentagon points for the five stats. */
AW.pent = (cx, cy, R, vals) => vals.map((f, i) => { const a = -Math.PI / 2 + (i * 2 * Math.PI) / 5; return [cx + Math.cos(a) * R * f, cy + Math.sin(a) * R * f]; });
AW.pts = (arr) => arr.map((p) => p.map((n) => n.toFixed(1)).join(',')).join(' ');

/* Android-style frame: status bar over the content, content scrolls, optional bottom nav. */
AW.navItems = [['today', 'Today'], ['focus', 'Focus'], ['gym', 'Gym'], ['diet', 'Diet'], ['progress', 'Progress']];
AW.nav = (on) => `<nav class="nav" aria-label="Main">${AW.navItems.map(([i, l], k) => `<a class="${k === on ? 'on' : ''}"${k === on ? ' aria-current="page"' : ''}><span class="ind">${AW.ic(i)}</span><span class="lbl">${l}</span></a>`).join('')}</nav>`;
AW.frame = (inner, o = {}) => `<div class="sb ${o.sb || ''}"><span>13:10</span><span class="sbi"><svg width="16" height="12" viewBox="0 0 16 12" aria-hidden="true"><path fill="currentColor" d="M0 11h3v1H0zM4 8h3v4H4zM8 4h3v8H8zM12 0h3v12h-3z"/></svg><svg width="24" height="12" viewBox="0 0 24 12" aria-hidden="true"><rect x=".5" y=".5" width="20" height="11" rx="2.500" fill="none" stroke="currentColor"/><rect x="2" y="2" width="15" height="8" rx="1" fill="currentColor"/><rect x="21.500" y="4" width="2" height="4" rx="1" fill="currentColor"/></svg></span></div>
<div class="scroll ${o.nav === undefined ? 'nonav' : ''}">${inner}</div>${o.nav === undefined ? '' : AW.nav(o.nav)}<div class="gesture ${o.sb || ''}"></div>`;

AW.list = [['today', 'Today'], ['focus', 'Focus session'], ['gym', 'Gym: active workout'], ['rest', 'Gym: rest timer'], ['progress', 'Progress'], ['onboarding', 'Onboarding'], ['settings', 'Settings']];

AW.phone = (dir, mode, s) => `<div class="phone ${dir} ${mode}" data-s="${s}">${AW.dirs[dir].screens[s](mode)}</div>`;

AW.mount = (only) => {
  const q = new URLSearchParams(location.search), root = document.getElementById('root');
  const dirs = Object.keys(AW.dirs).filter((d) => !only || d === only).filter((d) => !q.get('d') || d === q.get('d'));
  const modes = q.get('m') ? [q.get('m')] : ['light', 'dark'];
  const screens = q.get('s') ? q.get('s').split(',') : AW.list.map((s) => s[0]);
  if (q.get('solo')) { document.body.className = 'solo' + (q.get('tall') ? ' tall' : ''); root.innerHTML = AW.phone(dirs[0], modes[0], screens[0]); return; }
  document.body.className = 'page';
  const base = only ? '../' : './';
  const tabs = `<nav class="tabs"><a href="${base}index.html" class="${only ? '' : 'on'}">Side by side</a>${Object.keys(AW.dirs).length > 1 ? Object.entries(AW.dirs).map(([k, v]) => `<a href="${base}${k}/index.html">${v.name}</a>`).join('') : ['innings', 'plates', 'magnitude'].map((k) => `<a class="${k === only ? 'on' : ''}" href="${base}${k}/index.html">${k[0].toUpperCase() + k.slice(1)}</a>`).join('')}</nav>`;
  let h = `<header class="pg"><h1>AWAKEN directions</h1>${tabs}</header>`;
  for (const d of dirs) {
    h += `<section class="pgd"><h2>${AW.dirs[d].name}</h2><p>${AW.dirs[d].line}</p>`;
    for (const m of modes) { h += `<h3>${m === 'light' ? AW.dirs[d].light : AW.dirs[d].dark}</h3><div class="row-x">${screens.map((s) => `<figure>${AW.phone(d, m, s)}<figcaption>${AW.list.find((x) => x[0] === s)[1]}</figcaption></figure>`).join('')}</div>`; }
    h += '</section>';
  }
  root.innerHTML = h;
};
