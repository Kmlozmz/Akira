import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:inotes_recovered/inotes_recovered.dart';
import '../design_lab.dart' as previous;
import 'components.dart';
import 'navigation.dart';

void main() => runApp(const RecoveredLaboratory());

class RecoveredLaboratory extends StatefulWidget {
  const RecoveredLaboratory({super.key, this.model, this.enableShaders = true});
  final RecoveredLabModel? model;
  final bool enableShaders;
  @override
  State<RecoveredLaboratory> createState() => _RecoveredLaboratoryState();
}

class _RecoveredLaboratoryState extends State<RecoveredLaboratory> {
  late final model = widget.model ?? RecoveredLabModel();
  @override
  void dispose() {
    if (widget.model == null) model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: model,
    builder: (context, _) => MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'iNotes · Laboratorio',
      theme: ThemeData(
        fontFamily: recoveredFont,
        brightness: model.dark ? Brightness.dark : Brightness.light,
        scaffoldBackgroundColor: model.background,
        colorScheme: ColorScheme.fromSeed(
          seedColor: model.accent,
          brightness: model.dark ? Brightness.dark : Brightness.light,
        ),
      ),
      home: _Catalog(model: model, shaders: widget.enableShaders),
    ),
  );
}

void _push(BuildContext context, Widget child, RecoveredLabModel model) {
  Navigator.of(context).push(
    RecoveredLabRoute<void>(
      builder: (_) => child,
      reduceMotion:
          model.reduceMotion || MediaQuery.disableAnimationsOf(context),
    ),
  );
}

class NativePage extends StatelessWidget {
  const NativePage({
    super.key,
    required this.model,
    required this.title,
    required this.body,
    this.foreground = const SizedBox.shrink(),
    this.enableShaders = true,
    this.largeTitle = false,
    this.back = true,
  });
  final RecoveredLabModel model;
  final String title;
  final Widget body, foreground;
  final bool enableShaders, largeTitle, back;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: model,
    builder: (context, _) {
      final padding = MediaQuery.paddingOf(context);
      return AnnotatedRegion<SystemUiOverlayStyle>(
        value: model.dark
            ? SystemUiOverlayStyle.light
            : SystemUiOverlayStyle.dark,
        child: Scaffold(
          backgroundColor: model.background,
          body: NativeGlassStage(
            model: model,
            enableShaders: enableShaders,
            background: ColoredBox(
              color: model.background,
              child: Column(
                children: [
                  SizedBox(height: padding.top + (largeTitle ? 104 : 88)),
                  Expanded(child: body),
                ],
              ),
            ),
            foreground: Stack(
              fit: StackFit.expand,
              children: [
                if (largeTitle)
                  Positioned(
                    top: padding.top + 36,
                    left: 16,
                    right: 16,
                    child: Text(
                      title,
                      style: inotesVerifiedTextRoles['notes-header']!
                          .toTextStyle(color: model.text),
                    ),
                  )
                else
                  Positioned(
                    top: padding.top + 16,
                    left: 72,
                    right: 72,
                    height: 44,
                    child: Center(
                      child: Text(
                        title,
                        style: nativeText(model, weight: FontWeight.w600),
                      ),
                    ),
                  ),
                if (back)
                  Positioned(
                    top: padding.top + 16,
                    left: 16,
                    child: NativeGlassButton(
                      label: 'Volver',
                      onPressed: () => Navigator.of(context).maybePop(),
                      child: NativePathIcon('chevron_left', color: model.text),
                    ),
                  ),
                foreground,
              ],
            ),
          ),
        ),
      );
    },
  );
}

class _Catalog extends StatelessWidget {
  const _Catalog({required this.model, required this.shaders});
  final RecoveredLabModel model;
  final bool shaders;
  @override
  Widget build(BuildContext context) => NativePage(
    model: model,
    title: 'Laboratorio',
    back: false,
    largeTitle: true,
    enableShaders: shaders,
    body: ListView(
      padding: EdgeInsets.fromLTRB(
        16,
        20,
        16,
        MediaQuery.paddingOf(context).bottom + 24,
      ),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: Text(
            'Reconstrucción de iNotes',
            style: nativeText(
              model,
              size: 17,
              weight: FontWeight.w600,
              color: model.secondary,
            ),
          ),
        ),
        NativeGroup(
          model: model,
          child: Column(
            children: [
              _entry(
                context,
                'Font & Size',
                NativeFontPage(model: model, shaders: shaders),
                last: false,
              ),
              _entry(
                context,
                'Controles',
                NativeControlsPage(model: model, shaders: shaders),
                last: false,
              ),
              _entry(
                context,
                'Superficies glass',
                NativeSurfacesPage(model: model, shaders: shaders),
                last: false,
              ),
              _entry(
                context,
                'Iconos originales',
                NativeIconsPage(model: model, shaders: shaders),
              ),
            ],
          ),
        ),
        const SizedBox(height: 28),
        NativeGroup(
          model: model,
          child: Column(
            children: [
              NativeRow(
                'Modo oscuro',
                model: model,
                last: false,
                trailing: NativeToggle(
                  label: 'Modo oscuro',
                  value: model.dark,
                  model: model,
                  onChanged: (v) => model.change(() => model.dark = v),
                ),
              ),
              NativeRow(
                'Reducir movimiento',
                model: model,
                last: false,
                trailing: NativeToggle(
                  label: 'Reducir movimiento',
                  value: model.reduceMotion,
                  model: model,
                  onChanged: (v) => model.change(() => model.reduceMotion = v),
                ),
              ),
              NativeRow(
                'Catálogo anterior',
                model: model,
                trailing: NativePathIcon(
                  'chevron_right',
                  color: model.secondary,
                  size: 16,
                ),
                onTap: () => _push(context, const previous.DesignLab(), model),
              ),
            ],
          ),
        ),
        const SizedBox(height: 24),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text(
            'Primera tanda de componentes recuperados. Las superficies usan los shaders originales; el gesto ya permite volver y cancelar. La comparación visual completa sigue abierta.',
            style: nativeText(
              model,
              size: 13,
              height: 16 / 13,
              color: model.secondary,
            ),
          ),
        ),
      ],
    ),
  );
  Widget _entry(
    BuildContext context,
    String label,
    Widget child, {
    bool last = true,
  }) => NativeRow(
    label,
    model: model,
    last: last,
    onTap: () => _push(context, child, model),
    trailing: NativePathIcon('chevron_right', color: model.secondary, size: 16),
  );
}

class NativeFontPage extends StatelessWidget {
  const NativeFontPage({super.key, required this.model, this.shaders = true});
  final RecoveredLabModel model;
  final bool shaders;
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: model,
    builder: (context, _) => NativePage(
      model: model,
      title: 'Font & Size',
      enableShaders: shaders,
      body: Padding(
        padding: EdgeInsets.fromLTRB(
          16,
          0,
          16,
          MediaQuery.paddingOf(context).bottom + 20,
        ),
        child: LayoutBuilder(
          builder: (context, constraints) => SingleChildScrollView(
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: constraints.maxHeight),
              child: SizedBox(
                height: math.max(
                  constraints.maxHeight,
                  280 + 32 + 52 + 32 + 52 + 9 + 48,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    NativeGroup(
                      model: model,
                      child: SizedBox(
                        height: 280,
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
                          child: SingleChildScrollView(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'Your notes will look like this',
                                  style: inotesVerifiedTextRoles['editor-h1']!
                                      .toTextStyle(
                                        color: model.dark
                                            ? const Color(0xffdcdcdc)
                                            : const Color(0xff454545),
                                        fontSize: 28 * model.noteSize / 17,
                                      ),
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  'Drag the slider below until this paragraph is easy to read. Titles, headings and lists follow the same size.',
                                  style: nativeText(
                                    model,
                                    size: model.noteSize,
                                    height: 26 / 17,
                                    note: true,
                                    color: model.dark
                                        ? const Color(0xffdcdcdc)
                                        : const Color(0xff454545),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                    const Spacer(),
                    const SizedBox(height: 32),
                    NativeGroup(
                      model: model,
                      child: NativeRow(
                        'Font',
                        model: model,
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              'Default',
                              style: nativeText(model, color: model.secondary),
                            ),
                            const SizedBox(width: 6),
                            NativePathIcon(
                              'chevron_up_down',
                              color: model.secondary,
                              size: 16,
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                    NativeGroup(
                      model: model,
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        child: Row(
                          children: [
                            Text(
                              'A',
                              style: nativeText(
                                model,
                                size: 14,
                                height: 1,
                                weight: FontWeight.w500,
                                color: model.secondary,
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(child: NativeSizeSlider(model: model)),
                            const SizedBox(width: 8),
                            Text(
                              'A',
                              style: nativeText(
                                model,
                                size: 23,
                                height: 1,
                                weight: FontWeight.w500,
                                color: model.secondary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 9, 16, 0),
                      child: Text(
                        'Choose the font and size for your notes. It does not affect the rest of the application.',
                        style: nativeText(
                          model,
                          size: 13,
                          height: 16 / 13,
                          color: model.secondary,
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
  );
}

class NativeControlsPage extends StatefulWidget {
  const NativeControlsPage({
    super.key,
    required this.model,
    this.shaders = true,
  });
  final RecoveredLabModel model;
  final bool shaders;
  @override
  State<NativeControlsPage> createState() => _NativeControlsPageState();
}

class _NativeControlsPageState extends State<NativeControlsPage> {
  bool auto = false, background = true;
  @override
  Widget build(BuildContext context) {
    final m = widget.model;
    return ListenableBuilder(
      listenable: m,
      builder: (context, _) => NativePage(
        model: m,
        title: 'Controles',
        enableShaders: widget.shaders,
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 32),
          children: [
            NativeGroup(
              model: m,
              child: Column(
                children: [
                  NativeRow(
                    'Auto',
                    model: m,
                    last: false,
                    trailing: NativeToggle(
                      label: 'Auto',
                      model: m,
                      value: auto,
                      onChanged: (v) => setState(() => auto = v),
                    ),
                  ),
                  NativeRow(
                    'Background',
                    model: m,
                    last: false,
                    trailing: NativeToggle(
                      label: 'Background',
                      model: m,
                      value: background,
                      onChanged: (v) => setState(() => background = v),
                    ),
                  ),
                  NativeRow(
                    'Desactivado',
                    model: m,
                    trailing: NativeToggle(
                      label: 'Desactivado',
                      model: m,
                      value: false,
                      enabled: false,
                      onChanged: (_) {},
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 32),
            NativeGroup(
              model: m,
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                child: NativeSizeSlider(model: m),
              ),
            ),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(
                '${m.noteSize.toInt()} pt · 7 tamaños observados',
                style: nativeText(m, size: 13, color: m.secondary),
              ),
            ),
            const SizedBox(height: 32),
            NativeGroup(
              model: m,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Wrap(
                  spacing: 14,
                  runSpacing: 14,
                  children: [
                    for (var i = 0; i < inotesPresetColors.length; i++)
                      Semantics(
                        label: 'Acento ${i + 1}',
                        button: true,
                        selected: m.accentIndex == i,
                        child: GestureDetector(
                          onTap: () => m.change(() => m.accentIndex = i),
                          child: SizedBox.square(
                            dimension: 40,
                            child: DecoratedBox(
                              decoration: BoxDecoration(
                                color: inotesPresetColors[i],
                                shape: BoxShape.circle,
                              ),
                              child: m.accentIndex == i
                                  ? Center(
                                      child: Text(
                                        String.fromCharCode(0xf3fd),
                                        style: const TextStyle(
                                          fontFamily:
                                              'packages/inotes_recovered/InotesCupertino',
                                          fontSize: 24,
                                          color: Colors.white,
                                        ),
                                      ),
                                    )
                                  : null,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class NativeSurfacesPage extends StatefulWidget {
  const NativeSurfacesPage({
    super.key,
    required this.model,
    this.shaders = true,
  });
  final RecoveredLabModel model;
  final bool shaders;
  @override
  State<NativeSurfacesPage> createState() => _NativeSurfacesPageState();
}

class _NativeSurfacesPageState extends State<NativeSurfacesPage> {
  int taps = 0;
  @override
  Widget build(BuildContext context) {
    final m = widget.model;
    return NativePage(
      model: m,
      title: 'Superficies glass',
      enableShaders: widget.shaders,
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 180),
        children: [
          Text(
            'Desplaza el fondo',
            style: nativeText(m, size: 28, weight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          Text(
            'La luz y los contornos reaccionan al contenido situado detrás.',
            style: nativeText(m, color: m.secondary),
          ),
          for (var i = 0; i < 16; i++)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: NativeGroup(
                model: m,
                child: SizedBox(
                  height: 100,
                  child: Padding(
                    padding: const EdgeInsets.all(20),
                    child: Row(
                      children: [
                        SizedBox(
                          width: 48,
                          height: 48,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              color: inotesPresetColors[i % 9],
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: Text(
                            [
                              'Notas de viaje',
                              'Ideas para Akira',
                              'Diseño y movimiento',
                              'Archivo personal',
                            ][i % 4],
                            style: inotesVerifiedTextRoles['note-row-title']!
                                .toTextStyle(color: m.text),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
      foreground: Stack(
        children: [
          Positioned(
            bottom: MediaQuery.paddingOf(context).bottom + 32,
            left: 32,
            child: NativeGlassButton(
              label: 'Herramientas glass',
              recipe: 'bottom_tools',
              size: const Size(115, 48),
              onPressed: () => setState(() => taps++),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  NativePathIcon('checklist', color: m.text),
                  const SizedBox(width: 28),
                  NativePathIcon('paperclip', color: m.text),
                ],
              ),
            ),
          ),
          Positioned(
            bottom: MediaQuery.paddingOf(context).bottom + 32,
            right: 32,
            child: NativeGlassButton(
              label: 'Nueva nota de muestra',
              recipe: 'new_note',
              size: const Size(48, 48),
              onPressed: () => setState(() => taps++),
              child: NativePathIcon('square_pencil', color: m.text),
            ),
          ),
          Positioned(
            top: MediaQuery.paddingOf(context).top + 110,
            right: 24,
            child: NativeGlassButton(
              label: 'Acciones glass',
              recipe: 'top_actions',
              size: const Size(100, 44),
              onPressed: () => setState(() => taps++),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  NativePathIcon('square_arrow_up', color: m.text),
                  const SizedBox(width: 22),
                  NativePathIcon('ellipsis', color: m.text),
                ],
              ),
            ),
          ),
          if (taps > 0)
            Positioned(
              bottom: MediaQuery.paddingOf(context).bottom + 94,
              left: 32,
              child: Text(
                '$taps pulsaciones',
                style: nativeText(m, size: 13, color: m.secondary),
              ),
            ),
        ],
      ),
    );
  }
}

class NativeIconsPage extends StatelessWidget {
  const NativeIconsPage({super.key, required this.model, this.shaders = true});
  final RecoveredLabModel model;
  final bool shaders;
  @override
  Widget build(BuildContext context) => NativePage(
    model: model,
    title: 'Iconos originales',
    enableShaders: shaders,
    body: FutureBuilder<String>(
      future: rootBundle.loadString('assets/recovered_components/icons.json'),
      builder: (context, snapshot) {
        if (!snapshot.hasData) return const SizedBox.shrink();
        final icons = (jsonDecode(snapshot.data!) as Map)['icons'] as List;
        return GridView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: icons.length,
          gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
            maxCrossAxisExtent: 100,
            mainAxisSpacing: 12,
            crossAxisSpacing: 12,
          ),
          itemBuilder: (context, index) {
            final icon = icons[index] as Map;
            return Semantics(
              label: icon['codepoint'] as String,
              child: NativeGroup(
                model: model,
                radius: 16,
                child: Center(
                  child: Text(
                    String.fromCharCode(
                      int.parse(
                        (icon['codepoint'] as String).substring(2),
                        radix: 16,
                      ),
                    ),
                    style: TextStyle(
                      fontFamily: icon['flutter_family_alias'] as String,
                      fontSize: 28,
                      color: model.accent,
                      fontVariations:
                          (icon['font_families'] as List).first ==
                              'MaterialIcons'
                          ? const [
                              ui.FontVariation('GRAD', 0),
                              ui.FontVariation('wght', 400),
                              ui.FontVariation('opsz', 48),
                              ui.FontVariation('FILL', 0),
                            ]
                          : null,
                    ),
                  ),
                ),
              ),
            );
          },
        );
      },
    ),
  );
}
