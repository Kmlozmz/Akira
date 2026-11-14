# Shared interface

Akira uses two UI adapters around one component contract. Kotlin/Compose renders the player, library, settings and other application screens. The Flutter module renders Appearance and its previews. Both adapters use the optical recipes and component dimensions documented in `design-system/akira/COMPONENTS.md`.

`flutter/lib/appearance` contains the product screen and its typed preference controller. `flutter/lib/design_system` contains shared Flutter controls; the retained renderer sources live under `flutter/lib/laboratory`. The local `vendor` packages pin those renderer dependencies and their shader assets. These directories contain runtime sources, not device captures or testing media.

`AppearanceActivity` connects the module to the existing Kotlin preference repository through the Pigeon contract in `flutter/pigeons`. Kotlin remains responsible for playback, the music library, accounts, permissions and persistent preferences. The interface module does not own a second application database or online session.

Classic and Glass share controlled inputs and header alignment. Classic retains the colored chevron with a parent-page label; Glass uses the shared circular back. Font & Size is not a product setting; the default interface font is Akira's Roboto Flex. Settings stored by older evaluations remain on disk for compatibility.

Generated Flutter platform projects, compiled AARs, test fixtures and local tools are excluded from version control. The Android project consumes the module's local release Maven output under the `com.akira.ui` group.

Run bridge generation from `ui_bridge/flutter` with the pinned Pigeon dependency:

```powershell
flutter pub get --enforce-lockfile
dart run pigeon --input pigeons/appearance.dart
flutter analyze --no-pub
flutter build aar --no-pub --no-debug --no-profile --build-number=1.0
```

The schema writes Kotlin to `app/src/main/kotlin/com/akira/app/feature/appearance/Appearance.g.kt` and Dart to `lib/bridge/appearance.g.dart` inside the module. Regenerate both files together and review their diff before rebuilding the AAR. The Android application consumes `com.akira.ui:flutter_release:1.0`; the local Maven output must exist before compiling the host. Keep all supported Android architectures in the normal AAR build.
