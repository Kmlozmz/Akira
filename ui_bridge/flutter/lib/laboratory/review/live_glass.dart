import '../recovered/render_bindings.dart';
import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import '../recovered/components.dart';
import '../recovered/regional_capture_stage.dart' show RegionalCaptureMetrics;
import 'settings.dart';
import 'native_pane_motion.dart';
import 'pane_resources.dart';
import 'native_font_actor.dart';
import 'native_font_pose.dart';
import 'native_font_body.dart';
import 'native_foreground_filter.dart';
import 'frame_audit.dart';
import 'package:flutter/scheduler.dart';
import 'native_contact.dart';
import 'interactive_controls.dart' show PressGlow;
import 'package:inotes_recovered/inotes_recovered.dart' as runtime;

enum GlassPaneSurface { more, font, color }

class LivePaneScope extends InheritedWidget {
  const LivePaneScope({super.key, required this.owner, required super.child});
  final LiveGlassPaneState owner;
  static LiveGlassPaneState? of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<LivePaneScope>()?.owner;
  @override
  bool updateShouldNotify(LivePaneScope oldWidget) => true;
}

class ReviewCaptureScope extends InheritedWidget {
  const ReviewCaptureScope({
    super.key,
    required this.captureKey,
    required this.settings,
    required this.enabled,
    required super.child,
  });
  final GlobalKey<NativeGlassStageState> captureKey;
  final AppearanceSettings settings;
  final bool enabled;
  static ReviewCaptureScope? of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<ReviewCaptureScope>();
  @override
  bool updateShouldNotify(ReviewCaptureScope oldWidget) =>
      oldWidget.captureKey != captureKey;
}

/// A live pane using recovered lean optics and the original six-stage sampler order.
class LiveGlassProbe {
  int paints = 0, captureGeneration = 0;
  Map<String, int> toJson() => {
    'glass_paints': paints,
    'capture_generation': captureGeneration,
  };
}

class LiveGlassPane extends StatefulWidget {
  const LiveGlassPane({
    super.key,
    required this.source,
    required this.child,
    this.radius = 26,
    this.probe,
    this.composition,
    this.composes = true,
    this.surface = GlassPaneSurface.more,
    this.foregroundSeed,
    this.foregroundSeedRect,
    this.dock = false,
  });
  final ReviewCaptureScope? source;
  final Widget child;
  final double radius;
  final LiveGlassProbe? probe;
  final Animation<double>? composition;
  final bool composes;
  final bool dock;
  final GlassPaneSurface surface;
  final Widget? foregroundSeed;
  final Rect? foregroundSeedRect;
  @override
  State<LiveGlassPane> createState() => LiveGlassPaneState();
}

class LiveGlassPaneState extends State<LiveGlassPane>
    with TickerProviderStateMixin {
  final boxKey = GlobalKey();
  final signal = _PaneSignal();
  ui.FragmentShader? material, reduction, morph;
  ui.FragmentProgram? leanProgram;
  Map<String, dynamic>? paletteRecipes;
  runtime.RecoveredMipChain? buttonChain;
  ui.Image? buttonBase, buttonMip;
  Object? _buttonSignature;
  Map<String, dynamic>? morphFields;
  List<Map<String, dynamic>> motion = [];
  NativePaneMotion? nativeMotion;
  NativeFontActor? fontActor;
  Ticker? _fontTicker;
  Duration _fontPrevious = Duration.zero;
  Completer<void>? _fontClosing;
  bool dismissing = false;
  double _poseProgress = double.nan;
  bool _poseDismissing = false;
  Map<String, dynamic>? _pose;
  late final opening = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 460),
  )..addListener(_compositionChanged);
  double get progress =>
      fontActor?.dynamics.geometryProgress ??
      (widget.composes ? widget.composition?.value ?? opening.value : 1);
  void _compositionChanged() {
    if (mounted) {
      signal.invalidate();
      setState(() {});
    }
  }

  Future<void>? _dismissal;
  Future<void> dismiss() => _dismissal ??= _dismissOnce();

  Future<void> _dismissOnce() {
    final actor = fontActor;
    if (actor != null) {
      dismissing = true;
      actor.close();
      _pose = null;
      _fontClosing = Completer<void>();
      _runFontTicker();
      _compositionChanged();
      return _fontClosing!.future;
    }
    if (widget.surface == GlassPaneSurface.font && nativeMotion != null) {
      dismissing = true;
      opening.duration = nativeMotion!.duration(true);
      opening.value = 0;
      return opening.forward().orCancel;
    }
    return opening
        .animateBack(0, duration: const Duration(milliseconds: 220))
        .orCancel;
  }

  void reopen() {
    dismissing = false;
    _dismissal = null;
    final closing = _fontClosing;
    _fontClosing = null;
    if (closing != null && !closing.isCompleted) closing.complete();
    if (fontActor != null) {
      fontActor!.open();
      _pose = null;
      _runFontTicker();
      _compositionChanged();
    } else {
      opening.duration =
          nativeMotion?.duration(false) ?? const Duration(milliseconds: 460);
      opening.forward(from: 0);
    }
  }

  void startFontMotion() {
    if (!mounted || fontActor != null || nativeMotion == null) return;
    final origin = Offset(
      nativeMotion!.origin[0].toDouble(),
      nativeMotion!.origin[1].toDouble(),
    );
    final seed =
        widget.foregroundSeedRect ??
        const Rect.fromLTWH(179.172044, 114, 70.827962, 32);
    fontActor = NativeFontActor(
      menu: origin & const Size(250, 146),
      anchor: Rect.fromCenter(
        center: seed.center + origin,
        width: seed.width,
        height: 32,
      ),
      pixelRatio: MediaQuery.devicePixelRatioOf(context),
    )..open();
    opening.stop();
    _pose = null;
    _runFontTicker();
    _compositionChanged();
  }

  void _runFontTicker() {
    _fontTicker ??= createTicker(_tickFont);
    if (!_fontTicker!.isActive) {
      _fontPrevious = Duration.zero;
      _fontTicker!.start();
    }
  }

  void _tickFont(Duration elapsed) {
    final dt = (elapsed - _fontPrevious).inMicroseconds / 1e6;
    _fontPrevious = elapsed;
    if (dt <= 0) return;
    final actor = fontActor!;
    final active = actor.step(dt);
    for (final field in [0x4b, 0x63, 0x7f, 0x87]) {
      final spring = actor.dynamics.state(field);
      ReviewFrameAudit.sample(
        'font-${field.toRadixString(16)}',
        spring.position,
        spring.target,
        spring.velocity,
      );
    }
    _pose = null;
    if (actor.collapsed) {
      _fontTicker!.stop();
      if (_fontClosing?.isCompleted == false) _fontClosing!.complete();
    } else if (!active) {
      _fontTicker!.stop();
    }
    _compositionChanged();
  }

  Map<String, dynamic>? get nativePose {
    if (widget.surface != GlassPaneSurface.font || nativeMotion == null)
      return null;
    if (fontActor != null) {
      return _pose ??= NativeFontPose.build(fontActor!, nativeMotion!);
    }
    if (_poseProgress != progress ||
        _poseDismissing != dismissing ||
        _pose == null) {
      _poseProgress = progress;
      _poseDismissing = dismissing;
      _pose = nativeMotion!.sample(progress, dismissing);
    }
    return _pose;
  }

  bool containsBodyPoint(Offset global) {
    final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize) return false;
    return composedRect(box.size).contains(box.globalToLocal(global));
  }

  Map<String, Object?>? darkFields, lightFields;
  List<ui.Image> levels = [];
  int generation = -1;
  String? error;
  Listenable? _backdrop, _routeMotion;

  void _invalidateBackdrop() => signal.invalidate();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final next = widget.source?.captureKey.currentState?.repaint;
    if (!identical(_backdrop, next)) {
      _backdrop?.removeListener(_invalidateBackdrop);
      _backdrop = next;
      _backdrop?.addListener(_invalidateBackdrop);
    }
    final route = ModalRoute.of(context)?.animation;
    if (!identical(_routeMotion, route)) {
      _routeMotion?.removeListener(_invalidateBackdrop);
      _routeMotion = route;
      _routeMotion?.addListener(_invalidateBackdrop);
    }
  }

  @override
  void initState() {
    super.initState();
    if (widget.source?.enabled == true) {
      widget.composition?.addListener(_compositionChanged);
      _load();
    }
  }

  Future<void> _load() async {
    try {
      final resources = await PaneResources.load();
      final recipe = resources.recipe;
      final programs = resources.programs;
      final interactions = resources.interactions;
      final light = resources.light;
      final native = resources.motion;
      final palette = resources.palette;
      if (!mounted) return;
      setState(() {
        material = programs[0].fragmentShader();
        leanProgram = programs[0];
        buttonChain = runtime.RecoveredMipChain(programs[1]);
        paletteRecipes = palette;
        reduction = programs[1].fragmentShader();
        morph = programs[2].fragmentShader();
        morphFields = Map<String, dynamic>.from(
          interactions['morph_filter'] as Map,
        );
        motion = (interactions['menu_motion'] as List)
            .map((e) => Map<String, dynamic>.from(e as Map))
            .toList();
        darkFields = Map<String, Object?>.from(
          recipe.material['fields'] as Map,
        );
        lightFields = Map<String, Object?>.from(
          light['back']['uniforms'] as Map,
        );
        nativeMotion = native;
        if (widget.surface != GlassPaneSurface.more) {
          darkFields = widget.surface == GlassPaneSurface.font
              ? native.fontMaterial
              : native.colorMaterial;
        }
        if (widget.surface == GlassPaneSurface.font) {
          opening.duration = native.duration(false);
        }
      });
      if (widget.composition == null) {
        if (!widget.composes ||
            widget.source?.settings.reduceMotion == true ||
            MediaQuery.disableAnimationsOf(context))
          opening.value = 1;
        else if (widget.surface == GlassPaneSurface.font) {
          WidgetsBinding.instance.addPostFrameCallback(
            (_) => startFontMotion(),
          );
        } else
          opening.forward();
      }
    } catch (e) {
      if (mounted) setState(() => error = '$e');
    }
  }

  void prepareButtonBackdrop() {
    final host = widget.source?.captureKey.currentState;
    final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
    final stage =
        host?.stageKey.currentContext?.findRenderObject() as RenderBox?;
    final image = host?.capture.image;
    if (host == null ||
        box == null ||
        stage == null ||
        image == null ||
        buttonChain == null ||
        material == null)
      return;
    final origin = stage.globalToLocal(box.localToGlobal(Offset.zero));
    final ratio = MediaQuery.devicePixelRatioOf(context) / 2;
    final scale = box.size.width / 400;
    final width = (box.size.width * ratio).ceil();
    final height = ((104 * scale * ratio) / 16).ceil() * 16;
    final signature = (
      host.capture.generation,
      origin,
      box.size,
      width,
      height,
    );
    if (_buttonSignature == signature) return;
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder)..scale(ratio);
    final extent = Size(width / ratio, height / ratio);
    final rootRatio = image.width / stage.size.width;
    canvas.drawImageRect(
      image,
      Rect.fromLTWH(
        origin.dx * rootRatio,
        origin.dy * rootRatio,
        extent.width * rootRatio,
        extent.height * rootRatio,
      ),
      Offset.zero & extent,
      Paint()..filterQuality = ui.FilterQuality.low,
    );
    _LivePanePainter(this).paint(canvas, box.size);
    final picture = recorder.endRecording();
    final next = picture.toImageSync(width, height);
    picture.dispose();
    final previous = buttonBase;
    buttonBase = next;
    buttonMip = buttonChain!.render(next);
    if (kProfileMode) RegionalCaptureMetrics.headerMipChains++;
    _buttonSignature = signature;
    if (previous != null)
      WidgetsBinding.instance.addPostFrameCallback((_) => previous.dispose());
  }

  void prepare(NativeGlassStageState host) {
    final base = host.capture.image;
    final nextGeneration = host.capture.generation;
    if (base == null || reduction == null || generation == nextGeneration)
      return;
    final next = <ui.Image>[];
    var input = base;
    for (var i = 0; i < 6; i++) {
      final width = (input.width + 1) ~/ 2, height = (input.height + 1) ~/ 2;
      bindReviewFloats(reduction!, {
        'uSize': [input.width, input.height],
        'uSrc': [0, 0, input.width, input.height],
        'uDst': [0, 0, width, height],
      });
      reduction!.setImageSampler(
        reviewSamplerIndex(reduction!, 'uTex'),
        input,
        filterQuality: ui.FilterQuality.low,
      );
      final recorder = ui.PictureRecorder();
      ui.Canvas(recorder).drawRect(
        ui.Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()),
        ui.Paint()..shader = reduction,
      );
      final picture = recorder.endRecording();
      input = picture.toImageSync(width, height);
      picture.dispose();
      next.add(input);
    }
    final previous = levels;
    levels = next;
    generation = nextGeneration;
    if (kProfileMode) RegionalCaptureMetrics.paneMipChains++;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      for (final image in previous) {
        image.dispose();
      }
    });
  }

  @override
  void dispose() {
    widget.composition?.removeListener(_compositionChanged);
    opening.dispose();
    _fontTicker?.dispose();
    if (_fontClosing?.isCompleted == false) _fontClosing!.complete();
    morph?.dispose();
    _backdrop?.removeListener(_invalidateBackdrop);
    _routeMotion?.removeListener(_invalidateBackdrop);
    signal.dispose();
    material?.dispose();
    reduction?.dispose();
    for (final image in levels) {
      image.dispose();
    }
    buttonChain?.dispose();
    buttonBase?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.source?.settings;
    final enabled =
        widget.source?.enabled == true &&
        s != null &&
        s.style == 'Glass' &&
        !s.reduceTransparency;
    final fontForeground =
        enabled &&
        widget.surface == GlassPaneSurface.font &&
        widget.foregroundSeed != null &&
        nativePose != null;
    Widget content = error == null
        ? fontForeground
              ? _FontComposedForeground(owner: this, child: widget.child)
              : widget.composes
              ? _ComposedContent(paneState: this, child: widget.child)
              : widget.child
        : Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              widget.child,
              Text(
                'No se pudo activar el glass',
                style: TextStyle(color: s?.text),
              ),
            ],
          );
    content = ColoredBox(
      color: enabled && material != null
          ? Colors.transparent
          : s?.surface ?? const Color(0xff1c1c1e),
      child: content,
    );
    if (!fontForeground) {
      content = ClipRSuperellipse(
        borderRadius: BorderRadius.circular(widget.radius),
        child: content,
      );
    }
    final nativeFilter = nativePose?['morph'] is Map;
    if (enabled &&
        widget.composes &&
        morph != null &&
        widget.surface == GlassPaneSurface.font &&
        nativeFilter) {
      content = _MorphLayout(owner: this, child: content);
    }
    final surface = CustomPaint(
      key: boxKey,
      painter: enabled ? _LivePanePainter(this) : null,
      child: content,
    );
    Widget output = surface;
    if (enabled && material == null)
      output = Opacity(opacity: 0, child: surface);
    if (enabled &&
        widget.composes &&
        morph != null &&
        widget.surface != GlassPaneSurface.font &&
        progress < .9999) {
      output = _MorphLayout(owner: this, child: surface);
    }
    if (widget.surface == GlassPaneSurface.color) {
      output = PressGlow(
        showLight: false,
        contactTuning: const NativeContactTuning.pane(),
        coordinateScale: .96,
        child: output,
      );
    }
    output = LivePaneScope(owner: this, child: output);
    final source = widget.source;
    return source == null
        ? output
        : ReviewCaptureScope(
            captureKey: source.captureKey,
            settings: source.settings,
            enabled: source.enabled,
            child: output,
          );
  }

  Map<String, double> sample() {
    if (motion.isEmpty)
      return {
        'width': 250,
        'height': 431,
        'center_x': 259,
        'center_y': 274.1229553222656,
        'radius_signed': -32,
        'shadow_alpha': .18,
        'union_parameter': 0,
        'blur_scale': 1.22,
      };
    final time = progress >= .9999
        ? (motion.last['t_observed_ms'] as num).toDouble()
        : progress.clamp(0.0, 1.0) * 460;
    var right = motion.indexWhere((s) => (s['t_observed_ms'] as num) >= time);
    if (right <= 0) right = right < 0 ? motion.length - 1 : 1;
    final a = motion[right - 1], b = motion[right];
    final fraction =
        ((time - (a['t_observed_ms'] as num)) /
                ((b['t_observed_ms'] as num) - (a['t_observed_ms'] as num)))
            .clamp(0.0, 1.0);
    return {
      for (final key in [
        'width',
        'height',
        'center_x',
        'center_y',
        'radius_signed',
        'shadow_alpha',
        'union_parameter',
        'blur_scale',
      ])
        key:
            (a[key] as num).toDouble() +
            ((b[key] as num) - (a[key] as num)) * fraction,
    };
  }

  Rect composedRect(Size size) {
    final pose = nativePose;
    if (pose != null) {
      if (fontActor != null) {
        return (pose['body_rect'] as Rect?)?.shift(
              -Offset(
                nativeMotion!.origin[0].toDouble(),
                nativeMotion!.origin[1].toDouble(),
              ),
            ) ??
            Rect.zero;
      }
      final p = pose['lean']['uP'][0] as List;
      return Rect.fromCenter(
        center: Offset(
          (p[0] as num).toDouble() - nativeMotion!.origin[0],
          (p[1] as num).toDouble() - nativeMotion!.origin[1],
        ),
        width: (p[2] as num).toDouble() * 2,
        height: (p[3] as num).toDouble() * 2,
      );
    }
    if (progress >= .9999) return Offset.zero & size;
    final s = sample();
    final px = ((361 - s['center_x']!) / (361 - 259));
    final py =
        ((s['center_y']! - 80.6229476928711) /
        (274.1229553222656 - 80.6229476928711));
    final start = Offset(size.width - 22, math.max(22, size.height - 22));
    final center = Offset(
      start.dx + (size.width / 2 - start.dx) * px,
      start.dy + (size.height / 2 - start.dy) * py,
    );
    return Rect.fromCenter(
      center: center,
      width: math.max(.01, size.width * s['width']! / 250),
      height: math.max(.01, size.height * s['height']! / 431),
    );
  }

  Rect foregroundFilterBounds(Size size) {
    final pose = nativePose!;
    final bodyBlur = (pose['foreground']['menu_blur'] as num?)?.toDouble() ?? 0;
    var bounds = foregroundRect(size).inflate(3 * bodyBlur);
    final seed = pose['seed_center'] as Offset?;
    final seedRect = widget.foregroundSeedRect;
    if (seed != null && seedRect != null) {
      final origin = Offset(
        nativeMotion!.origin[0].toDouble(),
        nativeMotion!.origin[1].toDouble(),
      );
      final blur = (pose['foreground']['seed_blur'] as num).toDouble();
      bounds = bounds.expandToInclude(
        Rect.fromCenter(
          center: seed - origin,
          width: seedRect.width,
          height: seedRect.height,
        ).inflate(3 * blur),
      );
    }
    bounds = bounds.inflate(2);
    final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
    final origin = box?.localToGlobal(Offset.zero) ?? Offset.zero;
    final dpr = MediaQuery.devicePixelRatioOf(context);
    return Rect.fromLTRB(
      ((bounds.left + origin.dx) * dpr).floor() / dpr - origin.dx,
      ((bounds.top + origin.dy) * dpr).floor() / dpr - origin.dy,
      ((bounds.right + origin.dx) * dpr).ceil() / dpr - origin.dx,
      ((bounds.bottom + origin.dy) * dpr).ceil() / dpr - origin.dy,
    );
  }

  Rect foregroundRect(Size size) {
    final raw = nativePose?['raw_body_rect'] as Rect?;
    if (raw == null) return composedRect(size);
    return raw.shift(
      -Offset(
        nativeMotion!.origin[0].toDouble(),
        nativeMotion!.origin[1].toDouble(),
      ),
    );
  }

  void _bindMorph(Size size, [Rect? inputBounds]) {
    final render = boxKey.currentContext?.findRenderObject() as RenderBox?;
    final origin = render?.localToGlobal(Offset.zero) ?? Offset.zero;
    final captured = nativePose?['morph'];
    if (captured is Map) {
      final fields = copyRenderFields(captured);
      final delta =
          origin -
          Offset(
            nativeMotion!.origin[0].toDouble(),
            nativeMotion!.origin[1].toDouble(),
          );
      fields['uP'] = [
        for (final p in fields['uP'] as List)
          [(p[0] as num) + delta.dx, (p[1] as num) + delta.dy, p[2], p[3]],
      ];
      final anchor = fields['uA'] as List;
      fields['uA'] = [
        (anchor[0] as num) + delta.dx,
        (anchor[1] as num) + delta.dy,
        anchor[2],
        anchor[3],
      ];
      fields['uFrag'] = [
        origin.dx + (inputBounds?.left ?? -16),
        origin.dy + (inputBounds?.top ?? -16),
        1 / MediaQuery.devicePixelRatioOf(context),
        1 / 255,
      ];
      bindReviewFloats(morph!, fields, imageFilter: true);
      return;
    }
    final rect = composedRect(size).shift(origin);
    final s = sample(), fields = copyRenderFields(morphFields!);
    fields['uFrag'] = [
      origin.dx - 16,
      origin.dy - 16,
      1 /
          (widget.source?.captureKey.currentContext == null
              ? 3.05
              : MediaQuery.devicePixelRatioOf(
                  widget.source!.captureKey.currentContext!,
                )),
      1 / 255,
    ];
    fields['uP'] = [
      [rect.center.dx, rect.center.dy, rect.width / 2, rect.height / 2],
      ...List.generate(7, (_) => [0, 0, 0, 0]),
    ];
    fields['uRad'] = [
      -math.min(
        widget.radius * s['radius_signed']!.abs() / 32,
        math.min(rect.width, rect.height) / 2,
      ),
      0,
      0,
      0,
      0,
      0,
      0,
      0,
    ];
    fields['uShape'] = [1, s['union_parameter'], 1, 1 / 3.05];
    fields['uA'] = [
      origin.dx + size.width - 22,
      origin.dy + size.height - 22,
      9.828731536865234,
      10.102507591247559,
    ];
    final warp = (fields['uWarp'] as List).cast<num>();
    fields['uWarp'] = [
      warp[0] * (1 - progress.clamp(0.0, 1.0)),
      warp[1],
      warp[2],
      warp[3],
    ];
    bindReviewFloats(morph!, fields, imageFilter: true);
  }
}

class _FontComposedForeground extends StatelessWidget {
  const _FontComposedForeground({required this.owner, required this.child});
  final LiveGlassPaneState owner;
  final Widget child;
  @override
  Widget build(BuildContext context) {
    final pose = owner.nativePose!;
    final foreground = pose['foreground'] as Map;
    final optical = (pose['morph'] ?? pose['lean']) as Map;
    final seed = (optical['uP'] as List)[1] as List;
    final movingCenter = pose['seed_center'] as Offset?;
    final center = movingCenter != null
        ? movingCenter -
              Offset(
                owner.nativeMotion!.origin[0].toDouble(),
                owner.nativeMotion!.origin[1].toDouble(),
              )
        : owner.widget.foregroundSeedRect?.center ??
              Offset(
                (seed[0] as num).toDouble() - owner.nativeMotion!.origin[0],
                (seed[1] as num).toDouble() - owner.nativeMotion!.origin[1],
              );
    final blur = (foreground['seed_blur'] as num).toDouble();
    final trigger = NativeForegroundFilter(
      alpha: (foreground['seed_alpha'] as num).toDouble(),
      blur: blur,
      child: owner.widget.foregroundSeed!,
    );
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned.fill(
          child: IgnorePointer(
            child: ExcludeSemantics(
              child: Transform.translate(
                offset: center - const Offset(125, 73),
                child: Center(child: trigger),
              ),
            ),
          ),
        ),
        if (owner.fontActor != null)
          NativeFontBody(
            menuSize: owner.fontActor!.menu.size,
            bodyRect: owner.foregroundRect(owner.fontActor!.menu.size),
            contentHeight: (pose['body_content_height'] as num).toDouble(),
            radius: (pose['body_content_radius'] as num).toDouble(),
            alpha: (foreground['menu_alpha'] as num).toDouble(),
            blur: (foreground['menu_blur'] as num).toDouble(),
            child: child,
          )
        else
          Opacity(
            opacity: (foreground['menu_alpha'] as num).toDouble().clamp(0, 1),
            child: _ComposedContent(
              paneState: owner,
              child: ClipRSuperellipse(
                borderRadius: BorderRadius.circular(owner.widget.radius),
                child: child,
              ),
            ),
          ),
      ],
    );
  }
}

/// Track the lens center while retaining the foreground's readable scale.
/// Reference contact sheets show magnified/censored text, not miniature rows.
class _ComposedContent extends SingleChildRenderObjectWidget {
  const _ComposedContent({required this.paneState, required super.child});
  final LiveGlassPaneState paneState;
  @override
  RenderObject createRenderObject(BuildContext context) =>
      _ComposedContentRender(paneState);
  @override
  void updateRenderObject(
    BuildContext context,
    covariant _ComposedContentRender renderObject,
  ) {
    renderObject.paneState = paneState;
    renderObject.markNeedsPaint();
  }
}

class _ComposedContentRender extends RenderProxyBox {
  _ComposedContentRender(this.paneState);
  LiveGlassPaneState paneState;
  Matrix4 get composition {
    if (paneState.fontActor != null) {
      final origin = paneState.foregroundRect(size).topLeft;
      return Matrix4.translationValues(origin.dx, origin.dy, 0);
    }
    final rect = paneState.composedRect(size);
    final magnification = paneState.widget.surface == GlassPaneSurface.font
        ? 1.0
        : 1 + .35 * (1 - rect.width / size.width).clamp(0.0, 1.0);
    return Matrix4.identity()
      ..translateByDouble(
        rect.center.dx - size.width * magnification / 2,
        rect.center.dy - size.height * magnification / 2,
        0,
        1,
      )
      ..scaleByDouble(magnification, magnification, 1, 1);
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    context.pushTransform(needsCompositing, offset, composition, super.paint);
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) =>
      result.addWithPaintTransform(
        transform: composition,
        position: position,
        hitTest: (result, transformed) =>
            super.hitTestChildren(result, position: transformed),
      );
  @override
  void applyPaintTransform(RenderBox child, Matrix4 transform) =>
      transform.multiply(composition);
}

class _MorphLayout extends SingleChildRenderObjectWidget {
  const _MorphLayout({required this.owner, required super.child});
  final LiveGlassPaneState owner;
  @override
  RenderObject createRenderObject(BuildContext context) => _MorphRender(owner);
  @override
  void updateRenderObject(
    BuildContext context,
    covariant _MorphRender renderObject,
  ) => renderObject.paneState = owner;
}

class _MorphRender extends RenderProxyBox {
  _MorphRender(this.paneState);
  LiveGlassPaneState paneState;
  @override
  bool get alwaysNeedsCompositing => true;
  @override
  void paint(PaintingContext context, Offset offset) {
    final foreground = paneState.widget.surface == GlassPaneSurface.font;
    final bounds = foreground ? paneState.foregroundFilterBounds(size) : null;
    paneState._bindMorph(size, bounds);
    context.pushLayer(
      ImageFilterLayer(imageFilter: ui.ImageFilter.shader(paneState.morph!)),
      (childContext, childOffset) {
        if (bounds != null) {
          // The filter removes this 1/255 alpha bias through uFrag.w. It fixes
          // the input extent even while translucent glyphs move independently.
          childContext.canvas.drawRect(
            bounds.shift(childOffset),
            Paint()
              ..color = const Color(0x01000000)
              ..isAntiAlias = false,
          );
        }
        super.paint(childContext, childOffset);
      },
      offset,
      childPaintBounds: bounds?.shift(offset),
    );
  }
}

class _PaneSignal extends ChangeNotifier {
  void invalidate() => notifyListeners();
}

class _LivePanePainter extends CustomPainter {
  _LivePanePainter(this.owner) : super(repaint: owner.signal);
  final LiveGlassPaneState owner;
  @override
  void paint(Canvas canvas, Size size) {
    final source = owner.widget.source, shader = owner.material;
    final host = source?.captureKey.currentState;
    if (host == null ||
        shader == null ||
        owner.darkFields == null ||
        size.isEmpty)
      return;
    owner.prepare(host);
    if (owner.levels.length != 6) return;
    final probe = owner.widget.probe;
    if (probe != null) {
      probe.paints++;
      probe.captureGeneration = host.capture.generation;
    }
    final stage =
        host.stageKey.currentContext?.findRenderObject() as RenderBox?;
    final box = owner.boxKey.currentContext?.findRenderObject() as RenderBox?;
    if (stage == null || box == null) return;
    final origin = stage.globalToLocal(box.localToGlobal(Offset.zero));
    final fields = copyRenderFields(
      (source!.settings.dark ? owner.darkFields : owner.lightFields)!,
    );
    fields['uBd'] = [
      origin.dx,
      origin.dy,
      1 / stage.size.width,
      1 / stage.size.height,
    ];
    fields['uFrag'] = [0, 0, 1, 1];
    final shape = owner.composedRect(size), pose = owner.sample();
    final nativeLean = owner.nativePose?['lean'];
    if (nativeLean is Map) {
      fields.addAll(Map<String, dynamic>.from(nativeLean));
      fields['uP'] = [
        for (final p in nativeLean['uP'] as List)
          [
            (p[0] as num) - owner.nativeMotion!.origin[0],
            (p[1] as num) - owner.nativeMotion!.origin[1],
            p[2],
            p[3],
          ],
      ];
      (fields['uShape'] as List)[3] =
          1 / MediaQuery.devicePixelRatioOf(owner.context);
    } else {
      fields['uP'] = [
        [shape.center.dx, shape.center.dy, shape.width / 2, shape.height / 2],
        ...List.generate(7, (_) => [0, 0, 0, 0]),
      ];
      fields['uRad'] = [
        -math.min(
          owner.widget.radius * pose['radius_signed']!.abs() / 32,
          math.min(shape.width, shape.height) / 2,
        ),
        0,
        0,
        0,
        0,
        0,
        0,
        0,
      ];
      (fields['uShape'] as List)[0] = 1;
      if (owner.widget.surface == GlassPaneSurface.more) {
        (fields['uShape'] as List)[1] = pose['union_parameter'];
        (fields['uShadow'] as List)[0] = pose['shadow_alpha'];
        if (fields['uBlurP'] is List)
          (fields['uBlurP'] as List)[0] = pose['blur_scale'];
      }
    }
    final tap = List<num>.from(fields['uTap'] as List), level = owner.levels[2];
    fields['uTap'] = [.5 / level.width, .5 / level.height, tap[2], tap[3]];
    source.settings.configureMaterial(fields, dock: owner.widget.dock, readable: true);
    bindReviewFloats(shader, fields);
    for (final binding in {
      'uLevel': 2,
      'uSharp': 0,
      'uNext': 1,
      'uBleedL': 5,
    }.entries) {
      shader.setImageSampler(
        reviewSamplerIndex(shader, binding.key),
        owner.levels[binding.value],
        filterQuality: ui.FilterQuality.low,
      );
    }
    canvas.drawRect(
      Rect.fromLTRB(-16, -16, size.width + 16, size.height + 16),
      Paint()..shader = shader,
    );
  }

  @override
  bool hitTest(Offset position) => false;
  @override
  bool shouldRepaint(_LivePanePainter old) => true;
}
