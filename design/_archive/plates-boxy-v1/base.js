/* Plates: the five stats wear the five competition plate colours. Finishing a quest loads a plate on today's bar. */
(() => {
  const ic = AW.ic;
  const all = [...D.plan.morning, ...D.plan.day, ...D.plan.evening, ...D.plan.anytime];
  const qrow = (q) => `<li class="q ${q.done ? 'done' : ''}"><span class="tick">${q.done ? ic('check', 18) : ''}</span><span class="qt"><b>${q.t}</b><small>${q.s}</small></span><i class="sp s-${q.stat}"></i><span class="qxp">+${q.xp}</span></li>`;
  const knurl = (pct) => `<div class="kn" role="img" aria-label="${Math.round(pct * 100)} percent"><i style="width:${pct * 100}%"></i></div>`;

  const dayBar = () => { let x = 92, r = ''; all.forEach((q) => { const w = 16 + q.xp * 0.36; r += q.done ? `<rect x="${x}" y="6" width="${w}" height="84" rx="3" class="f-${q.stat}"/>` : `<rect x="${x + 0.75}" y="6.750" width="${w - 1.500}" height="82.500" rx="3" class="ghost"/>`; x += w + 3; });
    return `<svg class="loadbar" viewBox="0 0 350 96" width="100%" role="img" aria-label="Today's bar: 2 of 6 quests loaded"><defs><pattern id="kn" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="5" height="5" class="kbg"/><path d="M0 0v5M0 0h5" class="kl"/></pattern></defs><rect x="0" y="43" width="72" height="10" fill="url(#kn)"/><rect x="72" y="33" width="14" height="30" rx="2" class="col"/><rect x="86" y="40" width="264" height="16" rx="2" class="sl"/>${r}</svg>`; };

  const gymBar = () => { const side = (dir) => { const x0 = dir < 0 ? 104 : 246; const w = 26; return `<rect x="${dir < 0 ? x0 - w : x0}" y="4" width="${w}" height="88" rx="3" class="f-str"/><text x="${dir < 0 ? x0 - w / 2 : x0 + w / 2}" y="53" text-anchor="middle" class="pn">25</text>`; };
    return `<svg class="loadbar g" viewBox="0 0 350 96" width="100%" role="img" aria-label="70 kilograms: bar plus a 25 kilogram plate on each side"><rect x="0" y="40" width="350" height="16" rx="2" class="sl"/><rect x="108" y="43" width="134" height="10" fill="url(#kn2)"/><defs><pattern id="kn2" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><rect width="5" height="5" class="kbg"/><path d="M0 0v5M0 0h5" class="kl"/></pattern></defs><rect x="104" y="33" width="12" height="30" rx="2" class="col"/><rect x="234" y="33" width="12" height="30" rx="2" class="col"/>${side(-1).replace(/104/g, '104')}${side(1)}</svg>`; };

  const level = () => `<section class="lvl">
      ${AW.ring(76, 7, 1240 / 1434, `<b class="n">24</b>`, '', 'butt')}
      <div class="lv"><div class="lv-t"><span class="rk">Rank <b>C</b></span><span class="st">${ic('flame', 20)}<b>12</b> days</span></div>${knurl(1240 / 1434)}<div class="lv-b"><span>Level 24</span><span><b>1,240</b> of 1,434 XP</span></div></div></section>`;

  const today = () => AW.frame(`<div class="wrap">
      <header class="bar"><div><h1>Good afternoon, Assis</h1><p>Friday 6 November</p></div><button class="icb" aria-label="Settings">${ic('settings')}</button></header>
      ${level()}
      <div class="plan-h"><h2>Today's plan</h2><div class="acts"><button>Rest day</button><button>Habits</button></div></div>
      ${dayBar()}
      <p class="loaded"><b>2</b> of 6 done</p>
      <div class="blk"><button class="bh"><b>Morning</b><span>2 of 2 done</span>${ic('chevron', 18)}</button></div>
      <div class="blk"><div class="bh"><b>Day</b><em>Now</em><span>0 of 2 done</span></div><ul class="qs">${D.plan.day.map(qrow).join('')}</ul></div>
      <div class="blk"><div class="bh"><b>Evening</b><span>0 of 1 done</span></div><ul class="qs">${D.plan.evening.map(qrow).join('')}</ul></div>
      <div class="blk"><div class="bh"><b>Anytime</b><span>0 of 1 done</span></div><ul class="qs">${D.plan.anytime.map(qrow).join('')}</ul></div>
      <div class="tiles"><a class="tile"><span>Focus</span><strong>42<small>m</small></strong><em>of 60 min</em></a><a class="tile"><span>Screen</span><strong>2<small>h</small>57</strong><em>54 min distracting</em></a><a class="tile"><span>Steps</span><strong>4,100</strong><em>of 6,000</em></a></div>
    </div>`, { nav: 0 });

  const field = (cls, left, body) => `<div class="field ${cls}"><div class="fill" style="height:${left}%"></div><div class="fieldc">${body}</div></div>`;
  const focus = () => AW.frame(field('c-int', 79, `
      <header class="bar c"><button class="icb" aria-label="Minimise">${ic('down')}</button><div><h1 class="s">15-minute session</h1><p>DSA, graphs · Intelligence</p></div><span class="icb no"></span></header>
      <div class="clock"><b>11:48</b><p>remaining</p></div>
      <ul class="facts"><li>${ic('lock', 22)}<span><b>Focus Lock on</b><small>3 apps blocked</small></span></li><li>${ic('away', 22)}<span><b>Away 0:25</b><small>of 1:30 allowed</small></span></li></ul>
      <div class="dock"><button class="cta inv">Pause for 5 minutes</button><button class="txt">End session</button></div>`), { sb: 'onc' });

  const rest = () => AW.frame(field('c-str', 58, `
      <header class="bar c"><span class="icb no"></span><div><h1 class="s">Rest</h1><p>Push day · 30:17</p></div><span class="icb no"></span></header>
      <div class="clock"><b>0:52</b><p>of 1:30</p></div>
      <div class="adj"><button>−15 s</button><button>+15 s</button></div>
      <div class="nextup"><span>Next</span><b>Set 3 of 4</b><small>Barbell bench press, 70 kg × 8</small></div>
      <div class="dock"><button class="cta inv">Skip rest</button></div>`), { sb: 'onc' });

  const gym = () => AW.frame(`<div class="wrap">
      <header class="bar"><div><h1 class="s">Barbell bench press</h1><p>Push day · 29:05 · exercise 1 of 4</p></div><button class="txt sm">Finish</button></header>
      ${gymBar()}
      <p class="barn">Bar 20 kg + 25 kg a side</p>
      <div class="setn"><span>Set 3 of 4</span><span class="pips"><i class="f"></i><i class="f"></i><i class="c"></i><i></i></span></div>
      <div class="dials">
        <div class="dial"><strong>70<small>kg</small></strong><div class="st2"><button aria-label="Less weight">${ic('minus')}</button><button aria-label="More weight">${ic('plus')}</button></div></div>
        <div class="dial"><strong>8<small>reps</small></strong><div class="st2"><button aria-label="Fewer reps">${ic('minus')}</button><button aria-label="More reps">${ic('plus')}</button></div></div>
      </div>
      <p class="hint">Last time 67.5 kg × 8. Try 9 reps.</p>
      <button class="cta">Complete set</button>
      <ul class="dn"><li>${ic('check', 18)}<span>Set 1</span><b>60 kg × 10</b></li><li>${ic('check', 18)}<span>Set 2</span><b>70 kg × 8</b></li></ul>
      <h2 class="sect">Up next</h2>
      <ul class="nxt"><li><i class="sp s-str"></i><span><b>Dips, triceps</b><small>3 sets</small></span>${ic('chevron', 18)}</li><li><i class="sp s-str"></i><span><b>Incline dumbbell press</b><small>3 sets</small></span>${ic('chevron', 18)}</li><li><i class="sp s-str"></i><span><b>Cable fly</b><small>3 sets</small></span>${ic('chevron', 18)}</li></ul>
      <div class="more"><button>${ic('plus', 20)}Add exercise</button><button>${ic('swap', 20)}Swap</button></div>
    </div>`);

  const radar = () => { const s = 290, c = s / 2, R = 100; const grid = [0.25, 0.5, 0.75, 1].map((f) => `<polygon points="${AW.pts(AW.pent(c, c + 2, R, [f, f, f, f, f]))}" class="gr"/>`).join('');
    const now = AW.pent(c, c + 2, R, D.stats.map((x) => x.v / 25)), pr = AW.pent(c, c + 2, R, D.stats.map((x) => x.prev / 25));
    const lab = AW.pent(c, c + 2, R + 25, [1, 1, 1, 1, 1]).map((p, i) => `<text x="${p[0]}" y="${p[1] + 4}" text-anchor="middle">${D.stats[i].k}</text>`).join('');
    return `<svg class="radar" viewBox="-36 0 ${s + 72} ${s}" width="100%" role="img" aria-label="Stat balance now and last week">${grid}<polygon points="${AW.pts(pr)}" class="pv"/><polygon points="${AW.pts(now)}" class="nw"/>${now.map((p, i) => `<rect x="${p[0] - 5}" y="${p[1] - 9}" width="10" height="18" rx="2" class="f-${D.stats[i].id} rp"/>`).join('')}${lab}</svg>`; };
  const stacked = () => { const w = 350, h = 150, b = 32, gap = (w - b * 8) / 7, max = 760; return `<svg viewBox="0 0 ${w} ${h}" width="100%" role="img" aria-label="XP per week by stat, rising to 690 this week">${D.xpByStat.map((col, i) => { let y = h - 18, r = ''; col.forEach((v, k) => { const bh = (v / max) * (h - 36); y -= bh; r += `<rect x="${i * (b + gap)}" y="${y}" width="${b}" height="${Math.max(bh - 1.500, 1)}" class="f-${D.stats[k].id}"/>`; }); return r + (i === 7 ? `<text class="v" x="${i * (b + gap) + b / 2}" y="${y - 5}" text-anchor="middle">690</text>` : ''); }).join('')}<text class="ax" x="0" y="${h - 3}">8 weeks ago</text><text class="ax" x="${w}" y="${h - 3}" text-anchor="end">This week</text></svg>`; };
  const heat = () => `<svg viewBox="0 0 ${18 * 19 - 3} ${7 * 19 - 3}" width="100%" role="img" aria-label="Activity over the last 18 weeks, 94 active days">${D.heat.map((col, c) => col.map((v, d) => `<rect x="${c * 19}" y="${d * 19}" width="16" height="16" rx="2" class="h${v}"/>`).join('')).join('')}</svg>`;

  const progress = () => AW.frame(`<div class="wrap">
      <header class="bar"><div><h1>Progress</h1><p>Assis</p></div></header>
      <section class="herow">
        ${AW.ring(136, 10, 1240 / 1434, `<b class="n xl">24</b><small>Level</small>`, '', 'butt')}
        <div class="hr"><span class="rk big">Rank <b>C</b></span><div class="xp"><b>1,240</b> of 1,434 XP</div>${knurl(1240 / 1434)}<span class="st">${ic('flame', 20)}<b>12</b> day streak</span></div>
      </section>
      <ul class="links">${[['cal', 'Weekly review', 'Open now'], ['program', 'Programs', 'Day 12 of 30'], ['quit', 'Quit counters', '12 days clean'], ['store', 'Store', '1,240 gold'], ['health', 'Health', '4,100 steps']].map(([i, t, v], k) => `<li class="${k === 4 ? 'full' : ''}">${ic(i, 22)}<span><b>${t}</b><small>${v}</small></span></li>`).join('')}</ul>
      <h2 class="sect">Stat balance</h2>
      <div class="radw">${radar()}</div>
      <ul class="statl">${D.stats.map((s) => `<li><i class="sp s-${s.id}"></i><span>${s.k}</span><div class="sb2"><i class="f-bg-${s.id}" style="width:${(s.v / 25) * 100}%"></i></div><b>${s.v}</b></li>`).join('')}</ul>
      <h2 class="sect">Experience per week <em>Up 21%</em></h2>
      <div class="chart">${stacked()}</div>
      <h2 class="sect">Last 18 weeks <em>94 active days</em></h2>
      <div class="chart">${heat()}</div>
    </div>`, { nav: 4 });

  const onboarding = () => AW.frame(`<div class="wrap ob">
      <header class="obh"><button class="icb" aria-label="Back">${ic('back')}</button><div class="segs" role="img" aria-label="Step 5 of 21"><i class="f"></i><i class="p"></i><i></i><i></i><i></i></div><button class="txt sm">Skip</button></header>
      <h1>How tall are you?</h1><p class="lead">Used for your plan and a rough BMI.</p>
      <div class="seg center"><button class="on">cm</button><button>ft</button></div>
      <div class="wheel" role="slider" aria-label="Height" aria-valuenow="170"><span class="o3">167</span><span class="o2">168</span><span class="o1">169</span><span class="cur">170<small>cm</small></span><span class="o1">171</span><span class="o2">172</span><span class="o3">173</span></div>
      <div class="bmi"><span>BMI <b>23.5</b></span><small>Healthy range</small></div>
      <div class="dock"><button class="cta">Continue</button></div>
    </div>`);

  const set = (i, t, v) => `<li>${ic(i, 22)}<span><b>${t}</b><small>${v}</small></span>${ic('chevron', 18)}</li>`;
  const toggle = (on) => `<span class="sw ${on ? 'on' : ''}" role="switch" aria-checked="${on}"><i>${on ? ic('check', 14) : ''}</i></span>`;
  const settings = () => AW.frame(`<div class="wrap">
      <header class="bar"><button class="icb back" aria-label="Back">${ic('back')}</button><h1 class="s grow">Settings</h1></header>
      <ul class="setl">${set('profile', 'Profile', 'Assis · Strength, Intelligence')}${set('day', 'Day and reminders', 'Day starts 04:00 · Reminder 07:00')}${set('health', 'Health and training', 'Health Connect on · Mon, Wed, Fri')}</ul>
      <h2 class="sect">Appearance and sound</h2>
      <ul class="setl">
        <li class="col"><span class="thm">${ic('palette', 22)}<b>Theme</b></span><div class="seg wide"><button>Light</button><button>Dark</button><button class="on">Phone</button></div></li>
        <li>${ic('away', 22)}<span><b>Timer sound</b><small>Soft chime</small></span>${toggle(true)}</li>
        <li>${ic('strength', 22)}<span><b>Haptics</b></span>${toggle(true)}</li>
        <li>${ic('day', 22)}<span><b>Reduce motion</b></span>${toggle(false)}</li>
      </ul>
      <h2 class="sect">More</h2>
      <ul class="setl">${set('widget', 'Widgets and shortcuts', '3 widgets · 1 quick tile')}${set('data', 'Your data', 'Export, import, privacy')}</ul>
      <p class="fine">AWAKEN 1.2. Everything stays on this phone.</p>
    </div>`);

  AW.p = { qrow, knurl, dayBar, gymBar, level, field, radar, stacked, heat, set, toggle, ic };
  AW.dirs.plates = { name: 'Plates', line: 'Calibrated competition plates. The five stats wear the five plate colours, and each finished quest loads a plate on today\'s bar. Chalk by day, rubber floor by night.', light: 'Chalk: dusted white, steel, full plate colour', dark: 'Rubber floor: warm charcoal, chalk ink, brighter plates', screens: { today, focus, gym, rest, progress, onboarding, settings } };
})();
