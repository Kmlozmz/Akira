# Third-party source attribution

## Musicfy Home

Source: https://github.com/realidkroo/musicfy
Revision: bb28993b9f7abbac984d3eed8a8f4b62af83f256
License: GNU General Public License version 3; full text in LICENSE.
Copyright remains with the original contributors. The repository did not include individual copyright headers in these files.

Adapted for Akira on 2026-10-14: HeroCarousel.kt, HomeCards.kt, ScrollAwayCover.kt,
BlurEffectCache.kt, AgslShader.kt, TouchFeedback.kt, Press3D.kt, PlayingIndicator.kt,
RevealOnAppear.kt and the DownscaledBlur / DamageBoundary portions of GlassKit.kt,
plus referenced vector resources and gacha audio.
GlassKit.kt GlassState/glassRoot/GlassPillBackground/ProgressiveGlassBackground and
the HomeScreen header overlay are retained with their original masks, three steps,
50px maximum blur and scrim stops; Akira supplies semantic light/dark foundations.
Navigation geometry and header transition adapted from AppNavigation.kt and HomeScreen.kt.
Additional adaptations: ContainerTransform.kt, SharedElementTransition.kt, SearchGlassTopBar
and SearchField from SearchDesign.kt, BottomSheet.kt spring/commit constants, MainActivity navigation and SubSettingsScaffold header motion;
SwipeActions.kt, QueueScreen reorder state/row/handle behavior and their vector resources.
TopToast.kt pill geometry, typography, undo affordance and motion; Akira bottom placement,
existing backdrop blur, local artwork and accessible timed Snackbar actions retained.
Akira local swipe callbacks replace library/network writes; queue edits keep entry identity.
Simple blur is the default; BitChord refraction is an opt-in preference.

Calvin Reorderable 3.0.0 (the same dependency used by Musicfy):
https://github.com/Calvin-LL/Reorderable — Apache-2.0, Copyright Calvin Liang.
Used for lazy-list drag handles, placement animation and edge autoscroll.

Changes: local Song/Room data and playback callbacks replace Musicfy platform/network models;
Coil 2 replaces Coil 3; Akira branding/typeface; semantic light/dark colors; reduced motion;
existing Akira player and library retained. Original wipe, reel geometry, shaders and timings retained.

Distributions containing this adapted code must include the GPL license and provide the complete
corresponding source under its terms. Preserve this notice and upstream attribution with the source.

## BitChord floating dock

Source: https://github.com/kushagrasinghx/BitChord
Revision: fe95bb60aa9cd205cf4ab8bd25a5bec71a595b4f
License: GPL-3.0; full text in LICENSE. Copyright remains with its contributors.

Adapted for Akira on 2026-10-14: GlassNavBar/GlassNowPlaying integration, LiquidGlass,
MiniPlayer gesture arbitration, FloatingTabBar and GlassSelectionPill, and ic_player_next.
Changes: renamed packages; Akira Song/Coil 2/local transport and destinations; live system
reduced motion; measured mini/artwork anchors for Akira's existing expandable player;
shared backdrop capture; neutral older-Android fallback and theme-adaptive hairline.
The original fold/shared elements, lens physics, material shaders and gesture constants
are retained. No online playback, authentication or streaming services were imported.

### Source provenance retained by BitChord

- EchoMusicApp/Echo-Music (GPL-3.0): GlassNavBar structure and liquid glass integration.
  https://github.com/EchoMusicApp/Echo-Music
- FloatingTabBar v1.0.1, Elyes Mansour (Apache-2.0), including Echo/BitChord modifications.
  https://github.com/elyesmansour/compose-floating-tab-bar
- Kyant0/backdrop v2.0.0, Copyright 2025 Kyant0 (Apache-2.0), Android vendored sources
  including BitChord resolution scaling/capture changes. https://github.com/Kyant0/backdrop
- liquid_glass_easy 4.3.4, Copyright (c) 2025 Ahmed Gamil (MIT): selection lens motion
  ported into BitChord's GlassSelectionPill. https://github.com/AhmeedGamil/liquid_glass_easy

Apache-2.0 and the original package's MIT text are included under
app/src/main/assets/licenses/ and distributed inside the APK. Original source headers
are preserved in core/design/backdrop and feature/player/dock. GPL source distribution terms above apply.

## SpotiFLAC Mornye and scroll physics

- SpotiFLAC-Mobile, revision `5a8952659b247fd95f1914a56db60825657fe26d`,
  Copyright (c) 2026 zarzet, MIT.
  https://github.com/spotiflacapp/SpotiFLAC-Mobile
  Adapted menu placement, action anatomy, switch dimensions and continuous outline.
  Akira's shared presentation host and interruptible geometry replace Mornye's
  two-stage movement/material route. No downloader or remote-provider code imported.
- Flutter, Copyright 2014 The Flutter Authors, BSD-3-Clause.
  https://github.com/flutter/flutter/tree/stable/packages/flutter/lib/src
  Normal BouncingScrollPhysics friction, ballistic friction simulation and
  overdamped spring equations adapted to Compose in core/design/motion.
  Gesture arbitration and lifecycle are Akira integration code; this is not Apple's
  private UIScrollView implementation.
- liquid_glass_easy 5.0.0, Copyright (c) 2025 Ahmed Gamil, MIT.
  https://pub.dev/packages/liquid_glass_easy/versions/5.0.0
  Superellipse outline adapted to MornyeMenuShape, extended in Akira with animated
  curvature between the source control and expanded panel.
  The refraction renderer also ports the bounded Snell-refraction / edge-only, brightness-dependent
  dispersion equations to AGSL in MornyeLens.kt, using the optical parameters in
  SpotiFLAC's mornye_liquid_backdrop.dart at the revision above. The Compose
  capture, scale budget and material integration are Akira code, not Flutter/Impeller.
  The foreground is never passed through the lens.

Full licenses: `app/src/main/assets/licenses/SPOTIFLAC-MIT.txt`,
`FLUTTER-BSD-3-Clause.txt` and `LIQUID-GLASS-EASY-5-MIT.txt`.

## Player ambient backgrounds and motion artwork (2026-10-17)

- Musicfy `bb28993b9f7abbac984d3eed8a8f4b62af83f256`, GPL-3.0:
  `ui/player/customize/PlayerBackgroundStyles.kt` and `ui/theme/PlayerColorExtractor.kt`
  were studied for slow colour orbits and transitions between multi-colour palettes.
  https://github.com/realidkroo/musicfy
  Its `AppleMusicArtistBackgroundProvider.kt` was also studied on 2026-10-19
  for public artist editorial image/video keys. Akira reuses its existing silent
  Media3 artwork player, exact-name matching and bounded in-memory cache.
- BitChord `fe95bb60aa9cd205cf4ab8bd25a5bec71a595b4f`, GPL-3.0:
  `sharedUi/.../ArtworkPalette.kt`, `ArtworkMeshBackdrop.kt` and
  `app/.../data/canvas/AppleMusicCanvas.kt` were studied for neutral-preserving
  quantisation, cached decoding, strict catalogue matching and dynamic anonymous
  catalogue token refresh. Akira implements its own Android integration, lifecycle,
  video-only decoder, palette cache and ambient drawing.
  https://github.com/kushagrasinghx/BitChord
- AndroidX Media3 HLS, Apache-2.0, is used to play available motion-art streams.
  https://github.com/androidx/media

Motion artwork remains Apple's/its licensors' content; it is fetched on demand,
not bundled with Akira. No music streaming, user credentials or static catalogue
bearer tokens are included. Availability depends on the matched release/catalogue.

## YouTube Music online provider (2026-10-18)

Musicfy revision `bb28993b9f7abbac984d3eed8a8f4b62af83f256`, GPL-3.0,
https://github.com/realidkroo/musicfy . The `providers/innertube/src/main`
provider is included, with private-data debug logging removed and a tab bounds
check corrected. Akira adds its own encrypted device session store, Google WebView
sign-in host, separate source queues and Media3 integration.
NewPipeExtractor v0.26.5 (GPL-3.0), https://github.com/TeamNewPipe/NewPipeExtractor ,
is used by this provider to resolve available YouTube streams.
Ktor 3.4.0 and OkHttp 4.12.0 (Apache-2.0), Brotli dec 0.1.2 (MIT), and
Android desugar_jdk_libs 2.1.5 (GPL-2.0 with Classpath Exception) support it.
No credentials, cookies or music tracks are bundled. Availability depends on
YouTube Music, the connected account and region. Full GPL text is in
`app/src/main/assets/licenses/GPL-3.0.txt`.

## Reference details sheet and Inter

Details-sheet geometry, stacked metadata rows and typography adapted from
SpotiFLAC Mornye (`mornye_player_details_sheet.dart`, `mornye_metadata_row.dart`)
at the revision attributed above, Copyright (c) 2026 zarzet, MIT.
Akira uses its own Compose presentation host, bounded local metadata reader and playback models.

Inter Variable: Copyright (c) 2016 The Inter Project Authors
(https://github.com/rsms/inter), SIL Open Font License 1.1.
The unmodified font is bundled for the details sheet. The Eclipse entrance uses
wordmark outlines derived from its Semibold instance;
full license: `app/src/main/assets/licenses/Inter-OFL.txt`.

## Roboto Flex

Roboto Flex: Copyright 2017 The Roboto Flex Project Authors
(https://github.com/TypeNetwork/Roboto-Flex), SIL Open Font License 1.1.
Source: https://github.com/google/fonts/tree/main/ofl/robotoflex .
Akira bundles static instances for the application, setup and widgets at weights
400, 500, 600, 700, 800 and 900. Optical sizes are 16, 18, 22 and 28 for the
first four weights and 28 for the two heaviest weights; remaining axes use their
source defaults. Full license:
`app/src/main/assets/licenses/RobotoFlex-OFL.txt`.

## Source organization

Presentation adapters are organized under feature/home, feature/collection, feature/player and core/design. Backdrop sources are in app/src/main/kotlin/com/akira/app/core/design/backdrop. RefractionLens.kt retains the adapted optical surface; its Apache-2.0 license is included in licenses/backdrop/LICENSE-APACHE-2.0. BouncingScrollPhysics.kt contains the adapted scroll equations. Source filenames and packages have changed; the attribution and license terms above continue to apply.
