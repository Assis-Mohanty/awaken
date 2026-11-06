/* Renders frames. URL: ?d=A|B|C (omit for all) &m=light|dark (omit for both) &s=today|... (omit for all) &solo=1 &tall=1 */
const DIRS = { A: ['Ledger', 'A-ledger'], B: ['Meridian', 'B-meridian'], C: ['Moss', 'C-moss'] };
const q = new URLSearchParams(location.search);
const pick = (v, all) => (v && all.includes(v) ? [v] : all);
function phone(D, mode, screen) { return `<div class="phone d${D} ${mode}">${SCREENS[screen](D)}</div>`; }
function build(root, base) {
  const dirs = pick(q.get('d') || window.DIR, ['A', 'B', 'C']), modes = pick(q.get('m'), ['light', 'dark']);
  const screens = pick(q.get('s'), SCREEN_LIST.map((s) => s[0]));
  const names = Object.fromEntries(SCREEN_LIST);
  if (q.get('solo')) { document.body.className = 'solo' + (q.get('tall') ? ' tall' : ''); root.innerHTML = phone(dirs[0], modes[0], screens[0]); return; }
  document.body.classList.add('page');
  const head = dirs.length === 1
    ? `<header class="pg"><h1 class="pgt">${DIRS[dirs[0]][0]}</h1><nav class="tabs">${['A', 'B', 'C'].map((k) => `<a class="${k === dirs[0] ? 'on' : ''}" href="${base}${DIRS[k][1]}/index.html">${DIRS[k][0]}</a>`).join('')}<a href="${base}index.html">Compare</a></nav></header>`
    : `<header class="pg"><h1 class="pgt">AWAKEN · three directions</h1><nav class="tabs">${['A', 'B', 'C'].map((k) => `<a href="${base}${DIRS[k][1]}/index.html">${DIRS[k][1].slice(2)}</a>`).join('')}</nav></header>`;
  let html = head;
  for (const D of dirs) for (const mode of modes) {
    html += `<h2 style="margin:26px 0 12px;font-size:15px;color:#bdbdc2;font-weight:600">${DIRS[D][0]} · ${mode}</h2><div class="grid">`;
    for (const s of screens) html += `<figure>${phone(D, mode, s)}<figcaption>${names[s]}</figcaption></figure>`;
    html += '</div>';
  }
  root.innerHTML = html;
}
