# Onboarding assets

Every image the onboarding needs. **46 images.** Counts assume Q2 (body shape) stays; dropping it removes 15.

## Rules

- **Format:** WebP with alpha, transparent background, in `app/src/main/res/drawable-nodpi/`.
- **Name:** `onb_<step>_<variant>_<gender>`, lowercase. `<gender>` is `m`, `f` or `n` (neutral). Images with no person always use `n`.
- **Fallback:** a missing `_m` or `_f` falls back to `_n`, so every gendered image must have an `_n` version.
- **Weight:** 60 KB or less each. About 2.5 MB in total.
- **Style:** one consistent style across the set. Must read on the light onboarding background. Faces and skin tones are optional; the neutral set has neither.
- **Original work only.** Nothing traced from, or based on, the reference app.
- **No text inside images.** All words come from strings so they can be translated and resized.

## Sizes

| Type | Pixels | Ratio |
|---|---|---|
| Hero | 1200 × 1200 | 1:1 |
| Tall figure | 720 × 1400 | about 1:2 |
| Stage figure | 900 × 1200 | 3:4 |
| Card | 480 × 360 | 4:3 |
| Permission | 800 × 600 | 4:3 |

## Images

### Step 1: Welcome (1, hero)
| Name | Shows |
|---|---|
| `onb_welcome_hero_n` | One calm, abstract scene for "Let's set up your plan" |

### Step 5: Height (3, tall figure)
Figure in side profile, feet on the bottom edge and head at the top edge, so the ruler lines up with them.

| Name | Shows |
|---|---|
| `onb_height_profile_m` | Male side profile |
| `onb_height_profile_f` | Female side profile |
| `onb_height_profile_n` | Neutral silhouette |

### Step 7: Body shape (15, stage figure)
Front view, same pose, framing and alignment across all five stages, because the images crossfade. Stage 1 is the leanest and stage 5 the largest. Keep the tone neutral: no stage should look like a "before" or an "after".

| Name | Stages |
|---|---|
| `onb_shape_1_m` to `onb_shape_5_m` | 5 |
| `onb_shape_1_f` to `onb_shape_5_f` | 5 |
| `onb_shape_1_n` to `onb_shape_5_n` | 5 |

### Step 8: What do you want to improve? (7, card)
Objects or scenes, no people.

| Name | Shows |
|---|---|
| `onb_improve_strength_n` | Strength |
| `onb_improve_focus_n` | Focus |
| `onb_improve_sleep_n` | Sleep |
| `onb_improve_screen_n` | Less screen time |
| `onb_improve_discipline_n` | Discipline |
| `onb_improve_fitness_n` | Fitness |
| `onb_improve_learning_n` | Learning |

### Step 9: Things that get in your way (5, card)
Objects or scenes, no people.

| Name | Shows |
|---|---|
| `onb_obstacle_scrolling_n` | Too much scrolling |
| `onb_obstacle_sleep_n` | Poor sleep |
| `onb_obstacle_sitting_n` | Sitting all day |
| `onb_obstacle_procrastination_n` | Procrastination |
| `onb_obstacle_none_n` | None |

### Step 12: Fitness level (9, card)
A person training, with effort rising from left to right across the three levels.

| Name | Gender |
|---|---|
| `onb_fitness_beginner_m`, `_f`, `_n` | 3 |
| `onb_fitness_intermediate_m`, `_f`, `_n` | 3 |
| `onb_fitness_advanced_m`, `_f`, `_n` | 3 |

### Step 13: Equipment (3, card)
Objects, no people.

| Name | Shows |
|---|---|
| `onb_equipment_bodyweight_n` | Bodyweight (for example a mat) |
| `onb_equipment_dumbbells_n` | Dumbbells |
| `onb_equipment_gym_n` | Gym |

### Step 17: Permissions (3, permission)

| Name | Shows |
|---|---|
| `onb_permission_health_n` | Health Connect: steps and sleep |
| `onb_permission_usage_n` | Usage access: phone time |
| `onb_permission_notifications_n` | Notifications |

## No image needed

| Step | Why |
|---|---|
| 2 Name, 4 Age, 6 Weight, 11 Sleep, 15 Training days | Inputs fill the screen |
| 3 Gender, 10 Screen time, 16 Pace | Text cards |
| 14 Injuries | Uses the existing `BodyMap` figure, drawn from tokens |
| 18 Building, 19 Your plan | Ring, cards and chart are drawn from tokens |

## App logo

The shell uses the existing launcher mark, `ic_launcher_foreground`, so it needs no new asset. Tell me if you want a separate small wordmark.

## Placeholders

Until the final art arrives, each name above exists as a vector drawable in `res/drawable/`: a grey silhouette crossed by a diagonal hatch band, so it can't be mistaken for final art.

To swap one in, add the WebP to `drawable-nodpi/` and delete the placeholder of the same name (Android rejects two resources with one name). `OnboardingAssetsTest` checks all names resolve. Re-record the goldens for the affected steps after a swap.
