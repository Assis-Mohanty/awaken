/* Magnitude: a star atlas. Importance is dot diameter on one fixed ramp; the day is an arc of sky. */
(() => {
  const ic = AW.ic;
  const rad = (xp) => 3 + xp / 4.500;
  const star = (q) => { const d = rad(q.xp) * 2 + 2; return `<span class="star ${q.done ? 'done' : ''}" style="--d:${d}px"></span>`; };
  const qrow = (q) => `<li class="q ${q.done ? 'done' : ''}"><span class="tick" role="checkbox" aria-checked="${!!q.done}">${star(q)}</span><span class="qt"><b>${q.t}</b><small>${q.s}</small></span><span class="qxp">${q.xp}</span></li>`;

  const skyArc = () => { const w = 350, h = 176, cx = 175, cy = 156, rx = 160, ry = 132, H0 = 5, H1 = 24;
    const at = (hr) => { const t = Math.PI * (1 - (hr - H0) / (H1 - H0)); return [cx + rx * Math.cos(t), cy - ry * Math.sin(t)]; };
    let path = ''; for (let i = 0; i <= 60; i++) { const [x, y] = at(H0 + (i / 60) * (H1 - H0)); path += `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`; }
    const qs = [...D.plan.morning, ...D.plan.day, ...D.plan.evening];
    const stars = qs.map((q) => { const [x, y] = at(q.hour); return `<circle cx="${x}" cy="${y}" r="${rad(q.xp) + (q.done ? 0 : -1)}" class="${q.done ? 'sd' : 'so'}"/>`; }).join('');
    const [nx, ny] = at(13.2); const labels = [['Morning', 8.500], ['Day', 14.500], ['Evening', 20.500]].map(([t, hr]) => `<text x="${at(hr)[0]}" y="${h - 2}" text-anchor="middle" class="sl">${t}</text>`).join('');
    return `<svg class="arc" viewBox="0 0 ${w} ${h}" width="100%" role="img" aria-label="Today's quests across the day, 2 of 6 done, now in the Day block"><path d="${path}" class="dome"/><line x1="10" y1="${cy}" x2="${w - 10}" y2="${cy}" class="hz"/><line x1="${nx}" y1="${ny - 2}" x2="${nx}" y2="${cy}" class="nowl"/><text x="${nx}" y="${ny - 8}" text-anchor="middle" class="nowt">Now</text>${stars}${labels}</svg>`; };

  const orbit = (total, doneN, size = 300) => { const c = size / 2, R = c - 12; let o = ''; for (let i = 0; i < total; i++) { const a = -Math.PI / 2 + (i / total) * 2 * Math.PI, x = c + R * Math.cos(a), y = c + R * Math.sin(a), past = i < doneN, cur = i === doneN; o += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${cur ? 9 : past ? 5.500 : 2.500}" class="${cur ? 'oc' : past ? 'op' : 'of'}"/>`; } return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" aria-hidden="true">${o}</svg>`; };

  const level = () => `<section class="lvl">${AW.ring(66, 2.500, 1240 / 1434, `<b class="n">24</b>`)}<div class="lv"><div class="lv-t"><span class="rk">Rank <i>C</i></span><span class="st">${ic('flame', 18)}<b>12</b> days</span></div><div class="xpb"><i style="width:${(1240 / 1434) * 100}%"></i></div><div class="lv-b"><span>Level 24</span><span><b>1,240</b> of 1,434 XP</span></div></div></section>`;

  const today = () => AW.frame(`<div class="wrap">
      <header class="bar"><div><h1>Good afternoon, Assis</h1><p>Friday 6 November</p></div><button class="icb" aria-label="Settings">${ic('settings')}</button></header>
      ${level()}
      <div class="plan-h"><div><h2>Today's plan</h2><p>2 of 6 done</p></div><div class="acts"><button>Schedule</button><button>Habits</button></div></div>
      <div class="sky">${skyArc()}</div>
      <div class="blk"><button class="bh"><b>Morning</b><span>2 of 2 done</span>${ic('chevron', 18)}</button></div>
      <div class="blk"><div class="bh"><b>Day</b><em>Now</em><span>0 of 2 done</span></div><ul class="qs">${D.plan.day.map(qrow).join('')}</ul></div>
      <div class="blk"><div class="bh"><b>Evening</b><span>0 of 1 done</span></div><ul class="qs">${D.plan.evening.map(qrow).join('')}</ul></div>
      <div class="blk"><div class="bh"><b>Anytime</b><span>0 of 1 done</span></div><ul class="qs">${D.plan.anytime.map(qrow).join('')}</ul></div>
      <div class="tiles"><a class="tile"><span>Focus</span><strong>42<small>m</small></strong><em>of 60 min</em></a><a class="tile"><span>Screen time</span><strong>2<small>h</small> 57<small>m</small></strong><em>54 min distracting</em></a><a class="tile"><span>Steps</span><strong>4,100</strong><em>of 6,000</em></a></div>
    </div>`, { nav: 0 });

  const focus = () => AW.frame(`<div class="wrap run">
      <header class="bar c"><button class="icb" aria-label="Minimise">${ic('down')}</button><div><h1 class="s">15-minute session</h1><p><i>DSA, graphs</i></p></div><span class="icb no"></span></header>
      <div class="orb">${orbit(15, 3)}<div class="clock"><b>11:48</b><p>remaining</p></div></div>
      <ul class="facts"><li>${ic('lock', 20)}<span><b>Focus Lock on</b><small>3 apps blocked</small></span></li><li>${ic('away', 20)}<span><b>Away 0:25</b><small>of 1:30 allowed</small></span></li></ul>
      <div class="dock"><button class="cta">Pause for 5 minutes</button><button class="txt">End session</button></div>
    </div>`);

  const rest = () => AW.frame(`<div class="wrap run">
      <header class="bar c"><span class="icb no"></span><div><h1 class="s">Rest</h1><p>Push day · 30:17</p></div><span class="icb no"></span></header>
      <div class="orb">${orbit(18, 8)}<div class="clock"><b>0:52</b><p>of 1:30</p></div></div>
      <div class="adj"><button>−15 s</button><button>+15 s</button></div>
      <div class="nextup"><span>Next</span><b>Set 3 of 4</b><small>Barbell bench press, 70 kg × 8</small></div>
      <div class="dock"><button class="cta">Skip rest</button></div>
    </div>`);

  const gym = () => AW.frame(`<div class="wrap">
      <header class="bar"><div><h1 class="s">Barbell bench press</h1><p>Push day · 29:05 · exercise 1 of 4</p></div><button class="txt sm">Finish</button></header>
      <div class="sets" role="img" aria-label="Set 3 of 4; sets 1 and 2 done"><span class="sdot done" style="--d:26px"></span><span class="sdot done" style="--d:28px"></span><span class="sdot cur" style="--d:30px"></span><span class="sdot" style="--d:30px"></span><p>Set 3 of 4</p></div>
      <div class="dials">
        <div class="dial"><strong>70<small>kg</small></strong><div class="st2"><button aria-label="Less weight">${ic('minus')}</button><button aria-label="More weight">${ic('plus')}</button></div></div>
        <div class="dial"><strong>8<small>reps</small></strong><div class="st2"><button aria-label="Fewer reps">${ic('minus')}</button><button aria-label="More reps">${ic('plus')}</button></div></div>
      </div>
      <p class="hint">Last time 67.5 kg × 8. Try 9 reps.</p>
      <button class="cta">Complete set</button>
      <ul class="dn"><li><span>Set 1</span><b>60 kg × 10</b></li><li><span>Set 2</span><b>70 kg × 8</b></li></ul>
      <h2 class="sect">Up next</h2>
      <ul class="nxt"><li><span><b>Dips, triceps</b><small>3 sets</small></span>${ic('chevron', 18)}</li><li><span><b>Incline dumbbell press</b><small>3 sets</small></span>${ic('chevron', 18)}</li><li><span><b>Cable fly</b><small>3 sets</small></span>${ic('chevron', 18)}</li></ul>
      <div class="more"><button>${ic('plus', 20)}Add exercise</button><button>${ic('swap', 20)}Swap</button></div>
    </div>`);

  const constellation = () => { const s = 296, c = s / 2, R = 104; const now = AW.pent(c, c + 2, R, D.stats.map((x) => x.v / 25)), pr = AW.pent(c, c + 2, R, D.stats.map((x) => x.prev / 25)), rim = AW.pent(c, c + 2, R, [1, 1, 1, 1, 1]);
    const lab = AW.pent(c, c + 2, R + 24, [1, 1, 1, 1, 1]).map((p, i) => `<text x="${p[0]}" y="${p[1] + 4}" text-anchor="middle">${D.stats[i].k}</text>`).join('');
    return `<svg class="radar" viewBox="-36 0 ${s + 72} ${s}" width="100%" role="img" aria-label="Stat balance now and last week"><circle cx="${c}" cy="${c + 2}" r="${R}" class="gr"/><circle cx="${c}" cy="${c + 2}" r="${R * 0.5}" class="gr"/>${rim.map((p) => `<line x1="${c}" y1="${c + 2}" x2="${p[0]}" y2="${p[1]}" class="gr"/>`).join('')}<polygon points="${AW.pts(pr)}" class="pv"/><polygon points="${AW.pts(now)}" class="nw"/>${now.map((p, i) => `<circle cx="${p[0]}" cy="${p[1]}" r="${2 + D.stats[i].v / 3.500}" class="sd"/>`).join('')}${lab}</svg>`; };
  const trend = () => { const w = 350, h = 140, max = 760, X = (i) => 14 + (i * (w - 40)) / 7, Y = (v) => h - 24 - (v / max) * (h - 44); const pts = D.xpWeeks.map((v, i) => [X(i), Y(v)]);
    return `<svg viewBox="0 0 ${w} ${h}" width="100%" role="img" aria-label="XP per week, last 8 weeks, rising to 690"><polyline points="${AW.pts(pts)}" class="tl"/>${pts.map((p, i) => `<circle cx="${p[0]}" cy="${p[1]}" r="${2.500 + D.xpWeeks[i] / 130}" class="${i === 7 ? 'sd' : 'tp'}"/>`).join('')}<text class="v" x="${pts[7][0]}" y="${pts[7][1] - 15}" text-anchor="middle">690</text><text class="ax" x="4" y="${h - 3}">8 weeks ago</text><text class="ax" x="${w}" y="${h - 3}" text-anchor="end">This week</text></svg>`; };
  const skymap = () => `<svg viewBox="0 0 ${18 * 19.300} ${7 * 19.300}" width="100%" role="img" aria-label="Activity over the last 18 weeks, 94 active days">${D.heat.map((col, c) => col.map((v, d) => `<circle cx="${c * 19.300 + 9.600}" cy="${d * 19.300 + 9.600}" r="${[1.200, 3, 4.800, 6.600, 8.800][v]}" class="${v ? 'hs' : 'h0'}"/>`).join('')).join('')}</svg>`;

  const progress = () => AW.frame(`<div class="wrap">
      <header class="bar"><div><h1>Progress</h1><p>Assis</p></div></header>
      <section class="herow">${AW.ring(138, 3, 1240 / 1434, `<b class="n xl">24</b><small>Level</small>`)}
        <div class="hr"><span class="rk big">Rank <i>C</i></span><div class="xp"><b>1,240</b> of 1,434 XP</div><div class="xpb"><i style="width:${(1240 / 1434) * 100}%"></i></div><span class="st">${ic('flame', 18)}<b>12</b> day streak</span></div></section>
      <ul class="links">${[['cal', 'Weekly review', 'Open now'], ['program', 'Programs', 'Day 12 of 30'], ['quit', 'Quit counters', '12 days clean'], ['store', 'Store', '1,240 gold'], ['health', 'Health', '4,100 steps']].map(([i, t, v]) => `<li>${ic(i, 22)}<b>${t}</b><span>${v}</span>${ic('chevron', 18)}</li>`).join('')}</ul>
      <h2 class="sect">Stat balance</h2>
      <div class="radw sky">${constellation()}<div class="leg"><span><i class="a"></i>Now</span><span><i class="b"></i>Last week</span></div></div>
      <ul class="statl">${D.stats.map((s) => `<li><span class="sdot2" style="--d:${8 + s.v * 0.8}px"></span><span>${s.k}</span><b>${s.v}</b></li>`).join('')}</ul>
      <h2 class="sect">Experience per week <em>Up 21%</em></h2>
      <div class="chart">${trend()}</div>
      <h2 class="sect">Last 18 weeks <em>94 active days</em></h2>
      <div class="chart sky pad">${skymap()}</div>
    </div>`, { nav: 4 });

  const dots = () => { let t = ''; for (let v = 150; v <= 190; v++) { const x = (v - 150) * 13 - 65, far = Math.abs(v - 170), r = v % 10 === 0 ? 3.500 : v % 5 === 0 ? 2.500 : 1.300; if (x < -6 || x > 396) continue; t += `<circle cx="${x}" cy="30" r="${v === 170 ? 0 : r}" class="rd" opacity="${Math.max(0.45, 1 - far / 22)}"/>`; if (v % 10 === 0) t += `<text x="${x}" y="58" text-anchor="middle">${v}</text>`; } return `<svg class="rul" viewBox="0 0 390 66" width="390" height="66" aria-hidden="true">${t}<circle cx="195" cy="30" r="11" class="sd"/></svg>`; };
  const onboarding = () => AW.frame(`<div class="wrap ob">
      <header class="obh"><button class="icb" aria-label="Back">${ic('back')}</button><div class="segs" role="img" aria-label="Step 5 of 21"><i class="f"></i><i class="p"></i><i></i><i></i><i></i></div><button class="txt sm">Skip</button></header>
      <h1>How tall are you?</h1><p class="lead">Used for your plan and a rough BMI.</p>
      <div class="val"><strong>170</strong><small>cm</small></div>
      <div class="seg center"><button class="on">cm</button><button>ft</button></div>
      <div class="rulw">${dots()}</div>
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
        <li class="col"><span class="thm">${ic('palette', 22)}<b>Theme</b></span><div class="seg wide"><button>Light</button><button class="on">Dark</button><button>Phone</button></div></li>
        <li>${ic('away', 22)}<span><b>Timer sound</b><small>Soft chime</small></span>${toggle(true)}</li>
        <li>${ic('strength', 22)}<span><b>Haptics</b></span>${toggle(true)}</li>
        <li>${ic('day', 22)}<span><b>Reduce motion</b></span>${toggle(false)}</li>
      </ul>
      <h2 class="sect">More</h2>
      <ul class="setl">${set('widget', 'Widgets and shortcuts', '3 widgets · 1 quick tile')}${set('data', 'Your data', 'Export, import, privacy')}</ul>
      <p class="fine">AWAKEN 1.2. Everything stays on this phone.</p>
    </div>`);

  AW.dirs.magnitude = { name: 'Magnitude', line: 'A star atlas. Importance is dot size on one fixed scale: the day is an arc of sky, each quest a star sized by its reward, your history a star map. First light by day, deep sky by night.', light: 'First light: pale periwinkle, ink-blue chart panels, copper', dark: 'Deep sky: saturated navy, chalk stars, amber', screens: { today, focus, gym, rest, progress, onboarding, settings } };
})();
