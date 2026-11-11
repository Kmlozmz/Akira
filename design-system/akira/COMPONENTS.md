# Akira component system

Akira uses controlled components: a screen supplies state, content and
callbacks. It does not recreate the control's optics, press physics or geometry.
Classic and Glass are variants of the same public component families.

## Ownership

`components.json` is the common parameter contract. `generate_component_specs.py` produces
typed Kotlin and Dart adapters and the packaged contract. Its `--check` mode fails if an
adapter is stale. Edit the contract to change a shared metric or response, rather than
copying another control into a screen.

The native and Flutter renderers remain adapters for their respective UI frameworks.
They share the optical source/recipes, contact-light source/profile, dimensions, motion
parameters and thumb-dynamics equations. They do not create a Flutter engine per native
control. The optical thumb shader owns its padded alpha/rim; other surfaces keep their
existing clipping. Native backdrop coordinates already include the control transform; the fallback
inverse is applied only when that coordinate mapping is unavailable.

## Components

| Component | What it does | Use it for |
| --- | --- | --- |
| `AkiraSwitch` | Controlled on/off input. Classic uses a Material switch with check/X; Glass uses a measured optical thumb and the shared interaction dynamics. | Settings, setup and binary preferences. |
| `AkiraSlider` | Controlled scalar/range input with keyboard, accessibility, haptics and active-state callbacks. Its active-thumb variant shares the same measured lens as Appearance. | Sound values, opacity, diffusion and other adjustable values. Drag tracking is direct; springs settle released/external values. |
| `AkiraSizeSlider` | Retained discrete-size adapter for source compatibility. | Internal fixtures only; Font & Size is not exposed in the app. |
| `AkiraBackButton` | Shared back face, centered glyph, complete-surface contact response and light. `PageBackButton` delegates to it for existing native callers. | Navigation headers. |
| `AkiraIconButton` | Circular or surface-free icon action using the shared contact response; optional selection feedback is an explicit variant. | Compact toolbar actions. |
| `PlayerTrackActions` | Shared player/lyrics action geometry with a soft flat face, stable press appearance and a white favorite circle with an open star. More transfers its entire face into the optical menu. | Expanded-player and lyric-header Favorite/More. |
| `SettingsPageHeader` / `ReviewScaffold` | Fixed centered title above a progressive content veil. Classic shows a colored chevron and parent label; Glass uses the circular back. Settings navigation has no haptics. | Native Settings pages and the Flutter Appearance flow. |
| `AkiraContextHeader` | Identifies the subject with artwork/icon, title and subtitle. | Artist and track action menus. |
| Library overflow | New local playlist and library refresh; online refresh uses its current account scope. | Library overview; profile remains separate. |
| `ArtistOptionsMenu` | One artist-menu layout with explicit provider-only capabilities. | Online and local artist pages; radio appears only when supported. |
| `AkiraChoiceRow` | Labeled choice with description, optional selected fill and check. | Listening modes and selectable options in panels. |
| `AkiraSegmentedControl` | Mutually exclusive compact choices with one moving indicator. | Small sets of modes or filters. |
| `AkiraMaterialSurface` | Resolves the current surface style and semantic role, protecting foreground readability. | Floating control and panel containers. |
| `AkiraContextMenu` | Anchored grouped actions or choices, with optional title/descriptions and one presentation host. | Sorting, filters, collection and overflow menus. |
| `AkiraModalSheet` | Modal panel with semantic variants and the shared presentation/dismissal lifecycle. | Account, output, quality, confirmation and related panels. |
| `AkiraPageSheet` | Page-sized or immersive modal presentation, retaining its page dismissal behavior. | Album, artist and full detail pages. |
| `AkiraNameDialog` | Shared name-entry panel rather than a separate screen-specific dialog. | Saving or renaming a profile or collection. |
| `FloatingTabBar` | One four-tab capsule when expanded; current destination, mini-player and Search when compact. Its selection lens samples the host glass once. | Main application navigation. |
| `AkiraAccountButton` / `AkiraAccountAvatar` | Shared account trigger and avatar/fallback presentation. | Home, Library and account entry points. |

Existing native `AkiraPlayerSlider` calls delegate to `AkiraSlider`. Transport bars retain
their explicit thumb-less variant; changing the appearance of a settings slider does not
silently redesign the expanded player's seek/volume bars. `AkiraSteppedSlider` is retained
as an unused compatibility helper and is not a separate active settings renderer.

## Shared behavior and material resources

| Resource | Responsibility |
| --- | --- |
| `AkiraContactResponse` / `rememberAkiraPressResponse` | Whole-control affine contact response, pointer tracking, press/release and illumination. |
| `AkiraThumbLens` | Measures the actual visible thumb before capturing/refracting the background. Used by native switches and active-thumb sliders. |
| `AkiraThumbLensMaterial` | One held-lens shader with the switch or slider recipe; no generic navigation material substituted for a control lens. |
| `AkiraThumbDynamics` | Analytic spring plus acceleration filtering, compared against the actual Appearance implementation at 120 samples/second. |
| `SharedGlassMaterial` | Common optical shader and role recipes for controls, menus and panels. The travelling dock lens uses the production Snell renderer instead of a generic control recipe. |
| `GlassSelectionPill` | Production selection optics, captured page/bar/tab-row composition and original pill physics. The nav composite supplies its own highlights without a second color-dodge filter. |
| `AkiraPresentationHost` / `FontPresentationActor` / `FontForegroundEffect` | Anchor ownership, opening/return, submenu retargeting, content handoff and modal lifecycle. |
| `AkiraMenuContact` / `AkiraMenuContactSurface` | One drag-follow action highlight, recovered selector contact-light profile, release-to-choose, outside cancellation and edge scrolling. Accessibility clicks remain on each action. |
| `AkiraMenuSource` | Transfers foreground ownership to the presentation until return completes; restores the original trigger once. |
| `PageNavigationHost` | Navigation-stack push/pop transitions and Android system back. Custom in-page swipe-back is disabled; modals retain their own dismissal semantics. |

## Contract groups

- `AkiraNavigationSpec`: back face, hit target, glyph and header metrics.
- `AkiraSwitchSpec`: track/thumb dimensions, travel, held size, track reveal and inactive colors.
- `AkiraSliderSpec`: track/thumb dimensions, held growth and inactive colors.
- `AkiraClassicControlSpec`: Classic state colors, check/X size and contrast threshold.
- `AkiraContactSpec`: button/pane resistance and spring parameters, contact light intensity/reach.
- `AkiraControlMotionSpec`: contact, release and position tracking responses.
- `AkiraThumbMotionSpec`: acceleration filter limits and analytic deformation parameters.
- `AkiraMenuSpec` / `AkiraPanelSpec`: menu rows and semantic container metrics.
- `AkiraDockSpec`: fixed Akira red accent and production 9 dp lens lift. Generic lens shader compatibility values remain serialized; the dock uses production refraction/dispersion.
- `AkiraPageMotionSpec`: background travel for the normal page transition.

The dock accent is always Akira red, independent of the ordinary application palette.
The expanded dock groups four destinations in one capsule; compact mode retains current
destination, mini-player and Search. The travelling lens uses the exact production
refraction/readability shader sources, source-layer composition, elastic outline and
motion equations. Its 9 dp lift matches production. Foreground tabs are recorded outside
the lens and sampled by the lens; the lens is never recorded into its own input. Ordinary
controls keep the shared contact-light filter; this optical composite uses its own
production rim/highlights instead of an additional color-dodge pass.

## Interaction rules

- A consumer changes a controlled value through its callback; it does not store another copy.
- A disabled control cannot write a value. Classic state indicators remain check/X.
- Glass thumb growth changes the lens's measured shape/capture footprint, rather than
  stretching a small captured texture.
- Contact lighting includes the control content and is disabled when idle; it is not a
  static bright spot painted into every back button.
- Custom in-page interactive swipe-back is disabled in both application adapters. Normal
  Android system back and explicit back buttons remain. The recovered laboratory gesture
  remains opt-in source for future work and is not installed in application routes.
- A morphing trigger has one foreground owner. Its original view stays hidden through
  the complete return, while the overlay carries the moving glyph/content. It reappears
  only when the overlay releases ownership; explicit inline anchors can opt out.
  Icon-button triggers capture their actual size, tint and glyph rather than substituting
  a generic fixed ellipsis beside the returning menu.
- Outside dismissal closes an entire menu chain in one return to its original trigger.
  An explicit editor back can still return to the parent. The native shell uses the
  approved font selector's live actor, ordered springs, optical body/seed conversion,
  foreground viewport, blur and warp. Closing retargets the current springs; it does not
  replay a uniformly shrinking rectangle or reverse the opening recording's idle tail.
- Foreground adapters pass physical fragment coordinates. The original `uFrag.z` owns
  the single conversion to logical pixels; multi-density marker tests enforce size/position.
- Shared optical menu/panel shaders own their alpha contour and shadow. A second polygon
  mask does not trim their rim. Foreground viewport clipping is separate from the material.
- Scalar slider positions follow the finger directly during a drag. Contact/deformation
  springs remain active; haptics are spaced at five-percent bands, at least 45ms apart.
- Appearance restores only glass opacity (11%), diffusion (61%) and dock diffusion (on).
  Theme, palette, typeface and music preferences are preserved.
- Contact-filter uniforms are initialized before each immutable RenderEffect snapshot;
  compilation/profile data are retained while idle controls have no contact filter.
- Insets and safe-area layout belong to the screen. Component material/interaction belong
  to the component. Explicit variants are preferable to copying a component implementation.

## Verification boundary

Host checks cover generated-adapter consistency, light/dark layouts, original contact-light
pixels, actual scene transmission, cross-adapter thumb dynamics, native return trajectories,
keyboard/state callbacks, one-tap menu-chain dismissal and disabled custom swipe-back. They do not establish
physical-device pixel parity, touch latency or a 120 fps frame rate. The evaluation package
remains isolated until owner acceptance; production main/test are not replaced.

Control-sourced presentations keep a visible shell until their first return to the exact source bounds. Their optical recipe blends back to the source control during the final segment; original font-selector actor scheduling is unchanged. The foreground shader is prepared once per host off the UI thread. Library category glyphs share a heavier rounded outline and a full-opacity vivid red; their text alignment stays fixed.

The Details page uses the same readable Panel material, preserving its page detents. Native control returns preserve the source capsule geometry and retain their content through the contraction; the default font selector remains the original reference. Player ambient colors are grouped by hue and drawn in one AGSL pass where available. Visible editorial artwork animation is independent of audio pause and still respects visibility and Reduce Motion.
