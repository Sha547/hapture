# Hapture

Design how an Android interaction feels, on-device, with real touch and
haptics — then copy working Jetpack Compose code.

## Status: Phases 1-9 done -- every interaction type, instrumented tests, a freehand-drawn object shape, timelines, and a doodle canvas

What's implemented and verified on-device (Pixel 9 emulator, API 35):

**Navigation** -- Home (real saved experiments) -> New Experiment (type
picker, honest about what's disabled) -> experiment screen, with
hardware/gesture Back retracing that stack (`MainActivity.kt`'s
`BackHandler`, verified on-device). Plain sealed-class router, not
Navigation-Compose -- see the comment in `MainActivity.kt` for why, and
when to revisit that.

**Presets** (`core/model/MotionPreset.kt`, spec §24) -- 8 named presets
(Gentle, Snappy, Heavy, Bouncy, Elastic, Gooey, Magnetic, Instant), each
just a (stiffness, damping) pair in the same normalized space the sliders
use. Tapping one sets and persists the sliders immediately; dragging a
slider afterward deselects the chip. Both behaviors verified on-device.

**Persistence** (`data/`) -- Room-backed. Picking a type on New Experiment
creates a real row with sensible defaults (`ExperimentRepository.createDefault`)
and navigates straight into it. Each slider persists on release, not per
frame (`LabeledSlider`'s `onValueChangeFinished`), verified surviving a full
`am force-stop` + relaunch -- including the *tuned value itself*, not just
the row existing. Long-press a row on Home to delete it, also verified.
Config is stored as discrete nullable columns rather than a JSON blob (see
the comment in `ExperimentEntity.kt` for why, and when to revisit that).

**Spring Drag** (`feature/editor/SpringDragScreen.kt`) -- drag, real-time
rubber-band overscroll past a boundary (`core/physics/RubberBand.kt`),
release captures real velocity (Compose `VelocityTracker`) and springs back
using Compose's actual `spring()` AnimationSpec -- the live preview *is*
the same primitive that gets exported, not a parallel simulation (see the
fidelity note there and in `export/SpringCodeGenerator.kt`). Three tunable
sliders (Stiffness / Damping / Resistance).

**Magnetic Snap** (`feature/editor/MagneticSnapScreen.kt`) -- drag between
three discrete targets (A/B/C); getting close to one pulls the card toward
it while still under your finger (`core/physics/MagneticSolver.kt`),
release hands off to the nearest target and a real spring
(`core/physics/SnapPoints.kt`) -- same fidelity policy, same
copy-verbatim-into-the-export approach as rubber-band.

Both screens share: a live position curve sampled once per frame
(`feature/editor/PositionCurve.kt`), a haptic engine with device-capability
detection and fallback (`core/haptics/HapticEngine.kt`), and a **Copy
Compose** button that generates a real, deterministic Kotlin snippet and
copies it to the clipboard (`export/`).

## Phase 3: editorial design system

A quiet, flat look: warm off-white canvas, charcoal text, soft grey secondary
text, hairline borders instead of shadows, 8-12dp corners, small tracked-caps
section labels. Set in Manrope (headings run tight, body runs soft grey) with
Geist Mono for numeric readouts; both OFL, bundled in `res/font/`. Icons are one
custom family (`ui/design/Icons.kt`): 24-unit grid, one stroke weight.
An earlier hand-drawn "doodle" skin was tried and dropped.

**Design tokens + themes** (`ui/design/Tokens.kt`) -- Paper, Stone, Graphite, Ink.
One token layer (canvas, surface, line, ink, inkSoft, inkFaint, accent, radii,
hairline) feeds `LocalTokens` and the Material colour scheme; nothing hardcodes
a value. `DesignTokensTest` gates every theme on WCAG contrast (text >= 12:1,
secondary text >= 4.5:1). The choice persists (`ThemeStore`); the default
follows the system light/dark setting until you pick one. Motion is gentle:
staggered fade-up entrances (`reveal`), a soft spring on button press.

**Your own object** -- shape (square/rounded/circle/pill), size and fill
(solid/tint/outline), previewed live on the stage and saved with the experiment
(Room schema v2, `MIGRATION_1_2`; existing rows keep their look). Shapes retired
from the picker (blob, star) load as rounded rather than crash.

**Export to design tools** (`export/`) -- besides Compose code:
- `Design spec`: one JSON with the spring in Compose, Figma and CSS terms, settle
  time, overshoot, the interaction's parameters, the object and the theme.
  Figma's custom spring takes mass 1, stiffness k and *damping* `2*zeta*sqrt(k)`,
  which is what `SpringMath.damping` emits (not the damping ratio).
- `CSS easing`: `linear()` easing + duration sampled from the spring's step response.
- `Share spec`: sends the JSON through the Android share sheet.
- Home > Appearance > "Copy design tokens": the active theme as W3C design tokens
  (Figma variables / Tokens Studio / Style Dictionary).
The Curve section shows a live settle time and overshoot for the current spring.
Spec numbers assume a 0-to-1 step with zero release velocity, so they are
nominal; a real drag adds release velocity.

**Tests** -- `./gradlew :app:testDebugUnitTest` (25): spring maths (all three
damping regimes), spec/tokens JSON validity, theme contrast, relative time,
lenient enum loading. Needs network once to fetch `org.json`.

## Phase 4: swipe and fling, haptic presets, project files

**Swipe and fling** (`feature/editor/SwipeFlingScreen.kt`) -- flick or drag the
card past the dismiss guides and it is thrown off with Compose's real
`exponentialDecay`; let go short and slow and it springs back with a real
`spring()`. The card then fades back in so you can throw it again. Tunables:
dismiss distance, fling sensitivity (velocity threshold), friction, tilt, and the
return spring (presets + stiffness/damping). The decision and tilt live in
`core/physics/SwipeSolver.kt` and are pasted verbatim into the exported Kotlin
(`SwipeCodeGenerator`), which emits dp values so the feel holds across densities.

**Bottom sheet** (`feature/editor/BottomSheetScreen.kt`) -- drag the sheet's edge:
it follows, resists past full (rubber band), carries momentum, and settles at
the nearest of three detents (peek, middle, full) with a real `spring()`; pull it
below the peek, or flick it down, and it dismisses ("Show sheet" brings it back).
Tunables: peek and middle height, momentum, dismiss ease, resistance, the spring,
and its look (corner radius, fill). Detents are fractions of the container, so
the export scales to any screen. The settle rule is `core/physics/SheetSolver.kt`,
pasted verbatim into `SheetCodeGenerator`'s output. `SheetMappingTest` checks
that peek < middle < full for every slider combination.

**Release-velocity fix** -- Spring drag and Swipe and fling used to feed the
velocity tracker `change.position`, which is local to the shape that moves under
the finger, so it barely changed. Measured on-device: a ~3,300 px/s rightward
flick read -1,713 px/s (wrong sign); tracking the accumulated drag instead reads
+3,359. All drag screens now track position in a stable frame.

**Haptic presets** (`core/haptics/`) -- Off, Soft, Crisp (the previous behaviour),
Firm. A preset is a table from interaction event to effect (soft, tick, click,
impact, heavy, success); picking one plays it once. Saved per experiment and
included in the design spec. Note: unit tests cover the tables, but the vibration
itself can only be judged on a phone; the emulator has no real motor.

**`.hapture` files** (`data/ProjectFile.kt`) -- one experiment as versioned JSON
holding the slider positions (not derived numbers), so an import reproduces it
exactly. Home > "Import a .hapture file" and, from a row's long-press menu,
"Export as .hapture", both through the system file picker (no storage
permission). Imports are defensive: wrong format, newer version, unknown type,
missing or non-numeric parameters and oversized files are refused with a reason;
0..1 values are clamped. Verified on-device: import, refusal of a junk file, and
export -> import equality.

Schema is now v4 (`MIGRATION_2_3`, `MIGRATION_3_4`); both verified against real databases from the previous version.

**Tests** -- `./gradlew :app:testDebugUnitTest`. (Running total as of the last phase in this README: 119 unit + 27 instrumented; see "Tests" bullets in each later phase for what was added when.)

## Phase 5: instrumented tests

`./gradlew :app:connectedDebugAndroidTest` (13 tests, needs a running device or
emulator): migrations against real SQLite (`MigrationInstrumentedTest`, one
hand-built database per prior schema version, exactly the shape verified by
hand against the emulator during Phase 4), the repository against real Room
(`RepositoryInstrumentedTest`, including that an unknown enum string written
directly to the table still opens through the real `Converters` -- not just the
pure function `ConvertersTest` already checked), and full navigation flows
through the real `MainActivity` (`ComposeFlowTest`: create an experiment, tune
a slider, leave and reopen it, confirm it persisted; the on-screen back button
and the hardware/gesture `BackHandler` both work; every interaction type opens
from New Experiment and reaches its own Export section without crashing).
`ValueSlider` and `TopBar`'s back control carry `testTag`s (`sliderTestTag(label)`,
`"backButton"`) for this; both are cosmetically invisible.

**Bug found and fixed: every editor screen was never idle.** Each one drove its
live curve with `LaunchedEffect(Unit) { while (true) { withFrameNanos { ... } } }`
-- a per-frame sampling loop with no exit, running for the screen's entire
lifetime regardless of whether anything was moving. `ComposeFlowTest`'s
`waitForIdle()` hung on this (Compose's idle detector never sees "no pending
frame callback"), which is what surfaced it; instrumented tests were built
specifically because this class of bug doesn't exist at the unit-test level, and
it did not take long to find one. Fixed in all four editors: the loop now
suspends on `snapshotFlow { dragging || <animatable>.isRunning }.first { it }`
and only samples while that's true, which is also strictly more correct (a
resting curve was always flat and uninformative) and cheaper.

**A note on how the fix nearly went wrong.** Applying it to two of the four
files with a Python one-liner of the form
`open(path, "w").write(open(path).read().replace(...))` silently emptied both
files: Python evaluates the write-mode `open()` (which truncates on open)
before evaluating the argument, so the `read()` inside sees an already-empty
file. With no git repo yet, this only turned into a full-file rebuild-from-
transcript rather than a permanent loss because the file's exact prior content
was still visible earlier in the same session. **Never open the same path for
write before its read has actually run** -- read into a variable on its own
line first, always.

## Phase 6: freehand-drawn object shape

**Draw your own shape** (`feature/editor/ShapeDrawer.kt`) -- from Spring Drag,
Magnetic Snap or Swipe and Fling's Object section, tap "Custom" (or "Redraw" if
one already exists) to trace a closed outline with a finger. On release it's
normalized into its own centred unit square (aspect preserved, not stretched to
fit), resampled to an even 28 points by arc length so storage size and smoothing
quality don't depend on how fast or slow it was drawn, and smoothed into a
closed Catmull-Rom spline (`core/model/CustomShape.kt`, pure Kotlin, no
Compose/Android dependency -- unit tested directly). The exact same smoothed
curve renders the live object (`ui/design/DrawnShape.kt`, a Compose `Shape`)
and exports as an SVG path in the design spec (`export/SvgPath.kt`) -- draw
once, see it everywhere. Bottom sheet doesn't offer it (a drawn shape means
nothing for a sheet's straight top edge).

Stored as a compact "x,y;x,y;..." string (`CustomShapeCodec`, schema v5,
`MIGRATION_4_5`), decoded defensively like everything else here: malformed
segments dropped, coordinates clamped, absurdly long input capped, never
thrown on. Round-trips through `.hapture` files too.

Note: the doodle/hand-drawn *look* tried earlier in this project (wobbly
outlines everywhere, a sketchbook theme) was rejected and stayed rejected --
this is a distinct, later-approved feature (draw one shape, use it as the
object), not a return to that style. The app's own visual design stays flat
editorial minimalism throughout, including the drawer screen itself.

**Instrumented UI-testing lesson:** a Dialog's content can compose one frame
after the click that triggers it, so `ShapeDrawerFlowTest` awaits it with
`waitUntil { onAllNodesWithText(...).fetchSemanticsNodes().isNotEmpty() }`
rather than a single `waitForIdle()` (fine elsewhere, flaky across a Dialog
boundary). Separately: the Object section sits below the fold on the test
viewport, and `performClick()` does **not** auto-scroll a node into view --
`performScrollTo().performClick()` is required first. Neither is an app bug;
both are now documented workarounds other tests in this codebase should follow
if they reach into a Dialog or a below-the-fold node.

## Build & run

This machine has no standalone JDK, only a JRE — `gradle.properties` points
`org.gradle.java.home` at Android Studio's bundled JBR as a workaround.
Delete that line if you install a proper JDK later.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.klynstudios.hapture/.MainActivity
```

Or just open the project in Android Studio and hit Run.

## Phase 7: timelines

**A timeline** (`feature/timeline/`, `data/TimelineEntity.kt`) is a saved,
ordered sequence of *existing* experiments, each with a gap before it plays.
Home gets its own "Timelines" section alongside Recent, with "New timeline"
landing straight in an empty editor -- no type picker, since a timeline has no
motion of its own to tune. There's deliberately no unified physics engine
behind it: Swipe and Fling isn't the same kind of motion as Spring Drag, so
nothing pretends to render one continuous scene across interaction types.
What it does show, honestly:

- **Pacing** -- a scrub bar (`TimelineScrubber`, built on a real Compose
  `Slider` for free drag/tap-to-seek/accessibility, same pattern as
  `ValueSlider`) proportioned by each step's own settle time
  (`ExperimentEntity.nominalDurationMs`, derived from the same stiffness/
  damping pair every experiment type already has -- its main motion, or its
  return spring -- one formula, no per-type cases).
- **What each step actually looks like moving** -- while the playhead is
  inside a step's own window, the preview stage drives a real object (that
  step's own shape and fill) through that step's own real step-response curve
  (`SpringMath.stepResponse`, already tested elsewhere). Scrubbing and Play
  both just move one time value; nothing new is simulated.

Steps reorder via up/down buttons (deterministic, no drag gesture fighting the
page's own scroll), each with its own gap stepper (0-3000ms, in 100ms steps).
Long-press a step to remove it; long-press a timeline on Home to rename or
delete it. Export is a "Copy sequence spec" JSON (`export/TimelineSpec.kt`):
each step's name, type, gap, cumulative start time and duration -- not the
full motion spec again, since each step's own experiment already exports that.

Both foreign keys (`timeline_steps.timelineId`, `.experimentId`) cascade on
delete at the database level, schema v6 (`MIGRATION_5_6`).

**Bug found and fixed: `OnConflictStrategy.REPLACE` cascade-deletes its own
children.** `ExperimentRepository.save()`/`rename()` and the first version of
`TimelineRepository` all used Room's `@Insert(onConflict = REPLACE)` to update
an *existing* row -- but SQLite's REPLACE is delete-then-reinsert, and once a
foreign key with `ON DELETE CASCADE` points at that row (which none did until
timelines existed), that delete cascades. Every timeline-mutating method ended
by "touching" its parent timeline this way, silently wiping the very steps it
had just written -- and `ExperimentRepository.save()`, called by every editor
screen's slider persist, would have silently detached an experiment from any
timeline the moment you so much as dragged a slider. Caught by
`TimelineRepositoryInstrumentedTest` against real SQLite (a pure-Kotlin test
can't see this -- there's no FK enforcement without real SQLite underneath),
not by inspection. Fixed with real `@Update` DAO methods for every "modify an
existing row" call site; `upsert`/`upsertStep` now exist only for genuinely
new inserts (id = 0, autogenerated, nothing to cascade away).

**Tests** -- 24 unit (pure timeline scheduling math in `TimelineMathTest`,
position-list arithmetic in `TimelineOrderingTest`, `TimelineSpecTest`,
`ExperimentDurationTest`) + 10 instrumented (a v5->v6 migration test; a
dedicated `TimelineRepositoryInstrumentedTest` covering add/remove/reorder and
both cascade-delete directions against real SQLite; `TimelineFlowTest`, a full
build-reorder-trim-and-reopen flow on the real navigation and database).

## Phase 8: more interactions, pin/undo, haptic timeline, doodles

- **Three new interaction types** (schema v8): Pull to Refresh, Pinch to Zoom, Drag to Reorder. Each has a pure solver in `core/physics/`, a code generator, an editor screen and unit tests.
- **Pin a favorite** (v7): long-press a row, "Pin to top"; pinned rows sort first (`ORDER BY pinned DESC, updatedAt DESC`).
- **Undo on delete**: delete is instant, with a 4 second "Deleted / Undo" notice. Restoring re-inserts the row; timeline steps that pointed at a deleted experiment were already cascade-deleted and stay gone. Also covers doodles.
- **Haptic timeline** (v9): haptic markers at absolute times on a timeline, independent of the motion steps, each one of the six `HapticEffect`s. Ticks on the scrub bar, fired during playback via `HapticMarkers.crossed`, included in the sequence spec JSON.
- **Doodles** (v10): a separate Home section with a freehand canvas. Brushes: pen, marker, paint, spray, eraser; 12 colours plus a hue slider; four canvas colours; undo/redo/clear; autosave. Strokes are stored canvas-relative so exports are resolution independent. Export: PNG (saved to Pictures/Hapture, or shared through a FileProvider) and SVG (copied). One painter (`drawDoodle`) serves the live canvas and the PNG, and `DoodleSvg` mirrors it brush for brush.

Tests: 36 instrumented (migrations up to v9 -> v10 chain, pin/undo flows, haptic cascade, doodle drawing flow) plus the unit suite.

## Phase 9: from a tuning tool to a portable motion system

- **Trigger interactions** (schema v11): Toggle, Button press, Tab indicator, Staggered list, Like burst. One editor (`TriggerScreen`); the motion is decided by a tap or press, driven by the same tunable spring.
- **Neutral motion spec** (`core/spec/MotionSpec`): trigger, transitions (property, from, to, delay, spring), parameters, haptic, reduced-motion spring and sampled curve, as JSON. Gesture editors feed it through `MotionSpec.fromDesignJson`, so they needed no per-screen changes.
- **Generators from that spec** (`export/platform/`): SwiftUI, Flutter, React Native (Reanimated), Web (Framer Motion + CSS `linear()`), Lottie (a real file baked from the exact step response), Compose. Every one includes a Reduce Motion path (critically damped, no overshoot).
  These are deterministic templates checked for content in unit tests; they have not been compiled with the Swift, Dart or JS toolchains here.
- **Motion lint** (`MotionLint`): warns about sluggish settle, wobble, large overshoot on controls, jitter-prone stiff/under-damped springs, long stagger, missing haptics. Shown at the top of every Export section.
- **Motion tokens** (v12): "Save as motion token" in any editor, tokens appear as chips under Presets everywhere, and Home exports the whole set as design-tokens JSON, SwiftUI, Kotlin, CSS or TypeScript.
- **Capture a feel**: draw a motion curve, or pick a screen recording, tap the moving object, and `SpringFit` (least squares over stiffness and damping) recovers the spring. Video tracking follows a solid-coloured object by colour (`ObjectTracker`), which suits UI mock recordings, not arbitrary footage. An instrumented test decodes a real encoded mp4 of a known spring (stiffness 500, damping ratio 0.45) and recovers it.
- **Live sync** (`LiveSyncServer`): off by default; when started serves `GET /spec` and an SSE `GET /events` stream on port 8787, so a running dev build or the Figma plugin follows slider changes. No authentication, so anyone on the same network can read it while it is on. Needs the INTERNET permission.
- **Figma plugin** (`figma-plugin/`): applies a spec (pasted, or live) as Smart Animate with Custom spring to the selected layers' existing interactions. Syntax-checked and logic-tested against a mock of the Figma API; not yet run inside Figma.

Tests: 40 instrumented, plus unit tests for the spec, generators, lint, tokens, fitter, tracker and live sync server.

## Not built yet

- Figma-native motion export beyond the spec JSON (no plugin yet)
- Haptic timeline, sharing, community (explicitly post-v1 per the spec)

No git repo has been initialized for this project yet.
