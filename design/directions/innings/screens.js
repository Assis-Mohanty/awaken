/* Innings: every key number is a hung scoreboard plate. Day session (light), floodlit night (dark). */
(() => {
  const ic = AW.ic;
  const hung = (txt, cls = '') => `<span class="hung ${cls}" aria-label="${txt}">${[...txt].map((c) => (/\d/.test(c) ? `<b>${c}</b>` : `<i>${c}</i>`)).join('')}</span>`;
  const qrow = (q) => `<li class="q ${q.done ? 'done' : ''}"><span class="tick">${q.done ? ic('check', 18) : ''}</span><span class="qt"><b>${q.t}</b><small>${q.s}</small></span><span class="qxp">+${q.xp}</span></li>`;
  const over = () => `<div class="over" role="img" aria-label="2 of 6 quests done">${[1, 1, 0, 0, 0, 0].map((f) => `<i class="${f ? 'f' : ''}"></i>`).join('')}</div>`;

  const levelBoard = () => `<div class="score">
      ${AW.ring(88, 6, 1240 / 1434, `${hung('24', 'sm')}`, '', 'butt')}
      <dl class="cells">
        <div><dt>Level</dt><dd class="xpval"><b>1,240</b> of 1,434 XP</dd></div>
        <div class="split"><div><dt>Rank</dt><dd><span class="badge">C</span></dd></div><div><dt>Streak</dt><dd class="st">${ic('flame', 20)}<b>12</b> days</dd></div></div>
      </dl></div>`;

  const today = () => AW.frame(`
    <section class="board top">
      <header class="bar"><div><h1>Good afternoon, Assis</h1><p>Friday 6 November</p></div><button class="icb" aria-label="Settings">${ic('settings')}</button></header>
      ${levelBoard()}
    </section>
    <section class="body">
      <div class="plan-h"><div><h2>Today's plan</h2><div class="ov">${over()}<span>2 of 6 done</span></div></div><div class="acts"><button>Schedule</button><button>Habits</button></div></div>
      <div class="blk"><button class="bh"><b>Morning</b><span>2 of 2 done</span>${ic('chevron', 18)}</button></div>
      <div class="blk"><div class="bh on"><b>Day</b><em>Now</em><span>0 of 2 done</span></div><ul class="qs">${D.plan.day.map(qrow).join('')}</ul></div>
      <div class="blk"><div class="bh"><b>Evening</b><span>0 of 1 done</span></div><ul class="qs">${D.plan.evening.map(qrow).join('')}</ul></div>
      <div class="blk"><div class="bh"><b>Anytime</b><span>0 of 1 done</span></div><ul class="qs">${D.plan.anytime.map(qrow).join('')}</ul></div>
      <div class="tiles">
        <a class="tile"><span>Focus</span><strong>42<small>m</small></strong><em>of 60 min</em></a>
        <a class="tile"><span>Screen time</span><strong>2<small>h</small> 57<small>m</small></strong><em>54 min distracting</em></a>
        <a class="tile"><span>Steps</span><strong>4,100</strong><em>of 6,000</em></a>
      </div>
    </section>`, { nav: 0, sb: 'onb' });

  const focus = () => AW.frame(`
    <section class="board run">
      <header class="bar c"><button class="icb" aria-label="Minimise">${ic('down')}</button><div><h1 class="s">15-minute session</h1><p>DSA, graphs</p></div><span class="icb no"></span></header>
      <div class="clock">${hung('11:48', 'xl')}<p>remaining</p></div>
      <div class="runbar"><i style="width:21%"></i></div>
      <ul class="facts"><li>${ic('lock', 20)}<span><b>Focus Lock on</b><small>3 apps blocked</small></span></li><li>${ic('away', 20)}<span><b>Away 0:25</b><small>of 1:30 allowed</small></span></li></ul>
      <div class="dock"><button class="cta">Pause for 5 minutes</button><button class="txt">End session</button></div>
    </section>`, { sb: 'onb' });

  const gym = () => AW.frame(`
    <section class="board top gymt">
      <header class="bar"><div><h1 class="s">Barbell bench press</h1><p>Push day · 29:05 · exercise 1 of 4</p></div><button class="txt sm">Finish</button></header>
      <p class="setn">Set 3 of 4</p>
      <div class="dials">
        <div class="dial">${hung('70', 'lg')}<span>kg</span><div class="st2"><button aria-label="Less weight">${ic('minus')}</button><button aria-label="More weight">${ic('plus')}</button></div></div>
        <div class="dial">${hung('8', 'lg')}<span>reps</span><div class="st2"><button aria-label="Fewer reps">${ic('minus')}</button><button aria-label="More reps">${ic('plus')}</button></div></div>
      </div>
    </section>
    <section class="body tight">
      <p class="hint">Last time 67.5 kg × 8. Try 9 reps.</p>
      <button class="cta">Complete set</button>
      <table class="sets"><thead><tr><th>Set</th><th>Weight</th><th>Reps</th></tr></thead><tbody><tr><td>1</td><td>60 kg</td><td>10</td></tr><tr><td>2</td><td>70 kg</td><td>8</td></tr></tbody></table>
      <h2 class="mini">Up next</h2>
      <ul class="nxt"><li><span><b>Dips, triceps</b><small>3 sets</small></span>${ic('chevron', 18)}</li><li><span><b>Incline dumbbell press</b><small>3 sets</small></span>${ic('chevron', 18)}</li><li><span><b>Cable fly</b><small>3 sets</small></span>${ic('chevron', 18)}</li></ul>
      <div class="more"><button>${ic('plus', 20)}Add exercise</button><button>${ic('swap', 20)}Swap</button></div>
    </section>`, { sb: 'onb' });

  const rest = () => AW.frame(`
    <section class="board run">
      <header class="bar c"><span class="icb no"></span><div><h1 class="s">Rest</h1><p>Push day · 30:17</p></div><span class="icb no"></span></header>
      <div class="clock">${hung('0:52', 'xl')}<p>of 1:30</p></div>
      <div class="runbar"><i style="width:58%"></i></div>
      <div class="adj"><button>−15 s</button><button>+15 s</button></div>
      <div class="nextup"><span>Next</span><b>Set 3 of 4</b><small>Barbell bench press, 70 kg × 8</small></div>
      <div class="dock"><button class="cta">Skip rest</button></div>
    </section>`, { sb: 'onb' });

  const radar = () => { const s = 280, c = s / 2, R = 96; const grid = [0.25, 0.5, 0.75, 1].map((f) => `<polygon points="${AW.pts(AW.pent(c, c + 4, R, [f, f, f, f, f]))}" class="gr"/>`).join('');
    const now = AW.pent(c, c + 4, R, D.stats.map((x) => x.v / 25)), pr = AW.pent(c, c + 4, R, D.stats.map((x) => x.prev / 25));
    const lab = AW.pent(c, c + 4, R + 26, [1, 1, 1, 1, 1]).map((p, i) => `<text x="${p[0]}" y="${p[1] + 4}" text-anchor="middle">${D.stats[i].k}</text>`).join('');
    return `<svg class="radar" viewBox="-36 0 ${s + 72} ${s}" width="100%" role="img" aria-label="Stat balance now and last week">${grid}<polygon points="${AW.pts(pr)}" class="pv"/><polygon points="${AW.pts(now)}" class="nw"/>${now.map((p) => `<rect x="${p[0] - 3.500}" y="${p[1] - 3.500}" width="7" height="7" class="dt"/>`).join('')}${lab}</svg>`; };
  const manhattan = () => { const w = 350, h = 150, b = 30, gap = (w - b * 8) / 7, max = 760;
    return `<svg class="man" viewBox="0 0 ${w} ${h}" width="100%" role="img" aria-label="XP per week, last 8 weeks, rising to 690">${D.xpWeeks.map((v, i) => { const x = i * (b + gap), bh = (v / max) * (h - 34), y = h - 18 - bh, l = i === 7; return `<rect x="${x}" y="${y}" width="${b}" height="${bh}" class="${l ? 'l' : 'b'}"/><text x="${x + b / 2}" y="${h - 3}" text-anchor="middle">${i === 7 ? 'Now' : ''}</text>${l ? `<text class="v" x="${x + b / 2}" y="${y - 5}" text-anchor="middle">690</text>` : ''}`; }).join('')}</svg>`; };
  const heat = () => `<svg class="heat" viewBox="0 0 ${18 * 19 - 3} ${7 * 19 - 3}" width="100%" role="img" aria-label="Activity over the last 18 weeks, 94 active days">${D.heat.map((col, c) => col.map((v, d) => `<rect x="${c * 19}" y="${d * 19}" width="16" height="16" class="h${v}"/>`).join('')).join('')}</svg>`;

  const progress = () => AW.frame(`
    <section class="board top">
      <header class="bar"><div><h1>Progress</h1><p>Assis</p></div></header>
      <div class="hero">
        ${AW.ring(148, 8, 1240 / 1434, `${hung('24', 'md')}<small>Level</small>`, '', 'butt')}
        <dl><div><dt>Rank</dt><dd><span class="badge lg">C</span></dd></div><div><dt>XP</dt><dd class="xpval"><b>1,240</b> of 1,434</dd></div><div><dt>Streak</dt><dd class="st">${ic('flame', 20)}<b>12</b> days</dd></div></dl>
      </div>
    </section>
    <section class="body">
      <ul class="links">
        ${[['cal', 'Weekly review', 'Open now'], ['program', 'Programs', 'Day 12 of 30'], ['quit', 'Quit counters', '12 days clean'], ['store', 'Store', '1,240 gold'], ['health', 'Health', '4,100 steps']].map(([i, t, v]) => `<li>${ic(i, 22)}<b>${t}</b><span>${v}</span>${ic('chevron', 18)}</li>`).join('')}
      </ul>
      <h2 class="sect">Stat balance</h2>
      <div class="radw">${radar()}<div class="leg"><span><i class="a"></i>Now</span><span><i class="b"></i>Last week</span></div></div>
      <ul class="statl">${D.stats.map((s) => `<li>${ic(s.icon, 20)}<span>${s.k}</span><b>${s.v}</b></li>`).join('')}</ul>
      <h2 class="sect">Experience per week <em>Up 21%</em></h2>
      <div class="chart">${manhattan()}</div>
      <h2 class="sect">Last 18 weeks <em>94 active days</em></h2>
      <div class="chart">${heat()}</div>
    </section>`, { nav: 4, sb: 'onb' });

  const ruler = () => { let t = ''; for (let v = 190; v >= 150; v--) { const y = (190 - v) * 10 + 8, maj = v % 10 === 0, mid = v % 5 === 0, l = maj ? 46 : mid ? 30 : 16; t += `<rect x="${130 - l}" y="${y - 1}" width="${l}" height="${maj ? 3 : 2}" class="${v === 170 ? 'k' : 't'}"/>`; if (maj && v !== 170) t += `<text x="${130 - l - 8}" y="${y + 5}" text-anchor="end">${v}</text>`; } return `<svg class="rul" viewBox="0 0 132 416" width="132" height="416" aria-hidden="true">${t}</svg>`; };
  const onboarding = () => AW.frame(`
    <section class="ob">
      <header class="obh"><button class="icb" aria-label="Back">${ic('back')}</button><div class="segs" role="img" aria-label="Step 5 of 21"><i class="f"></i><i class="p"></i><i></i><i></i><i></i></div><button class="txt sm">Skip</button></header>
      <h1>How tall are you?</h1><p class="lead">Used for your plan and a rough BMI.</p>
      <div class="obb">
        <div class="obl">${hung('170', 'lg ob3')}<span class="un">cm</span>
          <div class="seg" role="tablist"><button class="on">cm</button><button>ft</button></div>
          <div class="bmi"><span>BMI</span><b>23.5</b><small>Healthy range</small></div></div>
        <div class="rulw">${ruler()}</div>
      </div>
      <div class="dock"><button class="cta">Continue</button></div>
    </section>`);

  const set = (i, t, v) => `<li>${ic(i, 22)}<span><b>${t}</b><small>${v}</small></span>${ic('chevron', 18)}</li>`;
  const toggle = (on) => `<span class="sw ${on ? 'on' : ''}" role="switch" aria-checked="${on}"><i>${on ? AW.ic('check', 14) : ''}</i></span>`;
  const settings = () => AW.frame(`
    <section class="board top slim"><header class="bar"><button class="icb" aria-label="Back">${ic('back')}</button><h1 class="s grow">Settings</h1></header></section>
    <section class="body">
      <ul class="setl">${set('profile', 'Profile', 'Assis · Strength, Intelligence')}${set('day', 'Day and reminders', 'Day starts 04:00 · Reminder 07:00')}${set('health', 'Health and training', 'Health Connect on · Mon, Wed, Fri')}</ul>
      <h2 class="sect">Appearance and sound</h2>
      <ul class="setl">
        <li class="col"><span class="thm">${ic('palette', 22)}<b>Theme</b></span><div class="seg wide"><button>Light</button><button class="on">Dark</button><button>Phone</button></div></li>
        <li>${ic('away', 22)}<span><b>Timer sound</b><small>Soft chime</small></span>${toggle(true)}</li>
        <li>${ic('strength', 22)}<span><b>Haptics</b></span>${toggle(true)}</li>
        <li>${ic('day', 22)}<span><b>Reduce motion</b></span>${toggle(false)}</li>
      </ul>
      <h2 class="sect">More</h2>
      <ul class="setl">${set('widget', 'Widgets and shortcuts', '3 widgets · 1 quick tile')}${set('data', 'Your data', 'Export, import, privacy')}</ul>
      <p class="fine">AWAKEN 1.2. Everything stays on this phone.</p>
    </section>`, { sb: 'onb' });

  AW.dirs.innings = { name: 'Innings', line: 'A Test-match scoreboard. Every key number is a hand-hung plate; a day is an over scored ball by ball. Cherry ball by day, pink ball under floodlights.', light: 'Day session: sightscreen white, bottle-green board, cherry ball', dark: 'Floodlit: deep outfield green, white plates, pink ball', screens: { today, focus, gym, rest, progress, onboarding, settings } };
})();
