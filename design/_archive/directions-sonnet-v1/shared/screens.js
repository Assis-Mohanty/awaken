/* AWAKEN design mockups: the seven screens. Real content; same IA in every direction. */
const NAV = [['home', 'Today'], ['focus', 'Focus'], ['dumbbell', 'Gym'], ['bowl', 'Diet'], ['bars', 'Progress']];
const nav = (on) => `<nav class="nav">${NAV.map(([i, l], k) => `<a class="${k === on ? 'on' : ''}"><span class="pill">${ic(i, 24)}</span><b>${l}</b></a>`).join('')}</nav>`;
const status = () => `<div class="sb"><span>9:41</span><span class="sbi"><i></i><i></i><i></i></span></div>`;
const frame = (inner, o = {}) => `${status()}<div class="scroll ${o.nonav ? 'nonav' : ''}">${inner}</div>${o.nonav ? '' : nav(o.nav)}<div class="home-ind"></div>`;

const circ = (done) => `<span class="chk ${done ? 'done' : ''}">${done ? ic('check', 16) : ''}</span>`;
const row = (icon, title, sub, o = {}) => `<div class="row ${o.cls || ''}"><span class="ri">${ic(icon, 22)}</span><div class="rt"><b>${title}</b>${sub ? `<small>${sub}</small>` : ''}</div>${o.trail ?? (o.chev ? ic('chev', 18) : '')}</div>`;
const tag = (t) => `<span class="tag">${t}</span>`;
const btn = (t, cls = '') => `<button class="btn ${cls}">${t}</button>`;

function levelModule(D) {
  return `<section class="level">
    ${ring(D, 1240 / 1434, D === 'A' ? 84 : 92, `<span class="lv-n">24</span><span class="lv-l">Level</span>`)}
    <div class="lv-r">
      <div class="lv-top"><span class="rank">Rank <b>C</b></span><span class="streak">${ic('flame', 18)}<b>12</b> days</span></div>
      ${bar(1240 / 1434, 'xp')}
      <div class="lv-x"><span><b>1,240</b> / 1,434 XP</span><span>194 to go</span></div>
    </div></section>`;
}

const SCREENS = {
  today(D) {
    const plan = `
      <div class="blk"><div class="bh"><b>Morning</b><span>2 of 2 done</span>${ic('chev', 16)}</div></div>
      <div class="blk"><div class="bh now"><b>Day</b>${tag('Now')}<span>0 of 3 done</span></div>
        <div class="card list">
          <div class="row q">${circ(false)}<div class="rt"><b>Call your family</b><small>1 call · Normal</small></div><span class="qi">${ic('people', 20)}</span></div>
          <div class="row q">${circ(false)}<div class="rt"><b>Walk 6,000 steps</b><small>4,100 of 6,000 · Easy</small></div><span class="qi">${ic('heart', 20)}</span></div>
          <div class="row q">${circ(false)}<div class="rt"><b>Read for 20 minutes</b><small>Intelligence · Normal</small></div><span class="qi">${ic('book', 20)}</span></div>
        </div></div>
      <div class="blk"><div class="bh"><b>Evening</b><span>0 of 1 done</span></div>
        <div class="card list"><div class="row q">${circ(false)}<div class="rt"><b>Phone away from 22:30</b><small>Phone-down night · tonight</small></div><span class="qi">${ic('moon', 20)}</span></div></div></div>
      <div class="blk"><div class="bh"><b>Anytime</b><span>0 of 1 done</span></div>
        <div class="card list"><div class="row q">${circ(false)}<div class="rt"><b>Stay under your app limits</b><small>Instagram 26 of 30 min</small></div><span class="qi">${ic('phone', 20)}</span></div></div></div>`;
    return frame(`<div class="pad">
      <header class="top"><div><small>Good morning, Assis</small><h1>Friday 6 November</h1></div><button class="ib" aria-label="Settings">${ic('gear', 22)}</button></header>
      ${levelModule(D)}
      <div class="sec"><h2>Today's plan <em>2 of 6 done</em></h2><div class="acts"><a>Schedule</a><a>Habits</a></div></div>
      ${plan}
      <div class="sec"><h2>At a glance</h2></div>
      <div class="tiles">
        <div class="tile"><small>${ic('focus', 16)} Focus</small><b class="big">42<i>min</i></b>${bar(42 / 60)}<em>of 60 min</em></div>
        <div class="tile"><small>${ic('phone', 16)} Screen time</small><b class="big">2<i>h</i> 57<i>m</i></b>${bar(0.62)}<em>54 min distracting</em></div>
        <div class="tile"><small>${ic('steps', 16)} Steps</small><b class="big">8,412</b>${bar(1)}<em>Goal reached</em></div>
      </div></div>`, { nav: 0 });
  },

  focus(D) {
    return frame(`<div class="pad run">
      <header class="top c"><button class="ib" aria-label="Minimise">${ic('chev', 22).replace('M9 6l6 6-6 6', 'M6 9l6 6 6-6')}</button><div><small>15-minute session</small><h1 class="s">DSA · graphs</h1></div><span class="ib ghost"></span></header>
      <div class="hero-ring">${ring(D, 0.21, 300, `<span class="clock">11:48</span><span class="cl">remaining</span>`)}</div>
      <div class="meta">
        <div>${ic('lock', 18)}<span><b>Focus Lock is on</b><small>3 apps blocked</small></span></div>
        <div>${ic('clock', 18)}<span><b>Time away 0:25</b><small>of 1:30 allowed</small></span></div>
      </div>
      <div class="dock">${btn('Pause for 5 minutes', 'primary')}<button class="quiet">End session</button></div></div>`, { nonav: true });
  },

  gym(D) {
    const done = `<div class="chips">
      <span class="dchip">${ic('check', 14)} 1 · 60 kg × 10</span><span class="dchip">${ic('check', 14)} 2 · 70 kg × 8</span></div>`;
    const upNext = [['Dips · triceps', '3 sets'], ['Incline dumbbell press', '3 sets'], ['Cable fly', '3 sets']]
      .map(([t, s]) => `<div class="row mini"><span class="ri">${ic('dumbbell', 20)}</span><div class="rt"><b>${t}</b><small>${s}</small></div>${ic('chev', 18)}</div>`).join('');
    return frame(`<div class="pad">
      <header class="top c"><div><small>Push day · 29:05</small><h1 class="s">Exercise 1 of 4</h1></div><button class="quiet sm">Finish</button></header>
      <section class="cur">
        <div class="cur-h"><b>Barbell bench press</b><small>Chest · Barbell</small></div>
        ${done}
        <div class="setno">Set 3 of 4</div>
        <div class="dial">
          <div class="dcol"><button class="ib sm2" aria-label="Less weight">${ic('minus', 20)}</button><div class="dv"><b>70</b><i>kg</i></div><button class="ib sm2" aria-label="More weight">${ic('plus', 20)}</button></div>
          <div class="dx">×</div>
          <div class="dcol"><button class="ib sm2" aria-label="Fewer reps">${ic('minus', 20)}</button><div class="dv"><b>8</b><i>reps</i></div><button class="ib sm2" aria-label="More reps">${ic('plus', 20)}</button></div>
        </div>
        <div class="hint">Last time 67.5 kg × 8 · aim for 9</div>
        ${btn('Complete set', 'primary')}
      </section>
      <div class="sec"><h2>Up next</h2></div>
      <div class="card list">${upNext}</div>
      <div class="foot"><a>${ic('plus', 18)} Add exercise</a><a>${ic('swap', 18)} Swap</a></div></div>`, { nonav: true });
  },

  rest(D) {
    return frame(`<div class="pad run">
      <header class="top c"><span class="ib ghost"></span><div><small>Push day · 30:17</small><h1 class="s">Rest</h1></div><span class="ib ghost"></span></header>
      <div class="hero-ring">${ring(D, 0.58, 300, `<span class="clock">0:52</span><span class="cl">of 1:30</span>`)}</div>
      <div class="nextset"><small>Next</small><b>Barbell bench press · set 3</b><span>70 kg × 8 reps</span></div>
      <div class="adj"><button class="chipb">−15 s</button><button class="chipb">+15 s</button></div>
      <div class="dock">${btn('Skip rest', 'primary')}</div></div>`, { nonav: true });
  },

  progress(D) {
    const shortcuts = [['cal', 'Weekly review', 'Open now'], ['repeat', 'Programs', 'Day 12 of 30'], ['quit', 'Quit counters', '12 days clean'], ['coin', 'Store', '1,240 gold'], ['steps', 'Health', '8,412 steps']]
      .map(([i, t, v]) => `<div class="short"><span class="ri">${ic(i, 22)}</span><div class="rt"><b>${t}</b><small>${v}</small></div>${ic('chev', 16)}</div>`).join('');
    const stats = STATS.map((s) => `<div class="st"><span>${ic(s.ic, 18)}${s.k}</span><b>${s.v}</b></div>`).join('');
    return frame(`<div class="pad">
      <header class="top"><div><small>Assis</small><h1>Progress</h1></div></header>
      <section class="hero-lv">
        ${ring(D, 1240 / 1434, 150, `<span class="lv-n xl">24</span><span class="lv-l">Level</span>`)}
        <div class="hl-r"><span class="rank lg">Rank <b>C</b></span><div class="hl-x"><b>1,240</b> / 1,434 XP</div><small>194 XP to level 25</small>
          <div class="streak2">${ic('flame', 18)}<b>12</b> day streak<small>22 of the last 28 days</small></div></div>
      </section>
      <div class="shorts">${shortcuts}</div>
      <div class="sec"><h2>Stat balance</h2></div>
      <section class="card chart radarw">${radar(D)}<div class="legend"><span><i class="l1"></i>Now</span><span><i class="l2"></i>Last week</span></div></section>
      <div class="card list stats">${stats}</div>
      <div class="sec"><h2>Experience per week</h2><em>Up 21% on last week</em></div>
      <section class="card chart">${trend(D)}</section>
      <div class="sec"><h2>Last 18 weeks</h2><em>94 active days</em></div>
      <section class="card chart heatw">${heat(D)}</section></div>`, { nav: 4 });
  },

  onboarding(D) {
    const segs = Array.from({ length: 5 }, (_, i) => `<i class="${i < 1 ? 'f' : i === 1 ? 'h' : ''}"></i>`).join('');
    return frame(`<div class="pad ob">
      <header class="obt"><button class="ib" aria-label="Back">${ic('back', 22)}</button><div class="segs">${segs}</div><a class="skip">Skip</a></header>
      <small class="step">Step 5 of 21</small>
      <h1>How tall are you?</h1><p class="sub">Used for your plan and a rough BMI.</p>
      <div class="ob-body">
        <div class="ob-l"><div class="val"><b>170</b><i>cm</i></div>
          <div class="seg2"><span class="on">cm</span><span>ft</span></div>
          <div class="bmi"><small>BMI preview</small><b>23.5</b><em>In the healthy range</em></div></div>
        <div class="ruler">${ruler(D)}<div class="needle"></div></div>
      </div>
      <div class="dock">${btn('Continue', 'primary')}</div></div>`, { nonav: true });
  },

  settings(D) {
    const g = (icon, t, v) => `<div class="row s">${`<span class="ri">${ic(icon, 22)}</span>`}<div class="rt"><b>${t}</b><small>${v}</small></div>${ic('chev', 18)}</div>`;
    return frame(`<div class="pad">
      <header class="top c"><button class="ib" aria-label="Back">${ic('back', 22)}</button><h1 class="s">Settings</h1><span class="ib ghost"></span></header>
      <div class="card list">
        ${g('user', 'Profile', 'Assis · Strength, Intelligence')}
        ${g('clock', 'Day and reminders', 'Day starts 04:00 · Reminder 07:00')}
        ${g('heart', 'Health and training', 'Health Connect on · Mon, Wed, Fri')}
      </div>
      <div class="sec"><h2>Appearance and sound</h2></div>
      <div class="card list">
        <div class="row s"><span class="ri">${ic('theme', 22)}</span><div class="rt"><b>Theme</b></div><div class="seg2 sm3"><span>Light</span><span class="on">Dark</span><span>Phone</span></div></div>
        <div class="row s"><span class="ri">${ic('bell', 22)}</span><div class="rt"><b>Timer sound</b><small>Soft chime</small></div><span class="sw on"><i></i></span></div>
        <div class="row s"><span class="ri">${ic('bolt', 22)}</span><div class="rt"><b>Haptics</b></div><span class="sw on"><i></i></span></div>
        <div class="row s"><span class="ri">${ic('sun', 22)}</span><div class="rt"><b>Reduce motion</b></div><span class="sw"><i></i></span></div>
      </div>
      <div class="sec"><h2>More</h2></div>
      <div class="card list">
        ${g('widget', 'Widgets and shortcuts', '3 widgets · 1 quick tile')}
        ${g('data', 'Your data', 'Export, import, privacy')}
      </div>
      <p class="foot2">AWAKEN 1.2 · Everything stays on this phone.</p></div>`, { nonav: true });
  },
};
const SCREEN_LIST = [['today', 'Today'], ['focus', 'Focus session'], ['gym', 'Gym · active workout'], ['rest', 'Gym · rest'], ['progress', 'Progress'], ['onboarding', 'Onboarding'], ['settings', 'Settings']];
