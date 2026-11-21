# Unslop Design Log — `com.osuchat.app`

Credit: Adapted from `unslop` by Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), licensed under CC BY 4.0.

---

## Design Direction

- **Product:** Android Bancho IRC chat, multiplayer tournament referee HUD, beatmap explorer, and PP/stats companion for osu! players.
- **Character:** Instrument-grade, tactile, exact.
- **World:** Late-night rhythm-game HUD and audio studio hardware — matte obsidian surfaces, 0.5dp hairlines, tabular telemetry, and beatmap artwork/charts carrying the visual richness.
- **Moments:**
  1. Beatmap cover headers, Hit Object Density & Diffspike curve (`BeatmapDiffspikeGraphCard`), and Rank/PP History telemetry chart (`ProfileHistoryChart`).
  2. Live Tournament Series Scoreboard & Match Analyzer red-vs-blue comparison bars (`MpLobbyBoard`, `MatchAnalyzerScreen`), plus Player Profile hero & Medals shelf (`PlayerProfileContent`).
- **Signature Detail:** One crisp `3.dp` vertical left accent bar (`OsuPink` or semantic team accent) marking active selection/standing (Two-Pane selected chat row, pinned friend ranking standing, active Match MVP / team rows) paired with hairline-framed stepped tonal surfaces and `tnum` readouts.

---

## Full-App Slop Audit (Phase 0 Baseline)

1. **Surface Ladder & AMOLED Breaks:**
   - `DarkColorScheme` collapsed `surfaceContainerLow` and `surfaceContainer` to the identical `#18181E`.
   - `OsuAmoledColorScheme` set `surface = #08080C` instead of pure `#000000` and used `#08080C / #101014 / #16161C` instead of `#09090C / #111116 / #18181F`.
   - `BeatmapStatsBottomSheet` used `containerColor = background` (vanishing into AMOLED black).
   - `ScoreCardShareDialog` and `MatchSetupWizardDialog` used `color = surface`.
   - 50+ inline `Color(0x...)` literals scattered across `SettingsScreen.kt`, `ChatBackgroundContainer.kt`, `BeatmapPreviewCard.kt`, `ScoreCard.kt`, `ChatScreen.kt`, `ExploreAndProfileSection.kt`, `HomeScreen.kt`, `MpLobbyBoard.kt`, `MatchAnalyzerScreen.kt`, `PlayerStatsBottomSheet.kt`, and `AvatarAndStatus.kt`.
   - Team Red/Blue hexes differed between `MpLobbyBoard` (`#D14B4B` / `#4788CC`) and `MatchAnalyzerScreen` (`#E05252` / `#4B9DF2`); Podium Gold/Silver/Bronze hexes differed between `HomeScreen` and `ExploreAndProfileSection`.
2. **Light-Mode Contrast Defects:**
   - `BeatmapPreviewCard` (`background.copy(0.78f)`) and `BeatmapStatsBottomSheet` (`background.copy(0.72f)`) overlaid white text over light-mode near-white scrims.
   - `OsuPink` (`#FF66AA`) was applied directly on light-mode text and buttons instead of `MaterialTheme.colorScheme.primary` (`OsuPinkDark` calibrated to `#C92E69` for >= 4.5:1 AA contrast).
3. **Accent Overload:**
   - `osuFilterChipBorder` and 30+ `FilterChip` sites painted `1.dp` pink borders around active chips.
   - `OsuScoreCard` painted three pink accents per row (stars/mods, PP, share icon).
   - `FriendsListSection` painted up to four pink accents per online row.
   - `ChatScreen` painted all normal channel sender names pink.
   - `MultiplayerHubSection` displayed three simultaneous pink CTA buttons.
   - `MedalsSectionCard` and `MatchAnalyzerScreen` placed pink borders around nested cards and micro-pills.
4. **Icons & Emojis:**
   - Mixed icon families: 236 `Icons.Default.*`, 31 `Icons.AutoMirrored.Filled.*`, 25 `Icons.Rounded.*`.
   - Sparkle icon `Icons.Default.AutoAwesome` on `For You` mode chip.
   - Globe emoji `\uD83C\uDF10` fallback in `AvatarAndStatus.countryCodeToFlagEmoji` and hardcoded in `ExploreAndProfileSection.CountryPickerBottomSheet`.
   - Unicode geometric symbols (`◎ ◉ ◆ ▤`) used for osu! game modes instead of vector drawables.
5. **Spacing & Radius Drift:**
   - Off-4dp values (`3, 5, 7, 9, 10, 11, 13, 14, 15, 18, 22, 26, 30.dp`) across all screens.
   - Bottom sheets and dialogs used `12.dp` (`OsuRadius.lg`) instead of `16.dp` (`OsuRadius.xl`).
   - Chips mixed `PillShape`, `6.dp`, and `8.dp`; chat bubbles used `8.dp` instead of `12.dp`.
6. **Card-in-Card Nesting:**
   - Bordered pills inside bordered cards (`BookmarksBottomSheet`, `HomeScreen`, `ChatScreen`), bordered `OsuScoreCard` inside `DailySessionSummaryCard`, bordered medal chips inside `MedalsSectionCard`, bordered diffspike canvas inside `FlatCard`, and 9 identical standalone `SettingsSectionCard` boxes in `SettingsScreen`.
7. **Typography & Copy:**
   - Negative letter spacing on `15sp–18sp` styles in `Type.kt`; 70+ `FontWeight.Bold` / `ExtraBold` usages; `FontStyle.Italic` on `/me` actions.
   - ALL CAPS labels (`MATCH MVP`, `LIVE`, `FINISHED`, `HOST`, `READY`, `WAIT`, `NO MAP`, `PLAY`, `OPEN`, `RANKED`, `SPOTLIGHT`).
   - Exclamation marks (`"Leading your friends list!"`, `"Reached!"`, `"You already exceed ...!"`), trailing periods on short labels/subtitles, and 4 stray Turkish strings (`"Bildirimler kapalı"`, `"Bildirimleri Aç"`, `"Bildirimleri Kapat"`, `"Sayı Yerine Sadece Nokta"`) in `HomeScreen` and `ChatScreen`.
8. **Scope Check:**
   - `AppUpdateDialog` / App Updates card does not exist in `com.osuchat.app`; all 12 existing dialogs and bottom sheets are covered.

---

## Token System

### Surface Ladder (`OsuSurfaceTokens` + `MaterialTheme.colorScheme`)

| Role | Dark | AMOLED | Light |
| --- | --- | --- | --- |
| Canvas / `background` | `#121216` | `#000000` | `#F5F6F8` |
| `surfaceContainerLowest` | `#121216` | `#000000` | `#FFFFFF` |
| `surfaceContainerLow` | `#17171D` | `#09090C` | `#F0F1F5` |
| `surfaceContainer` (sheets/dialogs) | `#1D1D24` | `#111116` | `#FFFFFF` |
| `surfaceContainerHigh` | `#24242D` | `#18181F` | `#E7E9EF` |
| `surfaceContainerHighest` | `#2C2C37` | `#20202A` | `#DCDFE8` |
| `outlineVariant` (0.5dp hairline) | `16% #EDEDF2` | `18% #EDEDF2` | `14% #18181E` |
| Primary text | `#EDEDF2` | `#EDEDF2` | `#18181E` |
| Secondary text | `#A1A1B0` | `#A1A1B0` | `#555563` |
| Muted text | `#787887` | `#787887` | `#6E6E7D` |

### Spacing, Radius, Typography & Motion Scales

- **Spacing (`OsuSpacing`):** `4 / 8 / 12 / 16 / 20 / 24.dp` (`2.dp` allowed strictly for fine indicator tracks and intra-group message spacing).
- **Radius (`OsuRadius`):** `4.dp` (`xs` micro-badges/mod pills), `8.dp` (`md` chips/inputs/buttons), `12.dp` (`lg` containers/cards/message bubbles), `16.dp` (`xl` bottom sheets/dialogs), `0.5.dp` (`hairline`), `3.dp` (`accentBar`).
- **Typography (`OsuTypography`):** Weights constrained to `Normal (400)`, `Medium (500)`, `SemiBold (600)`. Zero negative tracking below `20.sp`. `.tnum()` enforced on every numeric readout.
- **Motion (`OsuMotion`):** `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`, `150ms` (color/icon state), `200ms` (tabs/bars), `220ms` (expand/collapse).

---

## Per-Phase Implementation Log

All changes preserve existing ViewModels (`*ViewModel.kt`), navigation routes, click semantics, Room database schema (`OsuChatDatabase` `version = 9`), ProGuard rules, Android 15 edge-to-edge insets, adaptive Two-Pane landscape `CHATS` layout, `CHATS` `BadgedBox` dot, `BeatmapMiniPlayerBar` docking, `ChatBackgroundContainer` framing/blur guard, `ChatInlineImagePreview` external `ImageLoader`, and `ScoreCardRenderer`.

### Phase 0: Tokens, Vector Icons & Shared UI Primitives (`ui/theme/`, `ui/components/`, `res/drawable/`)
1. **Vector Mode Icons (`res/drawable/`):**
   - Created 4 tintable 16dp vector drawables replacing unicode geometric glyphs: [ic_mode_osu.xml](app/src/main/res/drawable/ic_mode_osu.xml), [ic_mode_taiko.xml](app/src/main/res/drawable/ic_mode_taiko.xml), [ic_mode_catch.xml](app/src/main/res/drawable/ic_mode_catch.xml), and [ic_mode_mania.xml](app/src/main/res/drawable/ic_mode_mania.xml).
2. **Design System Tokens (`ui/theme/`):**
   - `Color.kt`: Calibrated dual dark/AMOLED + Light surface palette (`Dark*`, `Amoled*`, `Light*`, 3-tier neutral text hierarchy), calibrated Light accent `OsuPinkDark` (`#C92E69`), status/mode/podium/grade/star/mod chart tokens (`TeamRed`, `TeamBlue`, `PodiumGold`, `PodiumSilver`, `PodiumBronze`, `ModNm`, `ModHd`, `ModHr`, `ModDt`), theme-invariant photographic scrim tokens (`MediaScrimTop`/`MediaScrimBottom`/`MediaScrimSolid`, `FullscreenBackdrop`), `WallpaperPresetColors`, and `WidgetPreviewColors`.
   - `Theme.kt`: Added `OsuSpacing` (`4/8/12/16/20/24.dp`), `OsuRadius` (`xs=4.dp`, `sm/md=8.dp`, `lg=12.dp`, `xl=16.dp`, `hairline=0.5.dp`, `accentBar=3.dp`), `OsuMotion` (`150–220ms` `CubicBezierEasing(0.22f, 1f, 0.36f, 1f)`), `OsuSurfaceTokens` + `LocalOsuSurfaces`, `osuHairlineBorder`, and corrected `DarkColorScheme`, `OsuAmoledColorScheme`, and `LightColorScheme`.
   - `Type.kt`: Rebuilt `OsuTypography` with `Normal`/`Medium`/`SemiBold` hierarchy, removed negative tracking below `20.sp`, and preserved `.tnum()` tabular figure helper.
3. **Shared Primitives (`OsuPrimitives.kt`) & Shared Components (`ui/components/`):**
   - `OsuPrimitives.kt`: Created `OsuHairlineDivider`, `OsuSectionHeader`, `OsuGroupedContainer`, `OsuAccentBarContainer`, `OsuFilterChip`, `OsuSegmentedBar`, `OsuStatCell`, `OsuMicroTag`, `OsuEmptyState`, `OsuInlineBanner`, and `OsuSheetTopBar`.
   - `AvatarAndStatus.kt`: Replaced globe emoji fallback in `countryCodeToFlagEmoji` with empty-string sentinel; upgraded `OsuModeBadge` to render `painterResource(visual.iconRes)`; animated `ConnectionStatusChip` via `OsuMotion.colorSpec()`.
   - `BeatmapMiniPlayerBar.kt`: Added `OsuHairlineDivider()`, `OsuMotion` animated 2dp progress track and bookmark tint, `OsuSpacing`, and `Icons.Rounded.*`.
   - `BeatmapPpSimulatorPanel.kt`, `BeatmapPreviewCard.kt` (including `UserProfilePreviewCard`), `BookmarksBottomSheet.kt`, `ChatBackgroundContainer.kt` (including `WallpaperCustomizerPanel`), `ProfileHistoryChart.kt`, `ScoreCard.kt` (with `embedded: Boolean = false`), `ScoreCardShareDialog.kt`: Unified `Icons.Rounded.*` icons, removed inline color hexes and nested borders, enforced `.tnum()` and theme-invariant media scrims, upgraded sheets/dialogs to `OsuRadius.xl` + `surfaceContainer`, and preserved all wallpaper framing/blur guards and PNG export behavior.

### Phase 1: Auth & Core Home Shell (`LoginScreen`, `HomeScreen`, `MultiplayerHubSection`)
- **`LoginScreen.kt`:** Unified `OsuSpacing`/`OsuRadius`, `Icons.Rounded.*`, `OsuInlineBanner`, `OsuGroupedContainer`, and `OsuSheetTopBar` (`IrcPasswordTutorialDialog`), balancing OAuth v2 primary CTA vs Bancho IRC onboarding without decorative fluff.
- **`HomeScreen.kt`:** Aligned top bar, filter strips (`OsuFilterChip`), `CHATS` `BadgedBox` red-dot anchor, Two-Pane `CHATS` left-pane selection bar (`OsuAccentBarContainer` with 3dp bar and 0.5dp vertical divider), `FriendsRankingLeaderboardSection` (`PodiumGold`/`Silver`/`Bronze`), `FriendScoreFeedSection`, `GlobalMessageSearchHitCard`, `ChannelListRow`, `NewChatOrChannelDialog`, `ChannelOptionsBottomSheet`, and docked `BeatmapMiniPlayerBar`. Fixed all stray Turkish notification strings to English (`Notifications muted`, `Unmute notifications`, `Mute notifications`, `Dot-only badge`) and enforced 4dp spacing across skeletons.
- **`MultiplayerHubSection.kt`:** Replaced multi-pink CTA overload with calm hierarchy (`OsuGroupedContainer`, `OsuFilterChip`, `OsuEmptyState`, `OsuMotion`), `OsuRadius.xl` create-lobby dialog, and `Icons.Rounded.*`.

### Phase 2: Live Chat & Tournament HUD (`ChatScreen`, `MpLobbyBoard`, `MatchSetupWizardDialog`, `MappoolManagerDialog`, `MatchAnalyzerScreen`)
- **`ChatScreen.kt`:** Applied 2dp intra-turn vs 12dp inter-turn bubble spacing, `LocalOsuSurfaces.current` outgoing/incoming bubble fills for contrast over custom wallpapers, 3dp left accent bar on reply quotes, non-italic `/me` formatting, neutral/role-aware sender colors, `OsuMicroTag` on `MpMatchPreviewCard`, `OsuRadius.xl` sheets/dialogs with `surfaceContainer`, `OsuHairlineDivider`, `Icons.Rounded.*`, English notification labels, and preserved `externalImageLoader` + IME insets.
- **`MpLobbyBoard.kt`:** Injected `MpTimerManager.activeTimers` via Hilt `@EntryPoint` (`MpTimerEntryPoint`) to render an `AnimatedVisibility` countdown strip (`MM:SS` `.tnum()`, progress bar, abort action) above tabs; unified `TeamRed`/`TeamBlue`; upgraded to `OsuSegmentedBar`, `OsuFilterChip`, `OsuMicroTag`, and `OsuAccentBarContainer` slot cells (`MpSlotCell`); upgraded dialogs to `OsuRadius.xl`.
- **`MatchSetupWizardDialog.kt` & `MappoolManagerDialog.kt`:** Unified `OsuRadius.xl` + `surfaceContainer`, `OsuFilterChip`, `OsuInlineBanner`, `OsuEmptyState`, `OsuHairlineDivider`, and `Icons.Rounded.*`.
- **`MatchAnalyzerScreen.kt`:** Refactored summary header (`MatchSummaryHeaderCard`), red-vs-blue comparison bars, `OsuAccentBarContainer` MVP callout, and tabular `PlayerStandingRow` columns (`Player`, `Avg / Acc`, `Cost`) with `.tnum()`.

### Phase 3: Profile, Beatmap Discovery & Settings (`PlayerStatsBottomSheet`, `ExploreAndProfileSection`, `SettingsScreen`)
- **`PlayerStatsBottomSheet.kt`:** Upgraded sheet/dialogs to `OsuRadius.xl` + `surfaceContainer`, theme-invariant photographic scrim (`mediaScrimTop`/`mediaScrimBottom`), 4-mode `OsuSegmentedBar`, `OsuGroupedContainer` + `OsuStatCell` matrix, embedded `OsuScoreCard(embedded = true)` inside `DailySessionSummaryCard`, `ModNm`/`ModHd`/`ModHr`/`ModDt` chart tokens in `TopPlayAnalyticsCard`, calm `MedalsSectionCard` unlocked/locked contrast, empty-safe country flags, `OsuMotion`, and `Icons.Rounded.*`.
- **`ExploreAndProfileSection.kt`:**
  - Removed `Icons.Default.AutoAwesome`, unicode mode symbols (`modeVis.symbol`), globe emoji `"\uD83C\uDF10"` (`CountryPickerBottomSheet`), `FontWeight.Bold`, direct `OsuPink`, and hardcoded podium/peak `Color(0x...)` literals.
  - Replaced Search/For You chips, ranking/calculator mode strips, and direction toggles with `OsuSegmentedBar`; replaced remaining filter/preset/tag chips with `OsuFilterChip` and unified `DropdownTriggerChip`; replaced `modeVis.symbol` with `painterResource(modeVis.iconRes)`.
  - Upgraded `BeatmapStatsBottomSheet` and `CountryPickerBottomSheet` to `OsuRadius.xl` + `surfaceContainer` and `PlayLaterTagEditorDialog` to `OsuRadius.xl`; added vertical photographic scrim on beatmap cover headers; eliminated inner border nesting in `BeatmapDiffspikeGraphCard` and `TargetRankRoadmapCard`; guarded `countryCodeToFlagEmoji` against empty strings; animated bookmark/preview/track icon states via `OsuMotion`; delegated `FlatCard`, `ExploreErrorCard`, and `ExploreEmptyCard` to `OsuPrimitives`.
- **`SettingsScreen.kt` & `Color.kt` (`WidgetPreviewColors`):**
  - Added complete `WidgetPreviewColors` tokens in `Color.kt` so `SettingsScreen.kt` contains zero inline `Color(0x...)` literals and zero direct `OsuPink`/`OsuPinkDark` references.
  - Consolidated 10 separate `SettingsSectionCard` blocks into 5 grouped tonal sections (`Account & connection`, `Appearance`, `Home-screen widget`, `Notifications & chat filters`, `Tournament & backup`) using external `OsuSectionHeader`, `OsuGroupedContainer`, `OsuHairlineDivider`, `OsuSegmentedBar`, and `OsuFilterChip`.
  - Upgraded `BackupRestoreConfirmationDialog` to `OsuRadius.xl` + `surfaceContainer` with borderless tonal summary container and `.tnum()` counts; replaced all `Icons.Default.*` with `Icons.Rounded.*` and tightened copy.

---

### Phase 4: Cross-App Audit & Final Verification

#### Scope Clarification
- `AppUpdateDialog` and an App Updates settings card do not exist in `com.osuchat.app`; all 12 existing dialogs and bottom sheets were audited and updated.

#### Automated UI Regex Audit (All 21 Non-ViewModel Files Under `ui/`, Excluding `ui/theme/`)

| Audit Check | Remaining Hits | Notes |
| --- | --- | --- |
| Hardcoded `Color(0x...)` | 0 | All widget, chart, and scrim colors live in `ui/theme/Color.kt` |
| Legacy `Icons.Default`/`Filled` or `AutoAwesome` | 0 | Unified on `Icons.Rounded.*` / `Icons.AutoMirrored.Rounded.*` |
| Emoji / `modeVis.symbol` / unicode glyphs | 0 | Replaced with vector icons and `ic_mode_*.xml` |
| `FontStyle.Italic` | 0 | Clean upright typography throughout |
| `FontWeight.Bold` / `ExtraBold` / `Black` | 0 | Maximum weight constrained to `FontWeight.SemiBold` |
| Stray Turkish UI strings | 0 | All strings standardized in English |
| Direct `OsuPink` outside `ui/theme/` | 0 | Preset `ObsidianAccent` and `WidgetPreviewColors` aliased in `Color.kt` |
| `ModalBottomSheet` / `AlertDialog` with `OsuRadius.lg` | 0 | All sheets and dialogs use `OsuRadius.xl` (`16.dp`) |
| Exclamation marks in copy (excl. `osu!`, `!mp`) | 0 genuine | 3 regex matches are Kotlin `!in` operator and IRC `"!"` prefix checks |
| Raw `HorizontalDivider` / `FilterChip` | 0 | Shared `OsuHairlineDivider` and `OsuFilterChip` used everywhere |
| Off-4dp padding / spacing outside hairline/2dp tracks | 0 | All layout spacing conforms to `OsuSpacing` |

#### Final 9-Pass Checklist
- **Spacing -> Color -> Icons -> Copy -> Type -> Layout -> Details -> Visuals -> Motion:** All 9 passes completed and verified across all 21 UI composables/screens, 3 theme files, and 4 new vector drawables.

#### Gradle Verification Gates

| Command | Outcome | Scope |
| --- | --- | --- |
| `.\gradlew.bat testDebugUnitTest -q --console=plain` | PASSED (exit code 0) | Full unit test suite after all Kotlin/XML edits |
| `.\gradlew.bat assembleDebug -q --console=plain` | PASSED (exit code 0) | Debug APK compilation & Compose resource packaging |
| `.\gradlew.bat assembleRelease -q --console=plain` | PASSED (exit code 0) | R8 release compilation & proguard verification |
