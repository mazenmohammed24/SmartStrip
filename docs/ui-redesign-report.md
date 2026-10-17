# SmartStrip Android — UI Redesign Implementation Report

Date: 2026-10-17 · Branch: `feature/modern-adaptive-ui` (isolated; `master` and `feature/android-portal-parity` untouched, nothing committed)
Companion document: `docs/ui-redesign-audit.md`

## 1. Summary

The Android client (100% Jetpack Compose, Material 3 Expressive, minSdk 26) received a system-level modernization on top of the visual language already present in the tree: a token-based design system (semantic colors, spacing/density, motion policy), optional Material You dynamic color, an adaptive navigation shell (floating dock on compact windows, navigation rail at ≥ 600 dp), reduced-motion support wired through every decorative animation, an expanded Appearance settings group, and dependency/quality cleanup. All existing features, routes, business logic and backend contracts are unchanged.

## 2. Screens and features migrated

- **Settings** (pilot of the new appearance system): group renamed to *Appearance*; added Dynamic color (Android 12+, hidden below), Density (Comfortable/Compact), Reduce motion; accent swatches and the custom picker now visibly disable dynamic color when chosen; the group now uses the spacing tokens.
- **All list hosts** (Home, Activity, Energy, Automations, Rooms/rule lists, Strip detail): item gaps now come from the density token, so Compact visibly fits more rows.
- **Everything that uses the kit** (Pill/LiveBadge/StatusDot/SegTabs/Shimmer/dialogs/dock/rail/splash/tiles) now follows the semantic palette and the reduced-motion policy centrally.
- Screens restyled in earlier iterations on this tree (Home, Energy, Automations, Activity, Rooms, rule forms, sheets, dialogs) were audited for parity and left functionally untouched.

## 3. Architecture and design-system changes

- `Theme.kt`: new `PowerkSemanticColors` (success/warning + containers) provided via `CompositionLocal`; new `PowerkSpacing`/`CompactSpacing`/`ComfortableSpacing` tokens; `LocalReducedMotion`; pure, testable policy `useDynamicScheme(requested, sdkInt)`; `PowerkTheme` now resolves dynamic-color schemes, reads the system animator scale (`ANIMATOR_DURATION_SCALE == 0` ⇒ reduced motion) and switches the M3 motion scheme to `standard` when motion is reduced.
- `Server.kt` (`AppTheme`): three new persisted preferences — `dynamicColor`, `reducedMotion`, `density` (same SharedPreferences, applied without restart).
- New `components/NavRail.kt`: vertical glass navigation for expanded windows with animated selected state, badges and press feedback; same destinations as the dock.
- `App.kt`: `PowerkNavigation` wrapped in `BoxWithConstraints`; a single `NavHost` instance in both layouts (only surrounding padding/decoration changes, so width changes never recreate screens); rail slides in at ≥ 600 dp and the dock hides; snackbar bottom offset adapts; page transitions collapse to short fades when motion is reduced; the splash sheen is skipped in reduced motion.
- Motion policy: `Modifier.tap` press scale, dock pill/press/lift/badge, `SegTabs` thumb, `ConfirmDialog`, `Shimmer`, `LiveBadge` pulse, tile `rememberPop`/`OrbitEdge` sweep and the splash sheen all stop or snap under reduced motion — no infinite animation keeps ticking when disabled.
- Business logic, repositories, ViewModels, networking, provisioning and settings sync were not modified.

## 4. Dependencies

- Removed (verified unused): `dev.chrisbanes.haze:haze-glass`, `haze-glass-material3` (glass materials) and `haze-blur-materials`.
- Added: `dev.chrisbanes.haze:haze-blur:2.0.1` — the dock/rail blur APIs were previously resolved transitively; they are now declared directly at the same version.
- No version upgrades, no new libraries beyond that.

## 5. Tests executed

`gradew testDebugUnitTest assembleDebug` (via `build-android-alpha.ps1`) — **BUILD SUCCESSFUL**:

| Suite | Tests | Failures | Errors |
|---|---|---|---|
| ApiTest | 3 | 0 | 0 |
| StringsTest | 4 | 0 | 0 |
| ThemeTest (new) | 8 | 0 | 0 |
| WifiHelperTest | 6 | 0 | 0 |

New `ThemeTest` locks: dynamic-color API gating (31+), accent parsing/fallback, dark-scheme accent lift, semantic colors staying distinct from error colors, semantic container contrast direction, density token mapping.

## 6. Build status

- `compileDebugKotlin`: clean.
- APK produced and copied: **`D:\FTP\Android\SmartStrip-1.0-alpha-20261017-131345.apk`** (28.25 MB).
- Known benign warnings unchanged: AGP vs compileSdk 37, `provision_step_4_body` resource merge note.

## 7. Performance observations

- No measurements were taken; no performance claims are made. Changes that plausibly help: guarded animations stop frame callbacks entirely in reduced motion; the width-driven shell reuses one NavHost; no new recomposition-heavy components; dependency set shrank.
- Unchanged risks: the dock blur is a `RenderEffect` (API 31+); below Android 12 Haze falls back to a tinted scrim — a platform limit, not a regression.

## 8. Known issues and unfinished items

- **Device verification pending**: no emulator/device was available in this environment; rail layout, dynamic color, density and reduced motion compile and pass unit tests but need a visual check on hardware (tablet/foldable for the rail).
- Density scope is list gaps (+ settings spacing); card paddings and min-heights are intentionally unchanged to keep the diff reviewable.
- Dynamic color is a device capability, so it is local-only and never synced to the server settings (the synced accent remains the fallback).
- On expanded windows content keeps the dock-clearing bottom padding used by compact screens — harmless extra scroll space, deliberate to avoid touching every screen.
- New settings strings are plain English like the rest of the hardcoded UI; the app has extensive hardcoded English screens that Arabic locale cannot translate — a pre-existing localization gap, out of scope here.
- Reduced motion also follows the system animator scale, but the value is read once per theme pass; changing it in Developer options while the app is open may need an app restart to apply.

## 9. Files requiring manual review

- `android/app/src/main/java/com/powerk/app/App.kt` — adaptive shell, transition gating.
- `android/app/src/main/java/com/powerk/app/Theme.kt` — tokens, dynamic color, reduced motion.
- `android/app/src/main/java/com/powerk/app/components/NavRail.kt` — new rail.
- `android/app/src/main/java/com/powerk/app/components/Kit.kt` — semantic pills/badges, motion gating.
- `android/app/src/main/java/com/powerk/app/screens/SettingsScreen.kt` — Appearance group.
- `android/app/build.gradle.kts` — Haze dependency swap.

## 10. Round 2 — splash and strip-AP scanning (user feedback)

Two pieces were rebuilt after device feedback; both replace the previous visuals entirely.

### Splash screen (`App.kt`)

- The glass disc, circle and small badge are gone. The brand bolt (same path geometry as the launcher icon foreground, now shared via `components/BrandMark.kt`) is drawn **large** — 58 % of the window's short edge, capped at 340 dp.
- Sequence: the bolt is **traced as a rounded stroke** (`PathMeasure` segment drawing), fills with the accent gradient, then zooms Netflix-style with an accelerating curve until its bounding box passes every screen edge; a same-accent full-screen layer seals the silhouette notches during the final 240 ms, so the hand-over to the dashboard is a clean crossfade from a full accent field (no circle, no sliver artifacts on any screen shape).
- Zoom factor is computed from the window size, so phones, tablets and landscape are all covered.
- Reduced motion: no trace, no zoom — the filled mark is shown statically for ~0.4 s, then the dashboard appears. The outer exit is a short 260 ms fade (the old scale-out was removed).
- Timing: trace 0–430 ms, fill 240–480 ms, zoom 520–1040 ms, seal 940–1180 ms, hand-over at ~1210 ms + 260 ms fade.

### Scan for strip APs (`screens/ProvisionWizard.kt`, `screens/ScanScreen.kt`)

- New shared `BrandScanRadar` (in `components/BrandMark.kt`): the app icon as a launcher-style tile (accent gradient, rounded square, white bolt) with radar rings pulsing outward and a soft breathing scale while a scan/connect/send is in flight — the scan animation is now the app icon itself.
- Wizard step 1: the wavy indeterminate line and the plain `OutlinedButton` rows are gone. The radar carries the live caption (scanning / connecting to a specific SSID / number of APs found), and found networks appear as animated cards that fade + expand in, with an animated selection highlight and a springy check icon instead of a "✓ " text prefix.
- Wizard step 2 (send/wait) now reuses the radar with the live provisioning status instead of a wavy progress line.
- Server scan tab (`ScanScreen`): the searching block now shows the same brand radar above the slim progress bar.
- Reduced motion: rings and breathing stop; captions and progress remain.

### Round 2 build

- `build-android-alpha.ps1` (tests + `assembleDebug`) — **BUILD SUCCESSFUL**, all 21 unit tests pass.
- APK produced and copied: **`D:\FTP\Android\SmartStrip-1.0-alpha-20261017-145618.apk`**.
- Still awaiting on-device verification (no emulator/device available here): splash pacing on real hardware, radar legibility in the provisioning sheet, and the zoom hand-over on a tablet/foldable.

## 11. Round 3 — functional fixes (user feedback)

Four device-found bugs, all fixed; no business logic or API changes beyond the noted additions.

- **Outlet rename Save was dead (and dialog saved the old name).** Root cause: `VTextField` (components/Kit.kt) only propagated edits on IME-done/blur, so the outer screen state never saw keystrokes and `Save` gating never flipped. `VTextField` now reports **every** keystroke live (commit-on-done still trims); all call sites are plain state setters, so this also fixes the same class of bug anywhere else.
- **Icon-picker search did nothing.** Same root cause: the query state only updated on commit, so the grid never filtered while typing. Fixed by the live `VTextField`.
- **Long-press strip name to rename.** `TilesStripCard` (dashboard), `StripCard`/`StripHead`/`StripRow` now open rename on long-press. A shared `RenameDialog` was extracted to `components/Kit.kt` and `StripDetailScreen` was migrated to it (its Save was also affected by the dead-Save bug).
- **New strip reported "not registered" although it appeared on the dashboard.** Registration detection (`Api.waitForRegistration`) now uses two signals: a diff of the server strip list against the baseline captured on home Wi-Fi before provisioning (catches self-registration), plus the server discovery scan + adopt path. When the deadline (120 s) passes without confirmation the result is reported as **pending**, not an error: wizard step 3 shows a tertiary "pending" card with primary **Done** and outlined **Retry** (plus local `retrySetup()`), and tells the user the strip will appear on the dashboard automatically. `ProvisionResult` gained `pending: Boolean`; `ProvisionViewModel` gained baseline capture (`captureBaselineStripIds`) back on home Wi-Fi.

### Round 3 build

- `build-android-alpha.ps1` (tests + `assembleDebug`) — **BUILD SUCCESSFUL**, all 21 unit tests pass.
- APK produced and copied: **`D:\FTP\Android\SmartStrip-1.0-alpha-20261017-151903.apk`**.

## 12. Round 4 — depth language and navigation-bar rebuild (user feedback)

Feedback: the bottom bar (indicator pill, badge, icon/label spacing) still read as template Material 3, and the app overall felt flat — no shadows, no calculated depth.

### Depth system (new)

- `AppElevation` ladder in `Theme.kt` — the single source of truth for height: `card` 3 dp, `cardRaised` 5 dp, `floating` 10 dp, `nav` 18 dp, `overlay` 22 dp.
- `Modifier.appShadow(shape, elevation)` in `components/Kit.kt`: theme-tinted drop shadow (light themes: `onSurface` at 62 % so shadows read warm, not black; dark themes: black at 88 %) — every lifted surface now shares one lighting.

### Bottom dock rebuilt (`components/Dock.kt`)

- **Selected indicator**: the solid full-height accent pill is gone. Selection is now a soft accent capsule (gradient tint 26 %→13 %, hairline accent border, its own 3 dp micro-shadow) that slides between items — a lit tile inside the glass instead of a paint bar.
- **Icon/label spacing**: tight stack — 21 dp icon, 3 dp gap, explicit 11 sp line height (no font leading), label Bold when selected.
- **Badge**: new shared `NavBadge` — a 16 dp ringed count pill (error fill, surface ring so it floats above the glass, "9+" overflow) replacing the bare red circle; pops in with a spring.
- **Layered glass**: two stacked shadows (3 dp contact + 18 dp ambient), top-lit gradient border (white 70 %→16 % in light), upper sheen wash, frosted blur unchanged. Press feedback softened to 0.92 scale, selection lift 1.06 — all skipped under reduced motion.
- `NavRail` (≥600 dp windows) now matches the dock exactly: same capsule, same `NavBadge`, same elevation.

### Depth applied across the app

- Cards: `VCard` (every screen), `TilesStripCard` (dashboard strip cards) and Settings `Group` containers now rest at card level; dialogs raised to overlay level.
- Actions: `SaveButton` and `GreenFab` cast accent-tinted light (matching the header action button); the segmented-control thumb and dashboard layout-toggle thumb got micro-shadows so controls feel physical.
- Sheets: designed 36×4 dp drag handle; page background gained a soft "floor" vignette so lifted cards sit on a ground plane.
- Inline controls (fields, chips, option rows, tiles inside cards) deliberately stay flat — the language is layered, not shadow-everything.

### Round 4 build

- `build-android-alpha.ps1` (tests + `assembleDebug`) — **BUILD SUCCESSFUL**, all 21 unit tests pass.
- APK produced and copied: **`D:\FTP\Android\SmartStrip-1.0-alpha-20261017-153557.apk`**.
- Awaiting on-device check: dock capsule contrast in light/dark and with dynamic color, badge legibility, shadow weight on bright pages, rail on tablet.

## 13. Round 5 — field fixes, selection fill and pull-to-refresh (user feedback)

### Fixes from on-device screenshots

- **"Lighter thing" inside the selected dock capsule and segmented tabs** — the round-4 indicators were translucent accent gradients drawn over glass, so the blur, sheen and shadow silhouettes showed through as a shifting lighter blob. New shared `capsuleFill(strong)` in `components/Kit.kt` composites the accent onto an opaque container colour; the dock capsule, `NavRail` selection, `SegTabs` thumb and the new mini outlet pills all use it. Border and micro-shadow are unchanged — only the bleed-through is gone.
- **Alerts: time and delete icon overlapped** — the swipe-to-delete layer (error container + trash glyph) was ghosting through the alert card because `VCard`'s gradient background was 94/86 % opaque. `VCard` is now fully opaque; the trash is only visible while actually swiping.
- **Newly provisioned strip showed offline / no Wi-Fi / dead outlet taps** — the server's `connectivity` flag can lag right after registration, and a `strip-connectivity` event missed during the phone's Wi-Fi switch was never re-delivered. Now any live telemetry (`power-tick` / `relay-state`) marks the strip online in `StripsViewModel`, the wizard's **Done** refreshes the dashboard, and `WifiBars` shows a connected Wi-Fi glyph instead of four dead bars while signal strength is still unknown.
- **Done did not close the Add strip sheet** — `ProvisionWizardPanel` takes an `onDone` callback; leaving the wizard (success, pending or failure) closes the whole modal and triggers a full refresh.
- **Text fields** — focusing a name/number field now selects the whole value (`VTextField`, `VTextInput`, `VNumberField`), so renaming never starts with a manual select-all.
- **Strips view** — collapsed strip rows (`StripRow`) keep their outlets controllable: one compact pill per outlet (icon glyph, accent tint when on, tap toggles, long-press opens the full outlet sheet) without expanding the card.

### Pull-to-refresh

- New shared `Refreshable` shell in `components/Kit.kt` (Material 3 `PullToRefreshBox`) and a `refreshing` flag on `StripsViewModel`, so the indicator follows the actual in-flight full sync.
- Wired on every manually refreshable page: Home (strip list), Activity, Energy (reloads the visible strip + range report), Schedules, Automation hub, Rooms and strip detail.

### Round 5 build

- `build-android-alpha.ps1` (tests + `assembleDebug`) — **BUILD SUCCESSFUL**, all 21 unit tests pass.
- APK produced and copied: **`D:\FTP\Android\SmartStrip-1.0-alpha-20261017-162557.apk`**.
