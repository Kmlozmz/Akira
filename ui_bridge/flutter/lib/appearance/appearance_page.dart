import 'dart:ui' as ui;

import '../design_system/component_specs.dart';

import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../laboratory/recovered/components.dart';
import '../laboratory/review/appearance_panels.dart';
import '../laboratory/review/live_glass.dart';
import '../laboratory/review/interactive_controls.dart';
import '../laboratory/review/laboratory.dart'
    show
        ReviewScaffold,
        reviewInsets,
        reviewPush,
        AppearanceSwitch,
        SelectionCircle,
        ReviewCapabilities;
import 'settings_controller.dart';

class AkiraAppearancePage extends StatelessWidget {
  const AkiraAppearancePage({super.key, required this.settings});
  final AkiraAppearanceSettings settings;

  @override
  Widget build(BuildContext context) {
    final s = settings;
    s.musicPreviewBuilder = (_) => MusicContentPreview(settings: s);
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) s.close();
      },
      child: ReviewScaffold(
        settings: s,
        title: s.tr('Appearance', 'Apariencia'),
        back: false,
        captureOnlyInGlass: true,
        body: (context) => ListView(
          padding: reviewInsets(context),
          children: [
            NativeGroup(
              model: s,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 20, 16, 12),
                child: Column(
                  children: [
                    Row(
                      children: [
                        for (final dark in [false, true])
                          Expanded(
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                              ),
                              child: _ThemeChoice(settings: s, dark: dark),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    _ToggleRow(
                      settings: s,
                      label: s.tr('Automatic', 'Automático'),
                      value: s.theme == 2,
                      changed: (v) => s.selectTheme(
                        v
                            ? 2
                            : s.dark
                            ? 1
                            : 0,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 32),
            NativeGroup(
              model: s,
              child: Column(
                children: [
                  _Entry(
                    settings: s,
                    label: s.tr('Color palette', 'Paleta de colores'),
                    last: false,
                    trailing: Container(
                      width: 22,
                      height: 22,
                      decoration: BoxDecoration(
                        color: s.accent,
                        shape: BoxShape.circle,
                      ),
                    ),
                    onTap: () => showAppearanceColors(context, s),
                  ),
                  _Entry(
                    settings: s,
                    label: s.tr('Style', 'Estilo'),
                    value: s.style,
                    onTap: () =>
                        reviewPush(context, s, _StylePage(settings: s)),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 32),
            NativeGroup(
              model: s,
              child: Column(
                children: [
                  _ToggleRow(
                    settings: s,
                    last: false,
                    label: s.tr(
                      'Full-screen artwork',
                      'Portada a pantalla completa',
                    ),
                    value: s.fullscreenArtwork,
                    changed: (v) => s.change(() => s.fullscreenArtwork = v),
                  ),
                  _ToggleRow(
                    settings: s,
                    last: false,
                    label: s.tr('Animated artwork', 'Portadas animadas'),
                    value: s.animatedArtwork,
                    changed: (v) => s.change(() => s.animatedArtwork = v),
                  ),
                  _ToggleRow(
                    settings: s,
                    label: s.tr('Reduce motion', 'Reducir movimiento'),
                    value: s.reduceMotion,
                    changed: (v) => s.change(() => s.reduceMotion = v),
                  ),
                ],
              ),
            ),
            if (s.style == 'Glass') ...[
              const SizedBox(height: 32),
              NativeGroup(
                model: s,
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _GlassPreview(settings: s),
                      const SizedBox(height: 16),
                      _ValueSlider(
                        settings: s,
                        label: s.tr('Glass opacity', 'Opacidad del cristal'),
                        value: s.glassOpacity,
                        onChanged: (v) => s.change(() => s.glassOpacity = v),
                      ),
                      _ValueSlider(
                        settings: s,
                        label: s.tr(
                          'Background diffusion',
                          'Difuminado del fondo',
                        ),
                        value: s.diffusion / 24,
                        onChanged: (v) => s.change(() => s.diffusion = v * 24),
                      ),
                      _ToggleRow(
                        settings: s,
                        label: s.tr(
                          'Apply diffusion to dock',
                          'Aplicar difuminado al dock',
                        ),
                        value: s.dockDiffusion,
                        changed: (v) => s.change(() => s.dockDiffusion = v),
                      ),
                      Text(
                        s.tr(
                          'Refraction stays on. This switch only controls background diffusion.',
                          'La refracción sigue activa. Este interruptor solo controla el difuminado del fondo.',
                        ),
                        style: nativeText(s, size: 13, color: s.secondary),
                      ),
                      const SizedBox(height: 12),
                      TextButton(
                        onPressed: s.restoreGlassDefaults,
                        child: Text(
                          s.tr(
                            'Restore glass defaults',
                            'Restaurar valores del cristal',
                          ),
                          style: nativeText(s, size: 16, color: s.accent),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            if (s.saveError != null) ...[
              const SizedBox(height: 20),
              Text(s.saveError!, style: nativeText(s, size: 14)),
              TextButton(
                onPressed: s.flush,
                child: Text(s.tr('Retry saving', 'Reintentar guardado')),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _GlassPreview extends StatefulWidget {
  const _GlassPreview({required this.settings});
  final AkiraAppearanceSettings settings;
  @override
  State<_GlassPreview> createState() => _GlassPreviewState();
}

class _GlassPreviewState extends State<_GlassPreview> {
  final capture = GlobalKey<NativeGlassStageState>();
  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    final enabled =
        context
            .dependOnInheritedWidgetOfExactType<ReviewCapabilities>()
            ?.shaders ??
        true;
    final track = s.tracks.firstOrNull;
    final lower = s.dark ? Colors.black : Colors.white;
    return Semantics(
      label: s.tr(
        'Player and dock preview',
        'Vista previa del reproductor y dock',
      ),
      image: true,
      child: SizedBox(
        height: 272,
        child: ClipRSuperellipse(
          borderRadius: BorderRadius.circular(22),
          child: ReviewCaptureScope(
            captureKey: capture,
            settings: s,
            enabled: enabled,
            child: NativeGlassStage(
              key: capture,
              model: s,
              enableShaders: enabled,
              background: Stack(
                fit: StackFit.expand,
                children: [
                  if (track?.artwork != null)
                    Image.memory(
                      track!.artwork!,
                      fit: BoxFit.cover,
                      gaplessPlayback: true,
                      errorBuilder: (_, _, _) =>
                          ColoredBox(color: s.background),
                    )
                  else
                    ColoredBox(
                      color: s.background,
                      child: Center(
                        child: Icon(
                          Icons.album_rounded,
                          color: s.accent.withValues(alpha: .25),
                          size: 100,
                        ),
                      ),
                    ),
                  DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [
                          lower.withValues(alpha: .58),
                          lower.withValues(alpha: .65),
                        ],
                      ),
                    ),
                  ),
                  Positioned(
                    top: 16,
                    left: 16,
                    right: 16,
                    child: Text(
                      s.tr('Library', 'Biblioteca'),
                      style: nativeText(s, size: 26, weight: FontWeight.w700),
                    ),
                  ),
                  if (track == null)
                    Center(
                      child: Text(
                        s.previewError ??
                            s.tr(
                              'Add music to preview your artwork.',
                              'Añade música para previsualizar tus portadas.',
                            ),
                        textAlign: TextAlign.center,
                        style: nativeText(s, size: 14),
                      ),
                    ),
                ],
              ),
              foreground: Builder(
                builder: (context) => Align(
                  alignment: Alignment.bottomCenter,
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        LiveGlassPane(
                          source: ReviewCaptureScope.of(context),
                          composes: false,
                          dock: true,
                          radius: 26,
                          child: Padding(
                            padding: const EdgeInsets.all(8),
                            child: Row(
                              children: [
                                _Artwork(
                                  bytes: track?.artwork,
                                  size: 34,
                                  color: s.accent,
                                ),
                                const SizedBox(width: 9),
                                Expanded(
                                  child: Text(
                                    track?.title ??
                                        s.tr('Your music', 'Tu música'),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: nativeText(
                                      s,
                                      size: 14,
                                      weight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Icon(
                                  Icons.play_arrow_rounded,
                                  size: 27,
                                  color: s.text,
                                ),
                                const SizedBox(width: 10),
                                Icon(
                                  Icons.fast_forward_rounded,
                                  size: 27,
                                  color: s.text,
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 8),
                        LiveGlassPane(
                          source: ReviewCaptureScope.of(context),
                          composes: false,
                          dock: true,
                          radius: 28,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 10,
                            ),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceAround,
                              children: [
                                Icon(
                                  Icons.home_rounded,
                                  color: s.secondary,
                                  size: 25,
                                ),
                                DecoratedBox(
                                  decoration: BoxDecoration(
                                    color: const Color(AkiraDockSpec.accentArgb)
                                        .withValues(alpha: .12),
                                    borderRadius: BorderRadius.circular(20),
                                  ),
                                  child: Padding(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 20,
                                      vertical: 4,
                                    ),
                                    child: const Icon(
                                      Icons.library_music_rounded,
                                      color: Color(AkiraDockSpec.accentArgb),
                                      size: 25,
                                    ),
                                  ),
                                ),
                                Icon(
                                  Icons.search_rounded,
                                  color: s.secondary,
                                  size: 25,
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ThemeChoice extends StatelessWidget {
  const _ThemeChoice({required this.settings, required this.dark});
  final AkiraAppearanceSettings settings;
  final bool dark;
  @override
  Widget build(BuildContext context) {
    final s = settings;
    final label = dark ? s.tr('Dark', 'Oscuro') : s.tr('Light', 'Claro');
    return Semantics(
      button: true,
      selected: s.dark == dark,
      label: label,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => s.selectTheme(dark ? 1 : 0),
        child: Column(
          children: [
            ExcludeSemantics(
              child: SizedBox(
                height: 184,
                child: _MiniLibrary(settings: s, dark: dark),
              ),
            ),
            const SizedBox(height: 12),
            Text(label, style: nativeText(s, size: 15)),
            const SizedBox(height: 8),
            SelectionCircle(selected: s.dark == dark, color: s.accent),
          ],
        ),
      ),
    );
  }
}

class _MiniLibrary extends StatelessWidget {
  const _MiniLibrary({required this.settings, required this.dark});
  final AkiraAppearanceSettings settings;
  final bool dark;
  @override
  Widget build(BuildContext context) {
    final ink = dark ? Colors.white : const Color(0xff171719);
    final surface = dark ? const Color(0xff171719) : Colors.white;
    final track = settings.tracks.firstOrNull;
    return ClipRSuperellipse(
      borderRadius: BorderRadius.circular(18),
      child: ColoredBox(
        color: surface,
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                settings.tr('Library', 'Biblioteca'),
                maxLines: 1,
                style: TextStyle(
                  fontFamily: settings.noteFamily,
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  color: ink,
                ),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: LayoutBuilder(
                  builder: (context, box) => ClipRRect(
                    borderRadius: BorderRadius.circular(9),
                    child: track?.artwork != null
                        ? Image.memory(
                            track!.artwork!,
                            width: box.maxWidth,
                            fit: BoxFit.cover,
                            gaplessPlayback: true,
                            errorBuilder: (_, _, _) => _Artwork(
                              bytes: null,
                              size: box.maxWidth,
                              color: settings.accent,
                            ),
                          )
                        : ColoredBox(
                            color: settings.accent.withValues(alpha: .14),
                            child: Center(
                              child: Icon(
                                Icons.album_rounded,
                                color: settings.accent,
                                size: 34,
                              ),
                            ),
                          ),
                  ),
                ),
              ),
              const SizedBox(height: 7),
              Text(
                track?.title ?? settings.tr('Your music', 'Tu música'),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontFamily: settings.noteFamily,
                  fontSize: 11,
                  color: ink,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              DecoratedBox(
                decoration: BoxDecoration(
                  color: ink.withValues(alpha: .07),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 5),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      Icon(
                        Icons.home_rounded,
                        size: 17,
                        color: ink.withValues(alpha: .6),
                      ),
                      const Icon(
                        Icons.library_music_rounded,
                        size: 17,
                        color: Color(AkiraDockSpec.accentArgb),
                      ),
                      Icon(
                        Icons.search_rounded,
                        size: 17,
                        color: ink.withValues(alpha: .6),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class MusicContentPreview extends StatelessWidget {
  const MusicContentPreview({
    super.key,
    required this.settings,
    this.compact = false,
  });
  final AkiraAppearanceSettings settings;
  final bool compact;
  @override
  Widget build(BuildContext context) {
    final s = settings;
    final size = s.noteSize;
    final type = TextStyle(
      fontFamily: s.noteFamily,
      fontSize: size,
      color: s.text,
      height: 1.35,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          s.tr('Your library', 'Tu biblioteca'),
          style: type.copyWith(
            fontSize: size * 1.45,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 14),
        if (s.tracks.isEmpty) ...[
          Text(
            s.previewError ??
                s.tr(
                  'Add music to see your library here.',
                  'Añade música para ver tu biblioteca aquí.',
                ),
            style: type,
          ),
          if (s.previewError != null)
            TextButton(
              onPressed: s.loadTracks,
              child: Text(s.tr('Retry preview', 'Reintentar vista previa')),
            ),
        ],
        for (final track in s.tracks.take(compact ? 1 : 3))
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8),
            child: Row(
              children: [
                _Artwork(bytes: track.artwork, size: 46, color: s.accent),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        track.title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: type.copyWith(fontWeight: FontWeight.w600),
                      ),
                      Text(
                        track.artist,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: type.copyWith(
                          fontSize: size * .85,
                          color: s.secondary,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

class _Artwork extends StatelessWidget {
  const _Artwork({
    required this.bytes,
    required this.size,
    required this.color,
  });
  final Uint8List? bytes;
  final double size;
  final Color color;
  @override
  Widget build(BuildContext context) => ClipRSuperellipse(
    borderRadius: BorderRadius.circular(size / 6),
    child: SizedBox.square(
      dimension: size,
      child: bytes == null
          ? ColoredBox(
              color: color.withValues(alpha: .14),
              child: Icon(Icons.music_note, color: color, size: size * .55),
            )
          : Image.memory(
              bytes!,
              fit: BoxFit.cover,
              gaplessPlayback: true,
              errorBuilder: (_, _, _) =>
                  ColoredBox(color: color.withValues(alpha: .14)),
            ),
    ),
  );
}

class _StylePage extends StatelessWidget {
  const _StylePage({required this.settings});
  final AkiraAppearanceSettings settings;
  @override
  Widget build(BuildContext context) {
    final s = settings;
    return ReviewScaffold(
      settings: s,
      title: s.tr('Style', 'Estilo'),
      back: false,
      captureOnlyInGlass: true,
      body: (_) => ListView(
        padding: reviewInsets(context),
        children: [
          NativeGroup(
            model: s,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 28),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (final style in ['Classic', 'Glass'])
                    Expanded(
                      child: _StyleChoice(settings: s, style: style),
                    ),
                ],
              ),
            ),
          ),
          if (s.style == 'Glass') ...[
            const SizedBox(height: 30),
            NativeGroup(
              model: s,
              child: _ToggleRow(
                settings: s,
                label: s.tr('Reduce transparency', 'Reducir transparencia'),
                value: s.reduceTransparency,
                changed: (v) => s.change(() => s.reduceTransparency = v),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 0),
              child: Text(
                s.tr(
                  'Use solid surfaces while keeping the same controls and animations.',
                  'Usa superficies sólidas conservando los controles y las animaciones.',
                ),
                style: nativeText(s, size: 13, color: s.secondary),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _StyleChoice extends StatelessWidget {
  const _StyleChoice({required this.settings, required this.style});
  final AkiraAppearanceSettings settings;
  final String style;
  @override
  Widget build(BuildContext context) {
    final s = settings;
    final glass = style == 'Glass';
    final label = glass ? s.tr('Glass', 'Cristal') : s.tr('Classic', 'Clásico');
    return Semantics(
      button: true,
      selected: s.style == style,
      label: label,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => s.change(() => s.style = style),
        child: Column(
          children: [
            ExcludeSemantics(
              child: SizedBox(
                width: 112,
                height: 220,
                child: _StyleMiniature(settings: s, glass: glass),
              ),
            ),
            const SizedBox(height: 14),
            Text(label, style: nativeText(s, size: 17)),
            const SizedBox(height: 12),
            SelectionCircle(selected: s.style == style, color: s.accent),
          ],
        ),
      ),
    );
  }
}

class _StyleMiniature extends StatelessWidget {
  const _StyleMiniature({required this.settings, required this.glass});
  final AkiraAppearanceSettings settings;
  final bool glass;
  @override
  Widget build(BuildContext context) {
    final s = settings;
    final track = s.tracks.firstOrNull;
    final ink = s.dark ? Colors.white : const Color(0xff171719);
    final surface = s.dark ? const Color(0xff101012) : Colors.white;
    Widget cover() => track?.artwork != null
        ? Image.memory(
            track!.artwork!,
            fit: BoxFit.cover,
            gaplessPlayback: true,
            errorBuilder: (_, _, _) =>
                ColoredBox(color: s.accent.withValues(alpha: .2)),
          )
        : ColoredBox(color: s.accent.withValues(alpha: .2));
    Widget dock() => Row(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      children: [
        Icon(Icons.home_rounded, size: 14, color: ink.withValues(alpha: .6)),
        const Icon(
          Icons.library_music_rounded,
          size: 14,
          color: Color(AkiraDockSpec.accentArgb),
        ),
        Icon(Icons.search_rounded, size: 14, color: ink.withValues(alpha: .6)),
      ],
    );
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: ink.withValues(alpha: .18), width: 1),
      ),
      child: Padding(
        padding: const EdgeInsets.all(1),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(19),
          child: ColoredBox(
            color: surface,
            child: Stack(
              children: [
                if (glass) Positioned.fill(child: cover()),
                if (glass)
                  Positioned.fill(
                    child: ColoredBox(
                      color: Colors.black.withValues(alpha: .6),
                    ),
                  ),
                Positioned(
                  top: 14,
                  left: 10,
                  right: 10,
                  child: Text(
                    s.tr('Library', 'Biblioteca'),
                    maxLines: 1,
                    style: TextStyle(
                      fontFamily: s.noteFamily,
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      color: glass ? Colors.white : ink,
                    ),
                  ),
                ),
                if (!glass)
                  Positioned(
                    top: 43,
                    left: 10,
                    right: 10,
                    height: 89,
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: cover(),
                    ),
                  ),
                Positioned(
                  left: 10,
                  right: 10,
                  top: glass ? 83 : 141,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        track?.title ?? s.tr('Your music', 'Tu música'),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: s.noteFamily,
                          fontSize: 9,
                          fontWeight: FontWeight.w600,
                          color: glass ? Colors.white : ink,
                        ),
                      ),
                      const SizedBox(height: 5),
                      Text(
                        track?.artist ?? 'Akira',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontFamily: s.noteFamily,
                          fontSize: 8,
                          color: (glass ? Colors.white : ink).withValues(
                            alpha: .65,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                Positioned(
                  left: 7,
                  right: 7,
                  bottom: 10,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(glass ? 18 : 8),
                    child: BackdropFilter(
                      filter: ui.ImageFilter.blur(
                        sigmaX: glass ? 5 : 0,
                        sigmaY: glass ? 5 : 0,
                      ),
                      child: ColoredBox(
                        color: glass
                            ? Colors.white.withValues(alpha: .25)
                            : ink.withValues(alpha: .08),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          child: dock(),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Entry extends StatelessWidget {
  const _Entry({
    required this.settings,
    required this.label,
    required this.onTap,
    this.value,
    this.trailing,
    this.last = true,
  });
  final AkiraAppearanceSettings settings;
  final String label;
  final VoidCallback onTap;
  final String? value;
  final Widget? trailing;
  final bool last;
  @override
  Widget build(BuildContext context) => NativeRow(
    label,
    model: settings,
    onTap: onTap,
    last: last,
    trailing: ConstrainedBox(
      constraints: BoxConstraints(
        maxWidth: MediaQuery.sizeOf(context).width * .38,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (value != null)
            Flexible(
              child: Text(
                value!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: nativeText(settings, color: settings.secondary),
              ),
            ),
          if (trailing != null) trailing!,
          const SizedBox(width: 8),
          NativePathIcon('chevron_right', color: settings.secondary, size: 16),
        ],
      ),
    ),
  );
}

class _ToggleRow extends StatelessWidget {
  const _ToggleRow({
    required this.settings,
    required this.label,
    required this.value,
    required this.changed,
    this.last = true,
  });
  final AkiraAppearanceSettings settings;
  final String label;
  final bool value;
  final bool last;
  final ValueChanged<bool> changed;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 9),
        child: Row(
          children: [
            Expanded(child: Text(label, style: nativeText(settings))),
            const SizedBox(width: 12),
            AppearanceSwitch(
              settings: settings,
              label: label,
              value: value,
              onChanged: changed,
            ),
          ],
        ),
      ),
      if (!last)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: SizedBox(
            width: double.infinity,
            height: 1,
            child: ColoredBox(color: settings.divider),
          ),
        ),
    ],
  );
}

class _ValueSlider extends StatelessWidget {
  const _ValueSlider({
    required this.settings,
    required this.label,
    required this.value,
    required this.onChanged,
  });
  final AkiraAppearanceSettings settings;
  final String label;
  final double value;
  final ValueChanged<double> onChanged;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 12),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(child: Text(label, style: nativeText(settings, size: 15))),
            Text(
              '${(value * 100).round()}%',
              style: nativeText(settings, size: 15, color: settings.secondary),
            ),
          ],
        ),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: InteractiveValueSlider(
            settings: settings,
            label: label,
            value: value.clamp(0, 1),
            onChanged: onChanged,
          ),
        ),
      ],
    ),
  );
}
