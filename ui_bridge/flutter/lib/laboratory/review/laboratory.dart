import '../../design_system/check_mark.dart';
import '../../design_system/navigation_controls.dart';

import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:inotes_design_reference/inotes_design_reference.dart'
    as reference;

import '../recovered/components.dart';
import '../recovered/laboratory.dart' as first;
import '../recovered/navigation.dart';
import '../recovered/motion/recovered_pointer_exclusions.dart';
import 'settings.dart';
import 'materials.dart';
import 'live_glass.dart';
import 'appearance_panels.dart';
import 'font_style_pages.dart';
import 'live_review_page.dart';
import 'interactive_controls.dart';
import 'frame_audit.dart';
import 'shader_warm_up.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  final settings = AppearanceSettings();
  await settings.load();
  if (settings.style == 'Glass' && !settings.reduceTransparency) {
    await warmUpMenuComposition();
  }
  runApp(ReviewLaboratory(settings: settings));
}

void reviewPush(
  BuildContext context,
  AppearanceSettings settings,
  Widget child,
) {
  Navigator.of(context).push(
    RecoveredLabRoute<void>(
      builder: (_) => child,
      reduceMotion:
          settings.reduceMotion || MediaQuery.disableAnimationsOf(context),
    ),
  );
}

class ReviewLaboratory extends StatefulWidget {
  const ReviewLaboratory({
    super.key,
    required this.settings,
    this.autoOpen = true,
    this.shaders = true,
  });
  final AppearanceSettings settings;
  final bool autoOpen, shaders;
  @override
  State<ReviewLaboratory> createState() => _ReviewLaboratoryState();
}

class _ReviewLaboratoryState extends State<ReviewLaboratory>
    with WidgetsBindingObserver {
  (bool, int)? _themeKey;
  ThemeData? _theme;
  ThemeData get appearanceTheme {
    final s = widget.settings;
    final key = (s.dark, s.accent.toARGB32());
    if (_themeKey != key) {
      _themeKey = key;
      _theme = ThemeData(
        fontFamily: recoveredFont,
        brightness: s.dark ? Brightness.dark : Brightness.light,
        scaffoldBackgroundColor: s.background,
        colorScheme: ColorScheme.fromSeed(
          seedColor: s.accent,
          brightness: s.dark ? Brightness.dark : Brightness.light,
        ),
        splashFactory: NoSplash.splashFactory,
      );
    }
    return _theme!;
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    ReviewFrameAudit.start();
  }

  @override
  void didChangePlatformBrightness() => widget.settings.platformBrightness(
    WidgetsBinding.instance.platformDispatcher.platformBrightness,
  );
  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    ReviewFrameAudit.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.settings,
    builder: (context, _) => MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'iNotes Design Lab',
      theme: appearanceTheme,
      builder: (context, child) =>
          ReviewCapabilities(shaders: widget.shaders, child: child!),
      home: ReviewHome(
        settings: widget.settings,
        autoOpen: widget.autoOpen,
        shaders: widget.shaders,
      ),
    ),
  );
}

class ReviewCapabilities extends InheritedWidget {
  const ReviewCapabilities({
    super.key,
    required this.shaders,
    required super.child,
  });
  final bool shaders;
  @override
  bool updateShouldNotify(ReviewCapabilities oldWidget) =>
      oldWidget.shaders != shaders;
}

class ReviewScaffold extends StatefulWidget {
  ReviewScaffold({
    super.key,
    required this.settings,
    required this.title,
    required this.body,
    this.back = true,
    this.backLabel = 'Appearance',
    this.shaders = true,
    this.action,
    this.sceneForeground,
  });
  final AppearanceSettings settings;
  final String title;
  final Widget Function(BuildContext) body;
  final bool back, shaders;
  final String backLabel;
  final Widget? action;
  final Widget? sceneForeground;
  @override
  State<ReviewScaffold> createState() => _ReviewScaffoldState();
}

class _ReviewScaffoldState extends State<ReviewScaffold> {
  AppearanceSettings get settings => widget.settings;
  String get title => widget.title;
  Widget Function(BuildContext) get body => widget.body;
  bool get back => widget.back;
  bool get shaders => widget.shaders;
  Widget? get action => widget.action;
  Widget? get sceneForeground => widget.sceneForeground;
  final captureKey = GlobalKey<NativeGlassStageState>();
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: settings,
    builder: (context, _) {
      final top = MediaQuery.paddingOf(context).top;
      final glass =
          shaders &&
          (context
                  .dependOnInheritedWidgetOfExactType<ReviewCapabilities>()
                  ?.shaders ??
              true) &&
          settings.style == 'Glass' &&
          !settings.reduceTransparency;
      final captureEnabled =
          shaders &&
          (context
                  .dependOnInheritedWidgetOfExactType<ReviewCapabilities>()
                  ?.shaders ??
              true);
      return ReviewCaptureScope(
        captureKey: captureKey,
        settings: settings,
        enabled: captureEnabled,
        child: AnnotatedRegion<SystemUiOverlayStyle>(
          value: settings.dark
              ? SystemUiOverlayStyle.light
              : SystemUiOverlayStyle.dark,
          child: Scaffold(
            backgroundColor: settings.background,
            body: NativeGlassStage(
              key: captureKey,
              model: settings,
              enableShaders: captureEnabled,
              background: ColoredBox(
                color: settings.background,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    Builder(builder: body),
                    Positioned(
                      top: 0,
                      left: 0,
                      right: 0,
                      height: top + 88,
                      child: ProgressiveHeaderVeil(color: settings.background),
                    ),
                  ],
                ),
              ),
              foreground: Stack(
                fit: StackFit.expand,
                children: [
                  if (sceneForeground != null) sceneForeground!,
                  Positioned(
                    top: top + 16,
                    left: 72,
                    right: 72,
                    height: 44,
                    child: Center(
                      child: Text(
                        title,
                        style: nativeText(settings, weight: FontWeight.w600),
                      ),
                    ),
                  ),
                  if (back)
                    Positioned(
                      top: top + 16,
                      left: settings.style == 'Classic' ? 16 : 22,
                      child: settings.style == 'Classic'
                          ? Semantics(
                              button: true,
                              label: settings.ui(widget.backLabel),
                              child: GestureDetector(
                                behavior: HitTestBehavior.opaque,
                                onTap: () => Navigator.of(context).maybePop(),
                                child: ConstrainedBox(
                                  constraints: BoxConstraints(
                                    maxWidth:
                                        MediaQuery.sizeOf(context).width * .31,
                                    minHeight: 44,
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      NativePathIcon(
                                        'chevron_left',
                                        color: settings.accent,
                                        size: 20,
                                      ),
                                      const SizedBox(width: 4),
                                      Flexible(
                                        child: Text(
                                          settings.ui(widget.backLabel),
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: nativeText(
                                            settings,
                                            color: settings.accent,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            )
                          : AkiraBackButton(
                              label: settings.ui('Back'),
                              color: settings.text,
                              glass: glass,
                              surface: settings.surface,
                              onPressed: () => Navigator.of(context).maybePop(),
                            ),
                    ),
                  if (action != null)
                    Positioned(top: top + 16, right: 16, child: action!),
                  if (settings.saveError != null)
                    Positioned(
                      left: 16,
                      right: 16,
                      bottom: MediaQuery.paddingOf(context).bottom + 8,
                      child: Text(
                        settings.saveError!,
                        style: nativeText(settings, size: 13),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      );
    },
  );
}

EdgeInsets reviewInsets(BuildContext context, {double top = 100}) =>
    EdgeInsets.fromLTRB(
      16,
      MediaQuery.paddingOf(context).top + top,
      16,
      MediaQuery.paddingOf(context).bottom + 32,
    );

Widget reviewEntry(
  BuildContext context,
  AppearanceSettings settings,
  String label,
  Widget page, {
  bool last = true,
  Widget? trailing,
}) => NativeRow(
  label,
  model: settings,
  last: last,
  trailing:
      trailing ??
      NativePathIcon('chevron_right', color: settings.secondary, size: 16),
  onTap: () => reviewPush(context, settings, page),
);

class ReviewHome extends StatefulWidget {
  const ReviewHome({
    super.key,
    required this.settings,
    this.autoOpen = false,
    this.shaders = true,
  });
  final AppearanceSettings settings;
  final bool autoOpen, shaders;
  @override
  State<ReviewHome> createState() => _ReviewHomeState();
}

class _ReviewHomeState extends State<ReviewHome> {
  @override
  void initState() {
    super.initState();
    if (widget.autoOpen)
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted)
          reviewPush(
            context,
            widget.settings,
            AppearancePage(settings: widget.settings, shaders: widget.shaders),
          );
      });
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return ReviewScaffold(
      settings: s,
      title: 'Laboratorio',
      back: false,
      shaders: widget.shaders,
      body: (context) => ListView(
        padding: reviewInsets(context),
        children: [
          NativeGroup(
            model: s,
            child: Column(
              children: [
                reviewEntry(
                  context,
                  s,
                  'Appearance',
                  AppearancePage(settings: s, shaders: widget.shaders),
                  last: false,
                ),
                reviewEntry(
                  context,
                  s,
                  'Glass en vivo',
                  LiveReviewPage(settings: s),
                  last: false,
                ),
                reviewEntry(
                  context,
                  s,
                  'Las seis familias de glass',
                  MaterialCatalogue(settings: s),
                  last: false,
                ),
                reviewEntry(
                  context,
                  s,
                  'Navegación y movimiento',
                  MotionPage(settings: s),
                  last: false,
                ),
                reviewEntry(
                  context,
                  s,
                  'Tipografía completa',
                  TypographyPage(settings: s),
                  last: false,
                ),
                reviewEntry(
                  context,
                  s,
                  'Iconos originales',
                  first.NativeIconsPage(model: s, shaders: widget.shaders),
                  last: false,
                ),
                reviewEntry(
                  context,
                  s,
                  'Controles y estados',
                  ReviewControlsPage(settings: s),
                  last: false,
                ),
                reviewEntry(
                  context,
                  s,
                  '117 componentes medidos',
                  OccurrencesPage(settings: s),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          NativeGroup(
            model: s,
            child: NativeRow(
              'Reducir movimiento',
              model: s,
              trailing: InteractiveToggle(
                label: 'Reducir movimiento',
                settings: s,
                value: s.reduceMotion,
                onChanged: (v) => s.change(() => s.reduceMotion = v),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'Abre cada muestra para probarla. Appearance controla el tema, el acento, el estilo y el texto de las notas. Los estados originales de glass se pueden comparar con su render.',
            style: nativeText(s, size: 13, height: 16 / 13, color: s.secondary),
          ),
        ],
      ),
    );
  }
}

class AppearancePage extends StatelessWidget {
  const AppearancePage({
    super.key,
    required this.settings,
    this.shaders = true,
  });
  final AppearanceSettings settings;
  final bool shaders;
  @override
  Widget build(BuildContext context) {
    final s = settings;
    return ReviewScaffold(
      settings: s,
      title: 'Appearance',
      shaders: shaders,
      body: (context) => ListView(
        padding: reviewInsets(context, top: 80),
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 16, bottom: 10),
            child: Text(
              'Theme',
              style: nativeText(s, weight: FontWeight.w600, color: s.secondary),
            ),
          ),
          NativeGroup(
            model: s,
            child: Column(
              children: [
                SizedBox(
                  height: 252,
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _ThemeOption(settings: s, dark: false),
                      _ThemeOption(settings: s, dark: true),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: SizedBox(
                    width: double.infinity,
                    height: 1,
                    child: ColoredBox(color: s.divider),
                  ),
                ),
                SizedBox(
                  height: 51,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Row(
                      children: [
                        Expanded(
                          child: ExcludeSemantics(
                            child: Text('Auto', style: nativeText(s)),
                          ),
                        ),
                        AppearanceSwitch(
                          value: s.theme == 2,
                          settings: s,
                          onChanged: (v) => s.selectTheme(
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
              ],
            ),
          ),
          const SizedBox(height: 30),
          NativeGroup(
            model: s,
            child: Column(
              children: [
                _appearanceEntry(
                  context,
                  s,
                  'Color',
                  ColorPage(settings: s),
                  last: false,
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      SizedBox.square(
                        dimension: 20,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: s.accent,
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      NativePathIcon(
                        'chevron_right',
                        color: const Color(0xff6c6c70),
                        size: 16,
                      ),
                    ],
                  ),
                ),
                _appearanceEntry(
                  context,
                  s,
                  'Style',
                  StylePage(settings: s),
                  last: false,
                  trailing: _Detail(settings: s, text: s.style),
                ),
                _appearanceEntry(
                  context,
                  s,
                  'Font & Size',
                  FontSizePage(settings: s),
                  trailing: _Detail(settings: s, text: s.fontLabel),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Detail extends StatelessWidget {
  const _Detail({required this.settings, required this.text});
  final AppearanceSettings settings;
  final String text;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(text, style: nativeText(settings, color: settings.secondary)),
      const SizedBox(width: 8),
      NativePathIcon('chevron_right', color: settings.secondary, size: 16),
    ],
  );
}

Widget _appearanceEntry(
  BuildContext context,
  AppearanceSettings settings,
  String label,
  Widget page, {
  bool last = true,
  required Widget trailing,
}) => Semantics(
  button: true,
  child: GestureDetector(
    behavior: HitTestBehavior.opaque,
    onTap: () {
      if (label == 'Color') {
        showAppearanceColors(context, settings);
      } else {
        reviewPush(
          context,
          settings,
          label == 'Style'
              ? ExactStylePage(settings: settings)
              : ExactFontSizePage(settings: settings),
        );
      }
    },
    child: SizedBox(
      height: 52,
      child: Stack(
        children: [
          Positioned.fill(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                children: [
                  Expanded(child: Text(label, style: nativeText(settings))),
                  trailing,
                ],
              ),
            ),
          ),
          if (!last)
            Positioned(
              left: 16,
              right: 16,
              bottom: 0,
              height: 1,
              child: ColoredBox(color: settings.divider),
            ),
        ],
      ),
    ),
  ),
);

class _ThemeOption extends StatelessWidget {
  const _ThemeOption({required this.settings, required this.dark});
  final AppearanceSettings settings;
  final bool dark;
  @override
  Widget build(BuildContext context) {
    final selected = settings.dark == dark;
    return Semantics(
      label: dark ? 'Dark' : 'Light',
      button: true,
      selected: selected,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => settings.selectTheme(dark ? 1 : 0),
        child: SizedBox(
          width: 136,
          child: Column(
            children: [
              const SizedBox(height: 31),
              ExcludeSemantics(
                child: Image.asset(
                  'assets/review/preview-${settings.dark ? 'dark' : 'light'}-${dark ? 'dark' : 'light'}.png',
                  width: 70.5,
                  height: 132.13,
                  filterQuality: FilterQuality.low,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                dark ? 'Dark' : 'Light',
                style: nativeText(settings, size: 15),
              ),
              const SizedBox(height: 8),
              SelectionCircle(selected: selected, color: settings.accent),
            ],
          ),
        ),
      ),
    );
  }
}

class SelectionCircle extends StatelessWidget {
  const SelectionCircle({
    super.key,
    required this.selected,
    required this.color,
  });
  final bool selected;
  final Color color;
  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: 22,
    child: DecoratedBox(
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: selected ? color : Colors.transparent,
        border: selected
            ? null
            : Border.all(color: const Color(0xff6c6c70), width: 1.5),
      ),
      child: selected
          ? AkiraCheckMark(
              size: 16,
              color: color.computeLuminance() > .179
                  ? Colors.black
                  : Colors.white,
            )
          : null,
    ),
  );
}

class AppearanceSwitch extends StatelessWidget {
  const AppearanceSwitch({
    super.key,
    required this.value,
    required this.settings,
    required this.onChanged,
    this.label = 'Auto',
  });
  final bool value;
  final AppearanceSettings settings;
  final ValueChanged<bool> onChanged;
  final String label;
  @override
  Widget build(BuildContext context) => InteractiveToggle(
    settings: settings,
    label: label,
    value: value,
    onChanged: onChanged,
  );
}

class ColorPage extends StatefulWidget {
  const ColorPage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  State<ColorPage> createState() => _ColorPageState();
}

class _ColorPageState extends State<ColorPage> {
  late int pending = widget.settings.accentArgb;
  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return ReviewScaffold(
      settings: s,
      title: 'Color',
      action: _Done(
        settings: s,
        onPressed: () {
          s.change(() => s.accentArgb = pending);
          Navigator.of(context).pop();
        },
      ),
      body: (context) => ListView(
        padding: reviewInsets(context),
        children: [
          NativeGroup(
            model: s,
            child: Column(
              children: [
                for (var i = 0; i < reference.inotesPresetColors.length; i++)
                  NativeRow(
                    [
                      'Yellow',
                      'Orange',
                      'Green',
                      'Cyan',
                      'Blue',
                      'Indigo',
                      'Purple',
                      'Pink',
                      'Red',
                    ][i],
                    model: s,
                    last: false,
                    onTap: () => setState(
                      () =>
                          pending = reference.inotesPresetColors[i].toARGB32(),
                    ),
                    trailing: _ColorChoice(
                      color: reference.inotesPresetColors[i],
                      selected:
                          pending == reference.inotesPresetColors[i].toARGB32(),
                    ),
                  ),
                reviewEntry(
                  context,
                  s,
                  'Custom',
                  CustomColorPage(
                    settings: s,
                    onCommitted: (color) {
                      if (mounted) setState(() => pending = color);
                    },
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ColorChoice extends StatelessWidget {
  const _ColorChoice({required this.color, required this.selected});
  final Color color;
  final bool selected;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      if (selected)
        const Text(
          '\uf3fd',
          style: TextStyle(
            fontFamily: 'packages/inotes_recovered/InotesCupertino',
            fontSize: 20,
          ),
        ),
      const SizedBox(width: 12),
      SizedBox.square(
        dimension: 24,
        child: DecoratedBox(
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
      ),
    ],
  );
}

class _Done extends StatelessWidget {
  const _Done({required this.settings, required this.onPressed});
  final AppearanceSettings settings;
  final VoidCallback onPressed;
  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    label: 'Done',
    child: GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: onPressed,
      child: SizedBox(
        width: 52,
        height: 44,
        child: Center(
          child: Text(
            'Done',
            style: nativeText(
              settings,
              weight: FontWeight.w600,
              color: settings.accent,
            ),
          ),
        ),
      ),
    ),
  );
}

class CustomColorPage extends StatefulWidget {
  const CustomColorPage({
    super.key,
    required this.settings,
    required this.onCommitted,
  });
  final AppearanceSettings settings;
  final ValueChanged<int> onCommitted;
  @override
  State<CustomColorPage> createState() => _CustomColorPageState();
}

class _CustomColorPageState extends State<CustomColorPage> {
  String base = reference.inotesCustomPalettes.keys.first;
  late int pending = widget.settings.accentArgb;
  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    return ReviewScaffold(
      settings: s,
      title: 'Custom',
      action: _Done(
        settings: s,
        onPressed: () {
          s.change(() => s.accentArgb = pending);
          widget.onCommitted(pending);
          Navigator.of(context).pop();
        },
      ),
      body: (context) => ListView(
        padding: reviewInsets(context),
        children: [
          NativeGroup(
            model: s,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Wrap(
                spacing: 10,
                runSpacing: 10,
                children: [
                  for (final palette in reference.inotesCustomPalettes.entries)
                    _Swatch(
                      label: palette.key,
                      color: palette.value[500] ?? palette.value[200]!,
                      selected: base == palette.key,
                      onTap: () => setState(() => base = palette.key),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          NativeGroup(
            model: s,
            child: Column(
              children: [
                for (final shade
                    in reference.inotesCustomPalettes[base]!.entries)
                  NativeRow(
                    '${shade.key}',
                    model: s,
                    last:
                        shade.key ==
                        reference.inotesCustomPalettes[base]!.keys.last,
                    onTap: () =>
                        setState(() => pending = shade.value.toARGB32()),
                    trailing: _ColorChoice(
                      color: shade.value,
                      selected: pending == shade.value.toARGB32(),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Swatch extends StatelessWidget {
  const _Swatch({
    required this.label,
    required this.color,
    required this.selected,
    required this.onTap,
  });
  final String label;
  final Color color;
  final bool selected;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Semantics(
    label: label,
    button: true,
    selected: selected,
    child: GestureDetector(
      onTap: onTap,
      child: SizedBox.square(
        dimension: 40,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            border: selected ? Border.all(color: Colors.white, width: 3) : null,
          ),
        ),
      ),
    ),
  );
}

class StylePage extends StatelessWidget {
  const StylePage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: 'Style',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        NativeGroup(
          model: settings,
          child: Column(
            children: [
              for (final style in ['Classic', 'Glass'])
                NativeRow(
                  style,
                  model: settings,
                  last: style == 'Glass',
                  onTap: () => settings.change(() => settings.style = style),
                  trailing: SelectionCircle(
                    selected: settings.style == style,
                    color: settings.accent,
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 32),
        NativeGroup(
          model: settings,
          child: NativeRow(
            'Reduce Transparency',
            model: settings,
            trailing: NativeToggle(
              label: 'Reduce Transparency',
              model: settings,
              value: settings.reduceTransparency,
              onChanged: (v) =>
                  settings.change(() => settings.reduceTransparency = v),
            ),
          ),
        ),
        const SizedBox(height: 32),
        Text(
          'Vista previa',
          style: nativeText(settings, color: settings.secondary),
        ),
        const SizedBox(height: 12),
        NativeGroup(
          model: settings,
          child: NativeRow(
            'Welcome to iNotes',
            model: settings,
            trailing: NativePathIcon('square_pencil', color: settings.accent),
          ),
        ),
      ],
    ),
  );
}

class FontSizePage extends StatefulWidget {
  const FontSizePage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  State<FontSizePage> createState() => _FontSizePageState();
}

class _FontSizePageState extends State<FontSizePage> {
  AppearanceSettings get settings => widget.settings;
  OverlayEntry? _entry;
  void _closeMenu() {
    _entry?.remove();
    _entry = null;
  }

  @override
  void dispose() {
    _closeMenu();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: 'Font & Size',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        NativeGroup(
          model: settings,
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
                      style: nativeText(
                        settings,
                        size: 28 * settings.noteSize / 17,
                        weight: FontWeight.w700,
                        height: 1.3298321428571427,
                        note: true,
                      ).copyWith(fontFamily: settings.noteFamily),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Drag the slider below until this paragraph is easy to read. Titles, headings and lists follow the same size.',
                      style: nativeText(
                        settings,
                        size: settings.noteSize,
                        height: 26 / 17,
                        note: true,
                        color: settings.dark
                            ? const Color(0xffdcdcdc)
                            : const Color(0xff454545),
                      ).copyWith(fontFamily: settings.noteFamily),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 32),
        NativeGroup(
          model: settings,
          child: NativeRow(
            'Font',
            model: settings,
            trailing: _Detail(settings: settings, text: settings.fontLabel),
            onTap: () => _fontMenu(context),
          ),
        ),
        const SizedBox(height: 32),
        NativeGroup(
          model: settings,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
            child: RecoveredPointerExclusionRegion(
              behavior: HitTestBehavior.deferToChild,
              child: Row(
                children: [
                  Text(
                    'A',
                    style: nativeText(
                      settings,
                      size: 14,
                      weight: FontWeight.w500,
                      color: settings.secondary,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: NativeSizeSlider(model: settings)),
                  const SizedBox(width: 8),
                  Text(
                    'A',
                    style: nativeText(
                      settings,
                      size: 23,
                      weight: FontWeight.w500,
                      color: settings.secondary,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 9, 16, 0),
          child: Text(
            'Choose the font and size for your notes. It does not affect the rest of the application.',
            style: nativeText(
              settings,
              size: 13,
              height: 16 / 13,
              color: settings.secondary,
            ),
          ),
        ),
      ],
    ),
  );
  void _fontMenu(BuildContext context) {
    _closeMenu();
    late OverlayEntry entry;
    entry = OverlayEntry(
      builder: (overlayContext) => Stack(
        children: [
          Positioned(
            right: 16,
            top: MediaQuery.paddingOf(context).top + 340,
            width: 220,
            child: TapRegion(
              onTapOutside: (_) => _closeMenu(),
              child: Material(
                color: Colors.transparent,
                child: NativeGroup(
                  model: settings,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      for (final font in ['system', 'serif'])
                        NativeRow(
                          font == 'serif' ? 'Serif' : 'Default',
                          model: settings,
                          last: font == 'serif',
                          trailing: settings.font == font
                              ? SelectionCircle(
                                  selected: true,
                                  color: settings.accent,
                                )
                              : null,
                          onTap: () {
                            settings.change(() => settings.font = font);
                            _closeMenu();
                          },
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
    _entry = entry;
    Overlay.of(context, rootOverlay: true).insert(entry);
  }
}

class MotionPage extends StatelessWidget {
  const MotionPage({super.key, required this.settings, this.depth = 1});
  final AppearanceSettings settings;
  final int depth;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: 'Movimiento · $depth',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        Text(
          'Desliza hacia la derecha',
          style: nativeText(settings, size: 28, weight: FontWeight.w700),
        ),
        const SizedBox(height: 12),
        Text(
          'Empieza en el borde o en el centro. Suelta antes para cancelar o continúa para volver. Prueba también un desplazamiento vertical.',
          style: nativeText(settings, height: 1.43, color: settings.secondary),
        ),
        const SizedBox(height: 32),
        NativeGroup(
          model: settings,
          child: Column(
            children: [
              reviewEntry(
                context,
                settings,
                'Abrir otra pantalla',
                MotionPage(settings: settings, depth: depth + 1),
                last: false,
              ),
              reviewEntry(
                context,
                settings,
                'Probar menú superpuesto',
                ExactFontSizePage(settings: settings),
              ),
            ],
          ),
        ),
        for (var i = 0; i < 16; i++)
          Padding(
            padding: const EdgeInsets.only(top: 16),
            child: NativeGroup(
              model: settings,
              child: NativeRow('Fila ${i + 1}', model: settings),
            ),
          ),
      ],
    ),
  );
}

class TypographyPage extends StatelessWidget {
  const TypographyPage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: 'Tipografía',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        for (var i = 0; i < reference.inotesComponentTextSpecs.length; i++)
          Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: NativeGroup(
              model: settings,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${i + 1} · ${reference.inotesComponentTextSpecs[i].size} pt · ${reference.inotesComponentTextSpecs[i].weight}',
                      style: nativeText(
                        settings,
                        size: 13,
                        color: settings.secondary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      reference.inotesComponentTextSpecs[i].families.first
                              .contains('Material')
                          ? '\ue5ca'
                          : reference.inotesComponentTextSpecs[i].families.first
                                .contains('Cupertino')
                          ? '\uf3fd'
                          : 'Welcome to iNotes\nDiseño, lectura y movimiento.',
                      style: reference.inotesComponentTextSpecs[i]
                          .toTextStyle(color: settings.text)
                          .copyWith(inherit: false),
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    ),
  );
}

class OccurrencesPage extends StatelessWidget {
  const OccurrencesPage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: '117 componentes',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        for (final occurrence in reference.recoveredControlMeasurements.values)
          NativeGroup(
            model: settings,
            child: Padding(
              padding: const EdgeInsets.only(bottom: 1),
              child: NativeRow(
                occurrence.label,
                model: settings,
                onTap: () => reviewPush(
                  context,
                  settings,
                  OccurrenceDetail(settings: settings, occurrence: occurrence),
                ),
                trailing: NativePathIcon(
                  'chevron_right',
                  color: settings.secondary,
                  size: 16,
                ),
              ),
            ),
          ),
      ],
    ),
  );
}

class OccurrenceDetail extends StatelessWidget {
  const OccurrenceDetail({
    super.key,
    required this.settings,
    required this.occurrence,
  });
  final AppearanceSettings settings;
  final reference.RecoveredControlMeasurement occurrence;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: 'Medidas originales',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        Text(
          occurrence.label,
          style: nativeText(settings, size: occurrence.fontSize ?? 17),
        ),
        const SizedBox(height: 24),
        Text(
          'Zona táctil: ${occurrence.semanticPhysical}\n\nDibujo: ${occurrence.renderBoundsPhysical ?? 'No observado'}\n\nTipo de límite: ${occurrence.boundsKind}\n\nFuente solicitada: ${occurrence.requestedFontFamilies.join(', ')}\n\nCoordenadas de la captura a DPR 3.05. No son padding interno.',
          style: nativeText(
            settings,
            size: 15,
            height: 1.43,
            color: settings.secondary,
          ),
        ),
      ],
    ),
  );
}

class ReviewControlsPage extends StatefulWidget {
  const ReviewControlsPage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  State<ReviewControlsPage> createState() => _ReviewControlsPageState();
}

class _ReviewControlsPageState extends State<ReviewControlsPage> {
  bool auto = false, background = true;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: widget.settings,
    title: 'Controles',
    body: (context) => ListView(
      padding: reviewInsets(context),
      children: [
        NativeGroup(
          model: widget.settings,
          child: Column(
            children: [
              NativeRow(
                'Auto',
                model: widget.settings,
                last: false,
                trailing: InteractiveToggle(
                  settings: widget.settings,
                  label: 'Auto',
                  value: auto,
                  onChanged: (v) => setState(() => auto = v),
                ),
              ),
              NativeRow(
                'Background',
                model: widget.settings,
                last: false,
                trailing: InteractiveToggle(
                  settings: widget.settings,
                  label: 'Background',
                  value: background,
                  onChanged: (v) => setState(() => background = v),
                ),
              ),
              NativeRow(
                'Desactivado',
                model: widget.settings,
                trailing: InteractiveToggle(
                  settings: widget.settings,
                  label: 'Desactivado',
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
          model: widget.settings,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: InteractiveSizeSlider(settings: widget.settings),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          '${widget.settings.noteSize.toInt()} pt',
          style: nativeText(widget.settings, color: widget.settings.secondary),
        ),
      ],
    ),
  );
}

/// The page fades into its navigation layer without a hard horizontal cutoff.
class ProgressiveHeaderVeil extends StatelessWidget {
  const ProgressiveHeaderVeil({super.key, required this.color});
  final Color color;
  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: ClipRect(
      child: ShaderMask(
        blendMode: BlendMode.dstIn,
        shaderCallback: (bounds) => const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            Colors.white,
            Colors.white,
            Color(0xB3FFFFFF),
            Colors.transparent,
          ],
          stops: [0, .48, .73, 1],
        ).createShader(bounds),
        child: BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: 12, sigmaY: 12),
          child: ColoredBox(color: color.withValues(alpha: .98)),
        ),
      ),
    ),
  );
}
