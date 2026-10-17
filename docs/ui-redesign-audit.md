# SmartStrip Android — UI Redesign Audit

Date: 2026-10-17 · Branch: `feature/modern-adaptive-ui` (isolated from `feature/android-portal-parity`)

## 1. Existing architecture summary

| Area | Current state |
|---|---|
| UI framework | 100% Jetpack Compose. No XML layouts (`res/layout` absent), no ViewBinding. |
| Toolchain | AGP 9.1.0 (built-in Kotlin plugin), Kotlin 2.4.10 (compose plugin), Gradle 9.3.1, Java/Kotlin 17. |
| SDKs | compileSdk 37, targetSdk 36, minSdk 26. |
| Compose | ui 1.12.1, material3 1.5.0-alpha16 (`MaterialExpressiveTheme`), icons-extended 1.7.8, navigation-compose 2.8.9, lifecycle 2.11.0. No Compose BOM. |
| Networking | OkHttp 4.12 (REST in `Api.kt`, WebSocket in `Realtime.kt`). Backend URL hardcoded (`Server.BASE_URL`). |
| State | `StripsViewModel` (single app-scoped store: strips, link, notifications, events, schedules, rules, settings, energy cache, timers) + `ProvisionViewModel` (Wi-Fi provisioning). No DI framework; manual singletons. |
| Persistence | SharedPreferences (`powerk`): theme mode, accent, language, home layout; `powerk_provision` for provisioning form. |
| UI effects | Haze 2.0.1 (blur) for the floating dock; custom component kit in `components/Kit.kt`. |
| Themes | `PowerkTheme` → `MaterialExpressiveTheme` with hand-built `powerkScheme(dark, accentHex)`; IBM Plex Sans/Mono; shapes 10–28 dp. Accent synced from server settings. |
| Localization | `values/` (EN) + `values-ar/` (full Arabic) with a parity unit test. Many screen strings are still hardcoded English (see gaps). |
| Tests | 4 JVM unit tests (Api wire format, outlet indexing, strings parity, Wi-Fi validation). No instrumentation tests. |

## 2. Screen & feature inventory

Top-level (bottom dock / navigation destinations):

| Route | Screen | Key features | Data |
|---|---|---|---|
| `home` | HomeScreen | total load card + threshold bar, All-off, 4 quick links, search, 3 strip layouts (rows/cards/tiles, persisted), add-strip sheet | StripsViewModel |
| `energy` | EnergyScreen | 7/30/90-day tabs, bar chart, tariff dialog (Egypt tiered / flat), per-outlet breakdown, reset | StripsViewModel energy cache |
| `automations` | AutomationsScreen (= SchedulesScreen) | schedule list, inline editor (strips/outlets, on/off, weekly/one-time, day chips, time + timezone pickers) | StripsViewModel |
| `activity` | ActivityScreen | alerts + log tabs, mark read/all, swipe delete, clear-all | StripsViewModel |
| `settings` | SettingsScreen | theme mode/accent/custom picker, language, time format, tariff/energy/currency/threshold, add strip, rooms, connection status, diagnostics, about | StripsViewModel + AppTheme/Lang |

Detail routes (pushed):

| Route | Screen | Notes |
|---|---|---|
| `menu/{mac}` | StripMenuScreen | per-strip tools hub |
| `strip/{mac}` | StripDetailScreen | power metrics, outlets, rename/delete, links |
| `schedules/{mac}` | SchedulesScreen (filtered) | same as automations |
| `rules` | AutomationHubScreen | 5 rule kinds with counts |
| `rule/{type}/{mac}` | RuleTypeScreen + editor | validation preserved |
| `rooms` | RoomsScreen | grouped by room, all on/off |
| `energy?strip=` | EnergyScreen (filtered) | same as energy tab |

Sheets/dialogs: AddStripSheet (scan/adopt + provisioning wizard), OutletSheet (details/icon/timer/history), StripSheet, ColorPickerDialog, ConfirmDialog, tariff choice, icon grid.

## 3. Proposed navigation structure

- Keep the existing 5 top-level destinations and route graph unchanged (works, back-stack + state restoration already correct).
- **Compact width (< 600 dp):** current floating frosted dock (bottom).
- **Medium/expanded width (≥ 600 dp):** persistent **navigation rail** at the start edge; content keeps full feature set. Rail shows the same destinations, badges included. Detail screens stay reachable and the rail remains visible (selected state follows the owning tab).
- No drawer: only 5 destinations, rail/dock cover them without hiding content.
- Layout switches by available window width (foldables, split-screen, resize) — not by device type.

## 4. Proposed design system

- **Tokens** (new, in `Theme.kt`):
  - `PowerkSemanticColors` (success / warning + containers) via `CompositionLocal`, independent of accent so status meaning never changes with the palette.
  - `PowerkSpacing` (list gap / section gap / screen padding) via `CompositionLocal`, driven by a compact/comfortable density preference.
  - `LocalReducedMotion` — set from an app preference or the system animator scale; gates decorative animation (shimmer, pulse, orbit, pop, spring thumbs) and switches the M3 motion scheme to `standard`.
- **Colors**: keep curated brand schemes; add **optional Material You dynamic color** (API 31+) with brand fallback. Accent presets stay; choosing an accent disables dynamic color.
- **Typography/shapes**: existing IBM Plex scale and 10–28 dp shapes are consistent — keep, no churn.
- **Components**: keep the existing kit (`VCard`, `Pill`, `SegTabs`, `ScreenHeader`, `SheetFrame`, …) and wire it to the new tokens; add `NavRail`. Avoid new abstractions that don't have multiple call sites.

## 5. Compatibility constraints

- minSdk 26: dynamic color must be API-gated (31+); blur falls back gracefully on < 12 (Haze).
- Edge-to-edge already enabled; all new surfaces must respect status/navigation/IME insets.
- RTL (Arabic) supported: rail/dock force LTR icon order (already true for dock) but text follows locale.
- Do not touch: `Api.kt`, `Realtime.kt`, `WifiHelper.kt`, `Diagnostics.kt` protocol/behavior; server sync contract (`saveSettings`, `applyRemote`).
- Unused-but-harmless deps (`haze-glass*`, `haze-blur-materials`) to be removed; `haze` + `haze-blur` kept.

## 6. Implementation sequence

1. Design tokens + dynamic color + semantic colors + reduced motion + density (`Theme.kt`, `Server.kt`).
2. Wire tokens into the kit (Pill/LiveBadge/Shimmer/tap/SegTabs/dialogs) and TilesCard/Dock/Splash animations.
3. Adaptive shell: width-class switch in `PowerkNavigation`, new `NavRail`.
4. Appearance settings (dynamic color, density, reduce motion) in Settings.
5. Apply density tokens to list hosts; dependency cleanup.
6. Unit tests for theme policy; compile, test, build, verify.

## 7. Known risks / acceptance criteria

Risks: NavHost state when width class changes (mitigated: single NavHost position, only padding/decoration changes); dynamic color contrast (semantic colors remain curated); oversized diffs (kept incremental).

Acceptance: existing features/routes unchanged; light/dark/dynamic all render; rail appears ≥ 600 dp with working selection + badges; reduce-motion setting stops decorative loops; density changes list spacing; build + unit tests green; APK produced.
