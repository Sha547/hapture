# Motion Lab: what the app contains

Motion Lab is an Android app (Jetpack Compose, package `com.motionlab.app`) for designing how an interaction *feels* on a real device, with real touch and real haptics, and then taking the result out as numbers and code for any platform.

The core idea: a motion is decided by a **trigger** (a drag, a tap, a press) and driven by a **spring**. You tune the spring by feel, and the app exports it in forms a designer or a developer on any platform can use.

- Minimum Android 8.0 (API 26), target 35
- Local-first: no account, no cloud, no onboarding wall
- Storage: Room (SQLite), currently schema **v14**

---

## 1. Interaction types (13)

### Gesture-driven
| Type | What you tune |
|---|---|
| **Spring drag** | Rubber-band resistance, spring release |
| **Magnetic snap** | Pull strength and capture radius toward the nearest point |
| **Swipe and fling** | Dismiss distance, velocity sensitivity, friction, tilt |
| **Bottom sheet** | Peek and middle detents, momentum, dismiss line, resistance |
| **Pull to refresh** | Trigger distance, hold time, continuous resistance |
| **Pinch to zoom** | Min and max zoom, resistance, always settles back to 1x |
| **Drag to reorder** | Swap threshold, spring settle of the dragged row |
| **Predictive back** | Android's back swipe: how far the page shrinks and leans, and the spring that finishes or cancels it |

### Trigger-driven (tap or press)
| Type | What moves |
|---|---|
| **Toggle** | Thumb travel and track fill, thumb stretch |
| **Button press** | Press depth and release |
| **Tab indicator** | Sliding underline between tabs |
| **Staggered list** | Items arriving one after another (opacity and rise, per-item delay) |
| **Like burst** | Heart pop with radial particles |

Every type shares: stiffness and damping sliders (showing the real stiffness and damping ratio), eight built-in presets (Gentle, Snappy, Heavy, Bouncy, Elastic, Gooey, Magnetic, Instant), Material 3's six spatial springs (standard and expressive; fast, default, slow), a "Compare with another spring" link, haptic presets (Off, Soft, Crisp, Firm), a live position curve with settle time and overshoot readout, and an Export section.

Gesture types also let you restyle the moved object: shape (square, rounded, circle, pill, or a **hand-drawn custom shape**), size and fill (solid, tint, outline).

---

## 2. Exporting motion (the point of the app)

Everything is generated from one **platform-neutral motion spec** (JSON): trigger, transitions (property, from, to, delay, spring), parameters, haptic, reduced-motion spring, and the sampled curve.

| Export | Notes |
|---|---|
| **Compose code** | Kotlin `spring()` plus the gesture handling for that interaction |
| **Design spec** | JSON with Figma custom-spring numbers, CSS, the object and the theme |
| **CSS easing** | `linear()` curve and duration |
| **SwiftUI** | `interpolatingSpring`, Reduce Motion aware |
| **Flutter** | `SpringDescription` and `SpringSimulation` |
| **React Native** | Reanimated `withSpring` |
| **Web** | Framer Motion, plus plain CSS with `prefers-reduced-motion` |
| **Lottie** | A real `.json` animation baked from the spring's exact step response |
| **Motion spec** | The neutral JSON all of the above are built from |
| **Share spec** | Sends the JSON to another app |

- **Reduced motion:** every generated snippet has a Reduce Motion path (same stiffness, critically damped, no overshoot).
- **Checks (motion lint):** flags sluggish settle, visible wobble, large overshoot on controls, jitter-prone stiff and under-damped springs, long stagger, missing haptics. Every check except long stagger has a one-tap fix (computed on tap, always within the sliders' range), with Undo for 10 seconds.
- **Files:** experiments export and import as `.motionlab` files (versioned, defensively parsed).

---

## 3. Motion tokens

- "Save as motion token" in any editor names the current spring.
- Saved tokens appear as chips under Presets in every editor.
- Home lists them and exports the whole set as **design-tokens JSON, SwiftUI, Kotlin, CSS or TypeScript**.

## 4. Capture a feel

Turn something you can show into a spring:
- **Draw a curve:** sketch how far it has moved over time; the app fits a spring.
- **Match a video:** pick a screen recording, tap the moving object, and the app tracks it by colour and fits a spring. Works for a solid-coloured shape on a plain background (typical UI mock recordings), not arbitrary footage.
- The result shows stiffness, damping ratio, settle time, overshoot, fit error, and lint notes. You can create an experiment from it or save it as a token.

## 5. Live sync

- Off until you start it, from any Export section.
- Serves the spec being edited on the local network: `GET /spec` and a Server-Sent Events stream `GET /events` (port 8787).
- A running dev build or the Figma plugin follows slider changes in real time; the app generates JavaScript, Kotlin and Swift client snippets.
- No authentication: anyone on the same network can read it while it is on.

## 6. Figma plugin (`figma-plugin/`)

Applies a spec (pasted, or followed live) as Smart Animate with **Custom spring** to the prototype interactions of the selected layers. It only edits transitions that already exist. Syntax-checked and logic-tested against a mock of the Figma API; not yet run inside Figma.

---

## 7. Timelines

- A saved, ordered sequence of existing experiments with gaps between steps.
- A scrub bar proportioned by each step's own settle time, and a preview object driven by the active step's real spring curve.
- **Haptic markers:** add haptic pulses (soft, tick, click, impact, heavy, success) at specific times, independent of the motion steps. They show as ticks on the scrub bar, fire during playback, and are included in the sequence JSON export.
- Reorder steps, adjust gaps, remove steps.

## 8. Doodles

A separate section for freehand sketching (not part of the motion pipeline):
- Brushes: pen, marker, paint, spray, eraser
- 12 colours plus a hue slider, size slider, four canvas colours
- Undo, redo, clear, autosave
- Export: **PNG** (saved to Pictures/Motion Lab, or shared), **SVG** (copied)

## 9. Home and everyday use

- **Recent** experiments, with rename, delete, export.
- **Pin** a favorite to the top (long-press).
- **Undo on delete:** delete is instant with a 4-second Undo (experiments and doodles).
- Import a `.motionlab` file.
- Themes: **Paper, Stone, Graphite, Ink**, plus a "copy design tokens" export of the current theme.

---

## 10. Design system

Quiet editorial minimalism: Manrope type, warm monochrome, hairline borders instead of shadows, small type, gentle motion. All colours and radii go through `ui/design/Tokens.kt`. Custom stroke-only icon family.

## 11. Code layout

```
app/src/main/java/com/motionlab/app/
  core/        physics solvers, spec + lint, capture (fit, tracker), doodle model, sync server, haptics
  data/        Room entities, DAOs, repositories, migrations, .motionlab file format
  export/      code generators (Compose per type, platform/ for the rest), spec + token exporters
  feature/     editor (all interaction screens), timeline, doodle, capture, home, common
  ui/          design system and theme
figma-plugin/  manifest.json, code.js, ui.html, README.md
```

Pure logic (physics, spec, lint, fit, tracker, generators, codecs) has no Android dependency, so it is unit-tested on the JVM. Anything touching real Room or real Compose is tested on a device.

## 12. Database (schema v12)

| Version | Added |
|---|---|
| v2 to v5 | Object look, swipe/fling params and haptic preset, bottom sheet params, custom drawn shape |
| v6 | Timelines and steps |
| v7 | Pinned experiments |
| v8 | Pull to refresh, pinch to zoom, drag to reorder params |
| v9 | Timeline haptic markers |
| v10 | Doodles |
| v11 | Trigger interaction params |
| v12 | Motion tokens |

Important rule: `INSERT OR REPLACE` is only used for brand-new rows. Updating an existing row that has cascade children must use a real `@Update`, or it silently deletes its children.

## 13. Tests

- **257 unit tests** (JVM): physics solvers, spec, lint, generators, Lottie structure, fitter, tracker, codecs, live sync server, token exporter, doodle geometry and SVG.
- **57 instrumented tests** (on a real emulator): every migration step, cascade deletes, pin and undo flows, haptic markers, all 12 interaction screens opening, doodle drawing flow, trigger interactions, token saving, draw-based capture, and the **video pipeline decoding a real encoded mp4 of a known spring**.

## 14. Known limits

- Generated SwiftUI, Flutter, React Native and web code is deterministic template output checked for content, but has not been compiled with those toolchains here.
- The Figma plugin has not been run inside Figma.
- Video matching follows one solid-coloured object; it is not general tracking.
- Live sync is gated by a 4-digit pairing code (5 wrong tries lock it for 30 s and change the code), but traffic is plain HTTP on the local network: it stops casual snooping, not someone capturing traffic. A shared link works on the same Wi-Fi only; some guest/café networks block device-to-device traffic.
- Gesture exports are an honest representation of the release/settle spring; wiring it to a platform's own gesture system is left to the developer.
- Not built: a .motionlab that carries screenshots or tokens.
- Token drift compares pasted code against its own export stamp; a formatter that rewrites lines counts as an edit, and JSON exports (Lottie, spec) can't carry a stamp.
- The predictive back editor's commit rule (a third of the way, or a flick) stands in for the system's; on a device the OS decides when a back swipe commits, which is why commit isn't a setting.
- Screenshot images of deleted experiments stay on disk (the row goes, the file doesn't), so Undo can bring them back; nothing cleans them up yet.

## 15. Phase 10 (schema v13)

- **Compare** (Home): race two springs over one shared curve with a Blend slider, or play a blind round (guess which preset is A; best streak kept).
- **Sync v2**: pairing code + session tokens, `/pair`, `/exports`, and a browser bridge page served by the phone at `/` (all platforms' code, live, copy/download); "Copy bridge link" gives a pre-paired link (30 min), and "Show QR code" shows it as a QR (own encoder, no dependency; verified to scan with an independent decoder). The Figma plugin asks for the code.
- **Motion grade** (A-F, from the lint) and a **spring fingerprint** glyph on Home; **haptic-only export** (Android VibrationEffect, CoreHaptics Swift, .ahap).
- **Sketch it** capture: drag across a fake screen; timed points fit through the same `SpringFit`. **Two-object** video tracking gives each spring and the delay between them, and can create a staggered list.
- **Screenshot preview**: the shared Stage paints your own app screenshot behind the object (own table, so stale editor saves can't clobber it).
- **Chained springs**: a transition can `follows` another (start when it reaches X%, plus lag); resolved to plain delays for every exporter; Lottie now bakes every transition.
- **Feel sets**: built-in archetypes and your own bundles; add to tokens or copy as code. **Drift check**: code exports carry a signature stamp; paste code back to see if it was edited or is out of date.
- **Timeline video**: portrait 720x1280 H.264 MP4 with haptic ticks lit as they fire, shared from the timeline's Export section.
- v13 migration: `token_sets`, `token_set_items`, `experiment_backdrops`, and five nullable `chain*` columns on `experiments`.

## 16. Motion in the app's own UI

Screens slide and fade a little when opened or left (spring-driven, a tenth of the width), Home rows ease in when added and out when deleted (the real delete runs after the exit, so Undo and the data change together), and buttons, chips and links press in a hair. Everything checks `LocalReduceMotion`, which follows the phone's "remove animations" setting and turns all of it into plain cuts. The tuning demos are exempt: moving is their job. The reduce-motion flag is read once at launch.

## 17. Android-first polish (schema v14)

- **Editor frame:** the stage and a one-line readout (settle time, overshoot, nearest Material 3 spring) stay pinned while the controls scroll. On screens 720dp and wider (tablets, unfolded foldables, landscape) the stage and controls sit side by side. Other screens keep a readable 680dp column, centred. Rotating or unfolding no longer resets to Home.
- **Material 3 springs:** chips for the six spatial springs, and "M3 Standard default" / "Near M3 ..." / "Off the M3 scale" in the readout (distance on log stiffness plus damping ratio).
- **Predictive back:** the app opts in (`enableOnBackInvokedCallback`); a held back swipe shrinks and leans the current screen, and letting go goes back or springs into place. A new Predictive back interaction tunes that motion and exports a `PredictiveBackHandler` composable (compile-checked like the other Compose exports). v14 adds two nullable columns, `backShrinkT` and `backShiftT`.
- **Compare from any editor:** opens Race with this spring in lane A and the nearest Material spring in lane B; Back returns to the editor.
- **Haptics support note:** each haptic preset says whether this phone plays it as designed, as a plainer buzz, can't tell (before Android 11), or has no motor.
- **Undo on delete** restores the experiment's screenshot too.
- Tests: 273 unit, 65 on-device (API 34 emulator). The on-device suite doesn't run on the Android 16 image with the current test libraries (`InputManager.getInstance` was removed); that's the test tooling, not the app.

