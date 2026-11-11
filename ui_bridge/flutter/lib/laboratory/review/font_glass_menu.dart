import 'dart:async';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/cupertino.dart' show CupertinoIcons;
import '../recovered/components.dart';
import '../recovered/motion/recovered_pointer_exclusions.dart';
import 'settings.dart';
import 'live_glass.dart';
import 'native_font_light.dart';
import 'interaction_motion.dart';

class FontGlassMenu extends StatefulWidget {
  const FontGlassMenu({
    super.key,
    required this.settings,
    required this.source,
    required this.paneKey,
    required this.onSelect,
    required this.onDismiss,
    this.initialPointer,
    this.foregroundSeedRect,
  });
  final AppearanceSettings settings;
  final ReviewCaptureScope? source;
  final GlobalKey<LiveGlassPaneState> paneKey;
  final ValueChanged<String> onSelect;
  final VoidCallback onDismiss;
  final int? initialPointer;
  final Rect? foregroundSeedRect;
  @override
  State<FontGlassMenu> createState() => FontGlassMenuState();
}

class FontGlassMenuState extends State<FontGlassMenu>
    with TickerProviderStateMixin {
  final boundsKey = GlobalKey();
  late final selection = GlassMotionController.unbounded(
    vsync: this,
    value: 10,
    epsilon: .01,
  )..addListener(_refresh);
  late final pressure = GlassMotionController.unbounded(
    vsync: this,
    epsilon: .001,
  )..addListener(_refresh);
  late final light = GlassMotionController.unbounded(vsync: this, epsilon: .001)
    ..addListener(_refresh);
  late final reach = GlassMotionController.unbounded(
    vsync: this,
    value: 1,
    epsilon: .001,
  )..addListener(_refresh);
  late final x = GlassMotionController.unbounded(vsync: this, epsilon: .01)
    ..addListener(_refresh);
  late final y = GlassMotionController.unbounded(vsync: this, epsilon: .01)
    ..addListener(_refresh);
  NativeFontLightProfile? profile;
  NativeFontContact? nativeContact;
  int? pointer, highlighted;
  final physicalDowns = <int, Duration>{};
  PointerDownEvent? outsideDown;
  bool closing = false;
  Offset contact = Offset.zero;
  static const fonts = ['system', 'serif', 'custom'];
  bool get reduced =>
      widget.settings.reduceMotion || MediaQuery.disableAnimationsOf(context);
  void _refresh() {
    if (mounted) setState(() {});
  }

  void reopen(int? initialPointer) {
    pointer = initialPointer;
    outsideDown = null;
    highlighted = null;
    nativeContact = null;
    _spring(pressure, 0, .3);
    widget.paneKey.currentState?.reopen();
    setState(() => closing = false);
  }

  @override
  void initState() {
    super.initState();
    pointer = widget.initialPointer;
    NativeFontLightProfile.loaded.then((value) {
      if (mounted) setState(() => profile = value);
    });
    GestureBinding.instance.pointerRouter.addGlobalRoute(_pointer);
  }

  Rect? get bounds {
    final box = boundsKey.currentContext?.findRenderObject() as RenderBox?;
    return box == null || !box.hasSize
        ? null
        : MatrixUtils.transformRect(
            box.getTransformTo(null),
            Offset.zero & box.size,
          );
  }

  bool _contains(Offset global) =>
      bounds?.contains(global) == true &&
      widget.paneKey.currentState?.containsBodyPoint(global) == true;

  void _spring(
    GlassMotionController controller,
    double target,
    double period,
  ) => controller.driveSpring(
    target,
    period: period,
    damping: 1,
    immediate: reduced,
  );

  void _over(Offset global, {required bool held}) {
    final rect = bounds;
    if (rect == null) return;
    final box = boundsKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return;
    contact = box.globalToLocal(global);
    if (held) {
      if (nativeContact == null) {
        nativeContact = NativeFontContact(contact);
        x.value = contact.dx;
        y.value = contact.dy;
        reach.value = 1;
        light.value = 0;
      } else {
        nativeContact!.move(contact);
        _spring(x, contact.dx, .15);
        _spring(y, contact.dy, .15);
      }
      _spring(
        light,
        nativeContact!.intensity,
        nativeContact!.travelled ? .5 : .1,
      );
      _spring(reach, nativeContact!.reach, .5);
    }
    final index = ((contact.dy - 10) / 42).floor();
    final next = _contains(global) && index >= 0 && index < 3 ? index : null;
    if (next != highlighted) {
      highlighted = next;
      if (next != null) {
        final top = 10.0 + 42 * next;
        if (pressure.value < .05) selection.value = top;
        _spring(selection, top, .12);
      }
    }
    _spring(pressure, next == null ? 0 : 1, .3);
    _refresh();
  }

  void _pointer(PointerEvent event) {
    if (event is PointerDownEvent) {
      physicalDowns[event.pointer] = event.timeStamp;
    } else if (event is PointerUpEvent || event is PointerCancelEvent) {
      physicalDowns.remove(event.pointer);
      if (event is PointerCancelEvent &&
          outsideDown?.pointer == event.pointer) {
        outsideDown = null;
      }
    }
    if (closing) return;
    if (event is PointerMoveEvent &&
        outsideDown?.pointer == event.pointer &&
        (event.position - outsideDown!.position).distance > kTouchSlop) {
      outsideDown = null;
    }
    if (event is PointerDownEvent &&
        pointer == null &&
        _contains(event.position)) {
      pointer = event.pointer;
      _over(event.position, held: true);
    } else if (event.pointer == pointer) {
      if (event is PointerMoveEvent) _over(event.position, held: true);
      if (event is PointerUpEvent || event is PointerCancelEvent) {
        if (event is PointerUpEvent) _over(event.position, held: false);
        final chosen = event is PointerUpEvent && _contains(event.position)
            ? highlighted
            : null;
        pointer = null;
        nativeContact = null;
        _spring(light, 0, .5);
        _spring(reach, 4, .5);
        if (chosen != null) {
          _select(fonts[chosen]);
        } else {
          highlighted = null;
          _spring(pressure, 0, .3);
        }
      }
    }
  }

  void _select(String font) {
    if (!closing) {
      setState(() => closing = true);
      widget.onSelect(font);
    }
  }

  void _dismiss() {
    if (closing) return;
    setState(() => closing = true);
    widget.onDismiss();
  }

  void _outsideStart(PointerDownEvent event) {
    if (closing || event.pointer == pointer) return;
    outsideDown = event;
    scheduleMicrotask(() {
      // TapRegion's semantic taps synthesize DOWN without routing a physical
      // contact or a subsequent UP. Keep that dismissal available to readers.
      if (mounted &&
          !closing &&
          identical(outsideDown, event) &&
          physicalDowns[event.pointer] != event.timeStamp) {
        outsideDown = null;
        _dismiss();
      }
    });
  }

  void _outsideEnd(PointerUpEvent event) {
    if (outsideDown?.pointer != event.pointer) return;
    outsideDown = null;
    _dismiss();
  }

  @override
  void dispose() {
    GestureBinding.instance.pointerRouter.removeGlobalRoute(_pointer);
    for (final controller in [selection, pressure, light, reach, x, y]) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    final menu = LiveGlassPane(
      key: widget.paneKey,
      source: widget.source,
      surface: GlassPaneSurface.font,
      radius: 32,
      foregroundSeedRect: widget.foregroundSeedRect,
      foregroundSeed: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(s.fontLabel, style: nativeText(s, color: s.secondary)),
          const SizedBox(width: 6),
          NativePathIcon('chevron_up_down', color: s.secondary, size: 16),
        ],
      ),
      child: MouseRegion(
        onHover: (e) => _over(e.position, held: false),
        onExit: (_) {
          if (pointer == null) {
            highlighted = null;
            _spring(pressure, 0, .3);
          }
        },
        child: RecoveredPointerExclusionRegion(
          behavior: HitTestBehavior.opaque,
          child: GestureDetector(
            key: boundsKey,
            behavior: HitTestBehavior.opaque,
            onTap: () {},
            onPanUpdate: (_) {},
            child: CustomPaint(
              painter: _SelectionLight(
                profile,
                selection.value,
                pressure.value.clamp(0, 1),
                Offset(x.value, y.value),
                light.value,
                reach.value,
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (var i = 0; i < fonts.length; i++)
                      Semantics(
                        button: true,
                        selected: highlighted == i,
                        onTap: () => _select(fonts[i]),
                        child: SizedBox(
                          height: 42,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 20),
                            child: Row(
                              children: [
                                SizedBox(
                                  width: 22,
                                  child: s.font == fonts[i]
                                      ? Icon(CupertinoIcons.check_mark, size: 15, color: s.text)
                                      : null,
                                ),
                                Expanded(
                                  child: Text(
                                    s.ui(['Default', 'Serif', 'Custom'][i]),
                                    maxLines: 1, overflow: TextOverflow.ellipsis,
                                    style: nativeText(s).copyWith(
                                      fontFamily: i == 1
                                          ? 'serif'
                                          : i == 0 ? s.defaultInterfaceFont : s.interfaceFont,
                                    ),
                                  ),
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
          ),
        ),
      ),
    );
    return TapRegion(
      onTapOutside: _outsideStart,
      onTapUpOutside: _outsideEnd,
      child: IgnorePointer(
        ignoring: closing,
        child: Material(color: Colors.transparent, child: menu),
      ),
    );
  }
}

class _SelectionLight extends CustomPainter {
  _SelectionLight(
    this.profile,
    this.rowTop,
    this.amount,
    this.contact,
    this.intensity,
    this.reach,
  );
  final NativeFontLightProfile? profile;
  final double rowTop, amount, intensity, reach;
  final Offset contact;
  @override
  void paint(Canvas canvas, Size size) => profile?.paint(
    canvas,
    size,
    rowTop: rowTop,
    selectionAlpha: amount,
    center: contact,
    intensity: intensity,
    reach: reach,
  );
  @override
  bool shouldRepaint(_SelectionLight old) => true;
}
