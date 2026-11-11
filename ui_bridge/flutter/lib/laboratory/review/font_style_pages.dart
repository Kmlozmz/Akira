import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../recovered/components.dart';
import '../recovered/motion/recovered_pointer_exclusions.dart';
import 'settings.dart';
import 'laboratory.dart';
import 'live_glass.dart';
import 'interactive_controls.dart';
import 'font_glass_menu.dart';
import 'font_menu_trigger.dart';

class ExactStylePage extends StatelessWidget {
  const ExactStylePage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: 'Style',
    body: (context) => ListView(
      padding: reviewInsets(context, top: 80),
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 16, bottom: 10),
          child: Text(
            'Style',
            style: nativeText(
              settings,
              weight: FontWeight.w600,
              color: settings.secondary,
            ),
          ),
        ),
        NativeGroup(
          model: settings,
          radius: settings.style == 'Classic' ? 10 : 26,
          child: SizedBox(
            height: settings.style == 'Classic' ? 224 : 252,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                for (final style in ['Classic', 'Glass'])
                  Semantics(
                    button: true,
                    label: style,
                    selected: settings.style == style,
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: () =>
                          settings.change(() => settings.style = style),
                      child: SizedBox(
                        width: 136,
                        child: Column(
                          children: [
                            SizedBox(
                              height: settings.style == 'Classic' ? 16 : 31,
                            ),
                            ExcludeSemantics(
                              child: Image.asset(
                                'assets/review/style-${style.toLowerCase()}.png',
                                width: 70.5,
                                height: 132.13,
                              ),
                            ),
                            const SizedBox(height: 10),
                            Text(style, style: nativeText(settings, size: 15)),
                            const SizedBox(height: 8),
                            SelectionCircle(
                              selected: settings.style == style,
                              color: settings.accent,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        if (settings.style == 'Glass') ...[
          const SizedBox(height: 30),
          NativeGroup(
            model: settings,
            radius: settings.style == 'Classic' ? 10 : 26,
            child: SizedBox(
              height: 52,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Reduce Transparency',
                        style: nativeText(settings),
                      ),
                    ),
                    AppearanceSwitch(
                      label: 'Reduce Transparency',
                      value: settings.reduceTransparency,
                      settings: settings,
                      onChanged: (v) => settings.change(
                        () => settings.reduceTransparency = v,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text(
              settings.reduceTransparency
                  ? 'Glass and blur effects are off.'
                  : 'Glass and blur effects are on. If you notice slowdowns, you can turn these effects off.',
              style: nativeText(
                settings,
                size: 13,
                height: 16 / 13,
                color: settings.secondary,
              ),
            ),
          ),
        ],
      ],
    ),
  );
}

class ExactFontSizePage extends StatefulWidget {
  const ExactFontSizePage({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  State<ExactFontSizePage> createState() => _ExactFontSizePageState();
}

class _ExactFontSizePageState extends State<ExactFontSizePage> {
  final rowKey = GlobalKey();
  final triggerKey = GlobalKey();
  var menuGlassKey = GlobalKey<LiveGlassPaneState>();
  var menuContactKey = GlobalKey<FontGlassMenuState>();
  OverlayEntry? menu;
  int menuEpoch = 0;
  bool disposing = false;
  ReviewCaptureScope? menuSource;
  AppearanceSettings get settings => widget.settings;
  bool _acceptsFontContact(Offset global) {
    final box = triggerKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize) return false;
    return Rect.fromCenter(
      center: box.localToGlobal(box.size.center(Offset.zero)),
      width: box.size.width,
      height: 32,
    ).contains(global);
  }

  void _menuChanged() {
    if (mounted && !disposing) {
      setState(() {});
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !disposing)
          menuSource?.captureKey.currentState?.repaint.request();
      });
    }
  }

  void closeMenu({bool animate = false}) {
    final entry = menu;
    if (entry == null) return;
    final epoch = menuEpoch;
    final pane = menuGlassKey.currentState;
    if (animate &&
        pane != null &&
        settings.style == 'Glass' &&
        !settings.reduceMotion) {
      pane
          .dismiss()
          .then((_) {
            if (menu == entry && menuEpoch == epoch) {
              entry.remove();
              menu = null;
              _menuChanged();
            }
          })
          .catchError((Object _) {});
    } else {
      menu = null;
      entry.remove();
      _menuChanged();
    }
  }

  @override
  void dispose() {
    disposing = true;
    closeMenu();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ReviewScaffold(
    settings: settings,
    title: settings.ui('Font & Size'),
    body: (context) => Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        MediaQuery.paddingOf(context).top + 88,
        16,
        MediaQuery.paddingOf(context).bottom + 24,
      ),
      child: LayoutBuilder(
        builder: (context, constraints) => SingleChildScrollView(
          child: SizedBox(
            height: math.max(constraints.maxHeight, 605),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                NativeGroup(
                  model: settings,
                  child: SizedBox(
                    height: 280,
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
                      child: settings.musicPreviewBuilder?.call(context) ?? Column(
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
                              color: settings.dark
                                  ? const Color(0xffdcdcdc)
                                  : const Color(0xff454545),
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
                const Spacer(),
                const SizedBox(height: 32),
                NativeGroup(
                  key: rowKey,
                  model: settings,
                  child: FontMenuTrigger(
                    acceptsPointer: _acceptsFontContact,
                    onOpen: (pointer) =>
                        openFontMenu(context, initialPointer: pointer),
                    child: NativeRow(
                      settings.ui('Font'),
                      model: settings,
                      trailing: Opacity(
                        key: const ValueKey('font-menu-selector'),
                        opacity: menu == null ? 1 : 0,
                        child: Row(
                          key: triggerKey,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              settings.fontLabel,
                              style: nativeText(
                                settings,
                                color: settings.secondary,
                              ),
                            ),
                            const SizedBox(width: 6),
                            NativePathIcon(
                              'chevron_up_down',
                              color: settings.secondary,
                              size: 16,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 30),
                NativeGroup(
                  model: settings,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
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
                          Expanded(
                            child: InteractiveSizeSlider(settings: settings),
                          ),
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
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                  child: Text(
                    settings.ui('Choose the font and text size for your music library and player.'),
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
          ),
        ),
      ),
    ),
  );
  void openFontMenu(BuildContext context, {int? initialPointer}) {
    if (menu != null && menuGlassKey.currentState?.dismissing == true) {
      menuEpoch++;
      menuContactKey.currentState?.reopen(initialPointer);
      _menuChanged();
      return;
    }
    closeMenu();
    menuEpoch++;
    final paneKey = GlobalKey<LiveGlassPaneState>();
    final contactKey = GlobalKey<FontGlassMenuState>();
    menuGlassKey = paneKey;
    menuContactKey = contactKey;
    final source = ReviewCaptureScope.of(context);
    menuSource = source;
    final box = rowKey.currentContext!.findRenderObject()! as RenderBox;
    final bottom = box.localToGlobal(Offset(0, box.size.height)).dy - 10;
    final top = (bottom - 146).clamp(80.0, double.infinity);
    final origin = Offset(MediaQuery.sizeOf(context).width - 32 - 250, top);
    final trigger = triggerKey.currentContext!.findRenderObject()! as RenderBox;
    final seedRect = (trigger.localToGlobal(Offset.zero) & trigger.size).shift(
      -origin,
    );
    late final OverlayEntry entry;
    entry = OverlayEntry(
      builder: (_) => Positioned(
        right: 32,
        top: top,
        width: 250,
        child: FontGlassMenu(
          key: contactKey,
          settings: settings,
          source: source,
          paneKey: paneKey,
          foregroundSeedRect: seedRect,
          initialPointer: initialPointer,
          onDismiss: () {
            if (menu == entry) closeMenu(animate: true);
          },
          onSelect: (font) async {
            if (menu != entry || paneKey.currentState?.dismissing == true)
              return;
            closeMenu(animate: true);
            if (font == 'custom') {
              await settings.pickCustomFont();
            } else if (settings.font != font) {
              settings.change(() => settings.font = font);
            }
          },
        ),
      ),
    );
    menu = entry;
    Overlay.of(context, rootOverlay: true).insert(entry);
    _menuChanged();
  }
}
