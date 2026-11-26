# Evidencia y lista de publicación — Akira 1.3.0

Base pública anterior: `v1.2.0` (`bbe46e5` / `ade8c39`, `versionCode=22`).
Rama actual de integración: `codex/major-upgrade`.

## Evidencia por sección del changelog

| Sección | Evidencia principal (commits y archivos) |
| --- | --- |
| Inicio (carrusel, variedad, contraste claro) | `57aa037`, `b063728`, `e90a823` (`LocalHomeFeed.kt`, `OnlineScreen.kt`, `OnlineHomeContent.kt`, `HomeHeroSuggestions.kt`, `HeroCarousel.kt`) |
| Escucha en línea (recomendaciones y recuperación) | `ce857aa` (`OnlineRecommendations.kt`, `OnlineRepository.kt`, `OnlineDataSource.kt`, `StreamRequestCache.kt`, `MusicLibraryService.kt`) |
| Aleatorio y cola (piscina completa y ruleta) | `bfa1421`, `ddeb41f` (`QueueOrder.kt`, `QueueTransactionPlayer.kt`, `MusicLibraryService.kt`, `MusicServiceConnection.kt`, `HomeNavigation.kt`, `ShuffleStartRegressionTest.kt`) |
| Búsqueda (filtros y coincidencias locales) | `bfa1421` (`LocalSearchIndexTest.kt`, `SearchScreen`, `OnlineScreen`) |
| Reproductor (fondo fluido reactivo a beat/animated art, reflejo unificado, posición de metadatos, slider recto y contraste) | `player_ambient.agsl`, `AmbientArtworkShader.kt`, `PlayerDynamicBackdrop.kt`, `PlayerArtworkGlow.kt`, `LiveArtworkSampler.kt`, `MotionArtworkVideo.kt`, `LivePlayerArtworkReflection.kt`, `NowPlayingExpandedScreen.kt`, `AkiraSlider.kt`, `PlayerArtworkAtmosphere.kt`, `PlayerFluidBackdropTest.kt` |
| Classic y navegación (dock, menús y Apariencia integrada) | `e90a823`, `f613ad2`, `33391ed` (`ClassicDockSurface.kt`, `HomeNavigation.kt`, `AkiraContextMenu.kt`, `NavigationBackOverlay.kt`, `PageNavigation.kt`, `AppearanceDestination.kt`, `AppearanceSession.kt`, `SettingsScreen.kt`, `ui_bridge/flutter/`) |
| Rendimiento (scroll Glass, carga diferida en Apariencia, minirreproductor, cursor de cola) | `7315732`, `9b5a898` (`GlassEffectReuseTest.kt`, `GlassNowPlaying.kt`, `SourceQueueStore.kt`, `PlaybackPersistenceRegressionTest.kt`, `MusicServiceConnection.kt`, `AppearanceSession.kt`, `main.dart`) |

## Estado del proceso de release (según `docs/development.md` y `AGENTS.md`)

- **Estado**: borrador de changelog preparado para revisión (`version.properties` sigue en `1.2.0` / `22`; no se ha creado tag ni publicado GitHub Release).
- **Pendiente tras aprobación del propietario**:
  1. Confirmar los commits en `codex/major-upgrade` (y sincronizar con la rama de publicación que corresponda).
  2. Incrementar `version.properties` a `versionName=1.3.0` y `versionCode=23`, y actualizar el badge en `README.md`.
  3. Ejecutar `.\gradlew.bat :app:prepareGitHubRelease` (o `.maintenance\gradle.cmd :app:prepareGitHubRelease`) para empaquetar `app/build/distributions/github/1.3.0/Akira-1.3.0.apk` firmado con la identidad de producción.
  4. Con autorización explícita, crear el tag `v1.3.0` y publicar la GitHub Release usando `release-notes/1.3.0/github.md`.
