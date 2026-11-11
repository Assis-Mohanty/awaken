/* Diet v2 mocks (Batch 4). Sample data only. From the repo root: python -m http.server 8765
   then /docs/diet/mocks/index.html  (one frame: ?m=dark&solo=1&s=diary). */
const { set, ic } = AW.p;
const page = (body, o = {}) => AW.frame(`<div class="wrap">${body}</div>`, o);
const sheet = (t, body) => AW.frame(`<div class="under"></div>`, {}) + `<div class="scrim"></div><section class="sheet" role="dialog" aria-label="${t}"><i class="grab"></i><h2>${t}</h2>${body}</section>`;
const kv = (a) => `<ul class="kvs">${a.map(([t, v]) => `<li class="kv"><span>${t}</span><b>${v}</b></li>`).join('')}</ul>`;
const chip = (t, on) => `<button class="chip ${on ? 'on' : ''}">${on ? ic('check', 16) : ''}${t}</button>`;
const big = (n, u) => `<strong class="num">${n}${u ? `<small>${u}</small>` : ''}</strong>`;
const hdr = (t, o = {}) => `<header class="bar">${o.back ? `<button class="icb back" aria-label="Back">${ic('back')}</button>` : ''}<div class="grow2"><h1 class="${o.big ? '' : 's'}">${t}</h1>${o.sub ? `<p>${o.sub}</p>` : ''}</div>${o.act || ''}</header>`;
const goals = `<button class="icb" aria-label="Goals">${ic('settings')}</button>`;
const dayn = (l = 'Today') => `<div class="dayn"><button aria-label="Previous day">${ic('back')}</button><b>${l}</b><button aria-label="Next day" ${l === 'Today' ? 'disabled' : ''}>${ic('chevron')}</button></div>`;
const mac = (rows) => `<ul class="mac">${rows.map(([t, v, p, c]) => `<li><span>${t}</span><b>${v}</b><div class="sb2"><i class="f-bg-${c}" style="width:${Math.min(p, 1) * 100}%"></i></div></li>`).join('')}</ul>`;
const meal = (t, k, items) => `<section class="meal"><div class="mh"><b>${t}</b><span>${k}</span><button aria-label="Add food to ${t}">${ic('plus', 22)}</button></div>${items.length ? items.map(([a, b]) => `<p>${a}<small>${b}</small></p>`).join('') : '<p class="mute">Nothing logged.</p>'}</section>`;
const water = (v) => `<div class="water"><span>Water<small>${v} of 2,500 ml</small></span><button aria-label="Add a glass">${ic('plus', 18)}250 ml</button></div>`;
const tiles = `<div class="tiles">${[['Weight', '64.2<small>kg</small>'], ['Nutrients', '9<small> of 12</small>'], ['Report', 'Week']].map(([a, b]) => `<a class="tile"><span>${a}</span><strong>${b}</strong></a>`).join('')}</div>`;
const n = (v) => v.toLocaleString('en');
const ringBlock = (eaten, target, hidden) => hidden
  ? `<div class="ringrow">${AW.ring(124, 11, 0, `<b>${n(eaten)}</b><small>kcal eaten</small>`)}<ul><li>Targets are off for you.</li></ul></div>`
  : `<div class="ringrow">${AW.ring(124, 11, Math.min(eaten / target, 1), `<b>${n(eaten)}</b><small>of ${n(target)} kcal</small>`)}<ul><li><b>${eaten > target ? `${n(eaten - target)} over` : `${n(target - eaten)} left`}</b></li><li>Active <b>412 kcal</b></li></ul></div>`;
const macros = mac([['Protein', '44 of 128 g', 0.34, 'str'], ['Carbs', '116 of 383 g', 0.3, 'wil'], ['Fat', '33 of 76 g', 0.43, 'vit'], ['Fibre', '11 of 38 g', 0.29, 'int']]);
const recal = `<section class="note"><div><b>Your weight has moved</b><p>The 7-day trend is 1.2 kg from when you set targets. Want new targets?</p></div><button class="btn2">Review targets</button></section>`;
const lunch = [['Dal tadka<span class="tag">Estimate</span>', '1 katori · 150 ml · 160 to 210 kcal'], ['Roti', '2 pieces · 140 kcal']];
const diaryBody = (o = {}) => `${hdr('Diet', { big: 1, act: goals })}${dayn(o.day)}${o.recal ? recal : ''}${ringBlock(o.eaten, 2200, o.hidden)}${o.hidden ? '' : macros}${meal('Breakfast', '240 kcal', [['Idli', '3 pieces · 174 kcal'], ['Sambar', '1 katori · 66 kcal']])}${meal('Lunch', '350 kcal', lunch)}${meal('Dinner', '0 kcal', [])}${meal('Snacks', '0 kcal', [])}${water('750')}${tiles}`;
const results = (rows) => `<ul class="setl flat res">${rows.map(([a, b]) => `<li><span><b>${a}</b><small>${b}</small></span>${ic('plus', 22)}</li>`).join('')}</ul>`;
const search = (v) => `<div class="srow"><label class="fld"><span>Search foods</span><input value="${v}" placeholder="Dal, roti, paneer" aria-label="Search foods"></label>${v ? '' : `<button class="st3" aria-label="Scan barcode">${ic('phone', 22)}</button>`}</div>`;
const stepper = `<div class="stepper"><button aria-label="Less">${ic('minus')}</button><button aria-label="More">${ic('plus')}</button></div>`;

const S = {
  diary: () => page(diaryBody({ eaten: 1420 }), { nav: 3 }),
  diaryOver: () => page(diaryBody({ eaten: 2410 }), { nav: 3 }),
  diaryHidden: () => page(diaryBody({ eaten: 1420, hidden: 1 }), { nav: 3 }),
  diaryRecal: () => page(diaryBody({ eaten: 1420, recal: 1 }), { nav: 3 }),
  diaryPast: () => page(diaryBody({ eaten: 1980, day: 'Yesterday' }), { nav: 3 }),
  diaryEmpty: () => page(`${hdr('Diet', { big: 1, act: goals })}${dayn()}${ringBlock(0, 2200)}${meal('Breakfast', '0 kcal', [])}${meal('Lunch', '0 kcal', [])}${meal('Dinner', '0 kcal', [])}${meal('Snacks', '0 kcal', [])}${water('0')}`, { nav: 3 }),
  diaryNew: () => page(`${hdr('Diet', { big: 1 })}<section class="empty"><div class="ghostbar"></div><h2>Set your targets</h2><p>Four questions. You can still log food without them.</p><button class="cta">Work out my targets</button><button class="txt full">Just log food</button></section>`, { nav: 3 }),

  addRecent: () => sheet('Add to Lunch', `${search('')}<div class="tabrow">${chip('Recent', 1)}${chip('Favourites')}${chip('My foods')}${chip('Meals')}${chip('Recipes')}</div>${results([['Roti', '1 piece · 70 kcal'], ['Dal tadka', '1 katori · 160 to 210 kcal'], ['Idli', '1 piece · 58 kcal'], ['Curd, whole milk', '1 katori · 92 kcal']])}<div class="chips"><button class="chip">Quick add</button><button class="chip">New food</button></div>`),
  addResults: () => sheet('Add to Lunch', `${search('dal')}${results([['Dal tadka', 'Recipe · 1 katori · 160 to 210 kcal'], ['Dal, moong, cooked', 'Overlay · 100 g · 105 kcal'], ['Dal makhani', 'Recipe · 1 katori · 230 to 300 kcal'], ['Lentils, cooked', 'USDA · 100 g · 116 kcal']])}`),
  addNone: () => sheet('Add to Lunch', `${search('xylo bar')}<section class="empty"><h2>This one isn't in the list yet</h2><p>Add it yourself and it will be here next time.</p></section><button class="cta">Create a food</button><button class="txt full">Quick add calories</button>`),

  serving: () => sheet('Dal tadka', `<p class="mute2">Home-cooked dal, tempered. Calories vary with the oil.</p><div class="heroN c">${big('1', 'katori')}</div>${stepper}<div class="unit">${chip('katori', 1)}${chip('cup')}${chip('tbsp')}${chip('g')}${chip('ml')}</div><p class="mute2">150 ml · about 150 g</p>${kv([['Energy', '160 to 210 kcal'], ['Protein', '8 g'], ['Carbs', '22 g'], ['Fat', '5 to 9 g']])}<span class="est">Estimate: depends on how it was cooked</span><button class="cta">Add to Lunch</button>`),
  servingG: () => sheet('Paneer, raw', `<div class="heroN c">${big('100', 'g')}</div>${stepper}<div class="unit">${chip('g', 1)}${chip('piece')}</div>${kv([['Energy', '321 kcal'], ['Protein', '21 g'], ['Carbs', '3.6 g'], ['Fat', '25 g']])}<button class="cta">Add to Lunch</button>`),
  servingEdit: () => sheet('Roti', `<div class="heroN c">${big('2', 'pieces')}</div>${stepper}${kv([['Energy', '140 kcal'], ['Protein', '5 g']])}<button class="cta">Save changes</button><button class="txt full">Remove from Lunch</button>`),

  calc1: () => page(`${hdr('About you', { back: 1, sub: 'Step 1 of 3' })}<h2 class="sect f">Sex</h2><div class="seg wide"><button>Female</button><button class="on">Male</button></div>${kv([['Birth year', '1999'], ['Height', '176 cm'], ['Weight', '78 kg']])}<h2 class="sect">How active are you?</h2><div class="chips">${chip('Sedentary')}${chip('Light', 1)}${chip('Moderate')}${chip('Very active')}</div><p class="step">Used only to estimate your daily energy. It stays on this phone.</p><div class="dock s"><button class="cta">Next</button></div>`),
  calc2: () => page(`${hdr('Your goal', { back: 1, sub: 'Step 2 of 3' })}<div class="seg wide"><button class="on">Lose</button><button>Maintain</button><button>Gain</button></div><h2 class="sect">Pace</h2><div class="pace"><button><span>Gentle<small>250 kcal below maintenance</small></span></button><button class="on"><span>Steady<small>500 kcal below maintenance</small></span>${ic('check', 20)}</button><button><span>Faster<small>750 kcal below maintenance</small></span></button></div><p class="step">A pace is lowered if it would be more than a quarter of your daily energy.</p><div class="dock s"><button class="cta">Next</button></div>`),
  calc3: () => page(`${hdr('Your numbers', { back: 1, sub: 'Step 3 of 3' })}<div class="heroN">${big('2,200', 'kcal a day')}</div>${kv([['Maintenance', '2,700 kcal'], ['Protein', '125 g'], ['Carbs', '260 g'], ['Fat', '70 g']])}<h2 class="sect">Macro split</h2><div class="seg wide"><button class="on">Balanced</button><button>High protein</button><button>Custom</button></div><div class="dock s"><button class="cta">Save targets</button><button class="txt full">Change an answer</button></div>`),
  calcFloor: () => page(`${hdr('Your numbers', { back: 1, sub: 'Step 3 of 3' })}<div class="heroN">${big('1,500', 'kcal a day')}</div><section class="note"><div><b>This is the lowest we set</b><p>Your pace would go below 1,500 kcal, so it stops here. A doctor or dietitian can help with anything lower.</p></div></section>${kv([['Maintenance', '1,900 kcal'], ['Protein', '95 g'], ['Carbs', '170 g'], ['Fat', '55 g']])}<div class="dock s"><button class="cta">Save targets</button></div>`),
};

const G = [['Diary', [['diary', 'Part-filled'], ['diaryOver', 'Over target'], ['diaryEmpty', 'Empty day'], ['diaryNew', 'Not set up'], ['diaryHidden', 'Targets hidden'], ['diaryPast', 'Past day'], ['diaryRecal', 'Recalculation card']]],
  ['Add food', [['addRecent', 'Recent'], ['addResults', 'Results'], ['addNone', 'No results']]],
  ['Serving', [['serving', 'Indian unit, range, estimate'], ['servingG', 'Grams only'], ['servingEdit', 'Editing an entry']]],
  ['Calculator', [['calc1', 'Step 1'], ['calc2', 'Step 2'], ['calc3', 'Step 3'], ['calcFloor', 'Floor reached']]]];
function render() {
  const q = new URLSearchParams(location.search), root = document.getElementById('root'), mode = q.get('m');
  const ph = (id, m) => `<div class="phone plates ${m}">${S[id]()}</div>`;
  if (q.get('solo')) { document.body.className = 'solo' + (q.get('tall') ? ' tall' : ''); root.innerHTML = ph(q.get('s'), mode || 'light'); return; }
  document.body.className = 'page';
  let h = '<header class="pg"><h1>Diet v2 mocks · Batch 4</h1></header>';
  for (const [t, items] of G) for (const m of mode ? [mode] : ['light', 'dark']) h += `<section class="pgd"><h3>${t} · ${m}</h3><div class="row-x">${items.map(([id, l]) => `<figure>${ph(id, m)}<figcaption>${l}</figcaption></figure>`).join('')}</div></section>`;
  root.innerHTML = h;
}
