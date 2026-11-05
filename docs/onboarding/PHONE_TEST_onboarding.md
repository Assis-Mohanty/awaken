# Phone test: onboarding quiz

Install the debug build. You already have a profile, so use the preview for most of this; the last section needs a fresh install or cleared app data.

## A. Look at it without losing data
1. Settings → Debug → **Preview onboarding (saves nothing)**. The welcome screen opens in the light palette.
2. Walk all the way to **Your plan** and press **Start**. You return to Debug. Check Today, Gym and Settings: nothing changed (name, stats, quests, streak).
3. Open the preview again, go two screens in, press the phone's Back until the welcome screen, then Back once more. You return to Debug.
4. Kill the app mid-preview and reopen it. The real app opens, not onboarding.

## B. Each screen
- Name: under 3 or over 20 characters keeps Continue off. Name cannot be skipped.
- Age, height, weight: wheels and rulers; the unit toggle switches cm/in and kg/lb. The BMI card appears once height and weight are set.
- Body shape: the slider changes the picture.
- Improve and Obstacles: pick several; "None of these" clears the others.
- Sleep: the wake wheel only offers times that make a valid phone-down window.
- Injuries: tapping a body-map card lights the figure; "None" is exclusive.
- Training days: the reminder switch appears only after a day is picked.
- Pace: the chosen card opens its explanation.
- Permissions (Health, usage access, notifications): "Not now" always moves on. Allow one, then press Back onto it: it is not undone. Below Android 13 the notification screen is skipped.
- Building: the ring fills and ends on **Your plan**. Turn on reduce motion in Settings and repeat: it should step, not sweep.
- Your plan: edit a focus stat, the step goal, a training day and the weight target. The quest list updates.

## C. Real install (clear app data first)
1. Walk the flow, skipping as much as you can. The plan still builds and Start opens Today with quests.
2. Walk it again with everything answered and usage access allowed. Settings → Screen discipline shows the limits from the plan.
3. Pick a training day = today and keep the reminder on. Set the reminder time to a minute from now (Settings → Daily reminder): the notification starts "It's a training day."
4. Gym shows "Today is a training day." on that day and not on others.
5. Settings → Health and training → **Training days**: toggle a day, turn the reminder off. Gym and the notification follow.
6. Kill the app on the plan screen and reopen: it resumes on the plan with your answers.
7. Notifications: if you answered the prompt in the flow, you are not asked again after the first quest.

## D. Upgrade
Install over an older build that had a profile. The app opens on Today, not onboarding, and the data is intact.
