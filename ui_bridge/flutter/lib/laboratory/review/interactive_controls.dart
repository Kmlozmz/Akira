import '../../design_system/component_specs.dart';
import '../recovered/render_bindings.dart';
import 'dart:convert';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/scheduler.dart';
import 'package:inotes_recovered/inotes_recovered.dart';
import '../recovered/components.dart'
    show recoveredNoteSizes, NativeGlassButton;
import '../recovered/motion/recovered_pointer_exclusions.dart';
import 'live_glass.dart';
import 'settings.dart';
import 'interaction_motion.dart';
import 'frame_audit.dart';
import 'native_deformation.dart';
import 'native_contact.dart';
import 'native_switch_track.dart';

class AkiraSwitch extends StatelessWidget {
  const AkiraSwitch({
    super.key,
    required this.settings,
    required this.label,
    required this.value,
    required this.onChanged,
    this.enabled = true,
  });
  final AppearanceSettings settings;
  final String label;
  final bool value, enabled;
  final ValueChanged<bool> onChanged;
  @override
  Widget build(BuildContext context) {
    if (settings.style != 'Glass') {
      final inactiveTrack=Color(settings.dark?AkiraClassicControlSpec.inactiveTrackDarkArgb:AkiraClassicControlSpec.inactiveTrackLightArgb);
      final inactiveThumb=Color(settings.dark?AkiraClassicControlSpec.inactiveThumbDarkArgb:AkiraClassicControlSpec.inactiveThumbLightArgb);
      return Semantics(label: label, child: Switch(
        value: value,
        onChanged: enabled ? (next) {
          HapticFeedback.selectionClick();
          onChanged(next);
        } : null,
        thumbIcon: WidgetStateProperty.resolveWith((states) => Icon(
          states.contains(WidgetState.selected) ? Icons.check_rounded : Icons.close_rounded,
          size: AkiraClassicControlSpec.iconSize, color: states.contains(WidgetState.selected)
              ? settings.accent : inactiveTrack)),
        trackColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected) ? settings.accent : inactiveTrack),
        thumbColor: WidgetStateProperty.resolveWith((states) =>
          states.contains(WidgetState.selected)
              ? (settings.accent.computeLuminance() > AkiraClassicControlSpec.contrastThreshold ? Colors.black : Colors.white)
              : inactiveThumb),
        trackOutlineColor: const WidgetStatePropertyAll(Colors.transparent),
      ));
    }
    return _ThumbControl(settings: settings, label: label, value: value,
      onChanged: onChanged, enabled: enabled, slider: false);
  }
}

class AkiraSizeSlider extends StatelessWidget {
  const AkiraSizeSlider({super.key, required this.settings});
  final AppearanceSettings settings;
  @override
  Widget build(BuildContext context) =>
      _ThumbControl(settings: settings, label: 'Tamaño de nota', slider: true);
}

class AkiraSlider extends StatelessWidget {
  const AkiraSlider({super.key, required this.settings, required this.label,
    required this.value, required this.onChanged});
  final AppearanceSettings settings;
  final String label;
  final double value;
  final ValueChanged<double> onChanged;
  @override
  Widget build(BuildContext context) => _ThumbControl(settings: settings,
    label: label, slider: true, scalarValue: value, onScalar: onChanged);
}

class _ThumbControl extends StatefulWidget {
  const _ThumbControl({
    required this.settings,
    required this.label,
    required this.slider,
    this.value = false,
    this.enabled = true,
    this.onChanged,
    this.scalarValue,
    this.onScalar,
  });
  final AppearanceSettings settings;
  final String label;
  final bool slider, value, enabled;
  final ValueChanged<bool>? onChanged;
  final double? scalarValue;
  final ValueChanged<double>? onScalar;
  @override
  State<_ThumbControl> createState() => _ThumbControlState();
}

class _ThumbControlState extends State<_ThumbControl>
    with TickerProviderStateMixin {
  final boxKey = GlobalKey();
  late final pressure = GlassMotionController.unbounded(vsync: this, value: 0)
    ..addListener(_refresh);
  late final position = GlassMotionController.unbounded(
    vsync: this,
    value: _restPosition,
    epsilon: widget.slider ? .00001 : .001 / 22,
  )..addListener(_refresh);
  late final reveal = GlassMotionController.unbounded(
    vsync: this,
    value: widget.value ? 0 : 567,
    epsilon: .01,
  )..addListener(_refresh);
  late bool revealOn = widget.value;

  void _revealTo(bool value) {
    if (widget.slider || revealOn == value) return;
    revealOn = value;
    reveal.driveSpring(
      value ? 0 : 567,
      period: value ? .68 : .4,
      damping: 1,
      immediate: widget.settings.reduceMotion,
    );
  }

  double get _restPosition => widget.slider
      ? widget.scalarValue ?? widget.settings.noteSizeIndex / 6
      : widget.value
      ? 1
      : 0;
  ui.FragmentShader? lens;
  Map<String, dynamic>? optical;
  ReviewCaptureScope? source;
  OverlayEntry? overlay;
  int? pointer;
  bool loading = false, dragged = false;
  Offset start = Offset.zero;
  double startPosition = 0;
  double targetPosition = 0;
  int? lastHapticBucket;
  final hapticClock = Stopwatch()..start();
  int lastHapticTime = -100;
  final deformation = NativeGlassDeformation();
  late final Ticker deformationTicker = createTicker(_advanceDeformation);
  Duration deformationPrevious = Duration.zero;
  double deformationTime = 0;
  bool get deforms =>
      widget.settings.style == 'Glass' && !widget.settings.reduceMotion;
  double get scaleX => deforms ? deformation.x.position : 1;
  double get scaleY => deforms ? deformation.y.position : 1;
  double get shiftX => deforms ? deformation.offset.position : 0;

  Size _baseThumbSize(double p) => widget.slider
      ? Size(AkiraSliderSpec.thumbWidth * (1 + (AkiraSliderSpec.heldScale-1) * p), AkiraSliderSpec.thumbHeight * (1 + (AkiraSliderSpec.heldScale-1) * p))
      : Size(AkiraSwitchSpec.thumbWidth + (AkiraSwitchSpec.heldWidth-AkiraSwitchSpec.thumbWidth) * p, AkiraSwitchSpec.thumbHeight + (AkiraSwitchSpec.heldHeight-AkiraSwitchSpec.thumbHeight) * p);

  double _deformationPosition(RenderBox box) => widget.slider
      ? position.value.clamp(0, 1) * (box.size.width - 37)
      : AkiraSwitchSpec.leadingInset + AkiraSwitchSpec.thumbWidth/2 + AkiraSwitchSpec.travel * position.value;

  void _ensureDeformation() {
    if (!deforms || deformationTicker.isActive) return;
    final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return;
    deformation.historyTime = deformationTime;
    deformation.historyPosition = _deformationPosition(box);
    deformation.historyVelocity = deformation.historyAcceleration = 0;
    deformationPrevious = Duration.zero;
    deformationTicker.start();
  }

  void _advanceDeformation(Duration elapsed) {
    final dt = (elapsed - deformationPrevious).inMicroseconds / 1e6;
    deformationPrevious = elapsed;
    if (dt <= 0) return;
    final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return;
    deformationTime += dt;
    final targetSize = _baseThumbSize(pointer == null ? 0 : 1);
    deformation.updateTargets(
      time: deformationTime,
      position: _deformationPosition(box),
      width: targetSize.width,
      height: targetSize.height,
      parameters: pointer == null
          ? NativeDeformationParameters.released
          : widget.slider
          ? NativeDeformationParameters.held
          : NativeDeformationParameters.switchHeld,
    );
    deformation.step(dt);
    if (pointer == null &&
        !pressure.isAnimating &&
        !position.isAnimating &&
        deformation.isSettled)
      deformationTicker.stop();
    _refresh();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    source = ReviewCaptureScope.of(context);
    if (!loading && source?.enabled == true) {
      loading = true;
      _load();
    }
  }

  Future<void> _load() async {
    final program = await loadRecoveredProgram('glass_lean_lens');
    final recipes =
        jsonDecode(
              await rootBundle.loadString(
                'assets/review/interaction-recipes.json',
              ),
            )
            as Map;
    if (!mounted) return;
    setState(() {
      lens = program.fragmentShader();
      optical = Map<String, dynamic>.from(
        recipes[widget.slider ? 'slider' : 'switch']['uniforms'] as Map,
      );
    });
  }

  @override
  void didUpdateWidget(_ThumbControl oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!widget.slider && pointer == null && oldWidget.value != widget.value)
      _revealTo(widget.value);
    if (pointer == null && (position.value - _restPosition).abs() > .001) {
      _positionTo(_restPosition);
    }
  }

  void _positionTo(double value) {
    targetPosition = value;
    _ensureDeformation();
    if (widget.slider && pointer != null && dragged) {
      position.value = value;
      return;
    }
    retargetGlass(
      position,
      value,
      response: widget.slider
          ? GlassResponse.sliderTracking
          : pointer != null && dragged
          ? GlassResponse.switchTracking
          : GlassResponse.switchRelease,
      reduceMotion: widget.settings.reduceMotion,
    );
  }

  void _refresh() {
    if (!mounted) return;
    if (!widget.slider && pointer != null && dragged)
      _revealTo(position.value > .5);
    ReviewFrameAudit.sample(
      widget.label,
      position.value,
      targetPosition,
      position.velocity,
    );
    if (pressure.value <= .0001 && pointer == null && !pressure.isAnimating) {
      overlay?.remove();
      overlay = null;
    }
    final host = source?.captureKey.currentState;
    if (host != null) {
      final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
      final stage =
          host.stageKey.currentContext?.findRenderObject() as RenderBox?;
      if (overlay != null && box != null && stage != null) {
        final p = pressure.value.clamp(-.12, 1.16);
        final center = stage.globalToLocal(
          box.localToGlobal(
            Offset(
              widget.slider
                  ? 18.5 + (box.size.width - 37) * position.value + shiftX
                  : AkiraSwitchSpec.leadingInset + AkiraSwitchSpec.thumbWidth/2 + AkiraSwitchSpec.travel * position.value + shiftX,
              22,
            ),
          ),
        );
        final halfWidth = _baseThumbSize(p).width / 2 * scaleX;
        host.lensCapture.pixelRatio = MediaQuery.devicePixelRatioOf(context);
        final localOrigin = stage.globalToLocal(box.localToGlobal(Offset.zero));
        host.lensCapture.alignmentOrigin = Offset(localOrigin.dx - 16, 0);
        host.lensCapture.region = widget.slider
            ? Rect.fromLTRB(
                center.dx - halfWidth - 40,
                center.dy - 31.5,
                center.dx + halfWidth + 40,
                center.dy + 31.5,
              )
            : Rect.fromLTRB(
                localOrigin.dx - 16,
                center.dy - 30,
                localOrigin.dx + 63 + 16,
                center.dy + 30,
              );
      } else {
        host.lensCapture.region = null;
      }
      host.repaint.request();
    }
    overlay?.markNeedsBuild();
    setState(() {});
  }

  void _down(PointerDownEvent event) {
    if (!widget.enabled || pointer != null) return;
    ReviewFrameAudit.begin(widget.label);
    pointer = event.pointer;
    start = event.position;
    startPosition = widget.slider ? _restPosition : position.value;
    targetPosition = startPosition;
    dragged = false;
    retargetGlass(
      pressure,
      1,
      response: GlassResponse.contact,
      reduceMotion: widget.settings.reduceMotion,
    );
    if (source?.enabled == true &&
        widget.settings.style == 'Glass' &&
        !widget.settings.reduceTransparency &&
        overlay == null) {
      overlay = OverlayEntry(
        builder: (_) => Positioned.fill(
          child: IgnorePointer(
            child: CustomPaint(painter: _ThumbLensPainter(this)),
          ),
        ),
      );
      Overlay.of(context, rootOverlay: true).insert(overlay!);
    }
    _ensureDeformation();
    _refresh();
  }

  void _choose(double deltaX) {
    final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
    if (box == null) return;
    final raw = (startPosition + deltaX / (box.size.width - 37).clamp(1, double.infinity)).clamp(0.0, 1.0);
    if (widget.scalarValue != null) {
      final next = (raw * 100).round() / 100;
      _positionTo(next);
      if (next != widget.scalarValue) {
        final bucket = (next * 20).round();
        final now = hapticClock.elapsedMilliseconds;
        if (bucket != lastHapticBucket && now - lastHapticTime >= 45) {
          lastHapticBucket = bucket;
          lastHapticTime = now;
          HapticFeedback.selectionClick();
        }
        widget.onScalar?.call(next);
      }
      return;
    }
    final index = (raw * 6).round();
    _positionTo(index / 6);
    if (widget.settings.noteSizeIndex != index) {
      HapticFeedback.selectionClick();
      widget.settings.change(() => widget.settings.noteSizeIndex = index);
    }
  }

  void _move(PointerMoveEvent event) {
    if (event.pointer != pointer) return;
    dragged = dragged || (event.position - start).distance > 3;
  }

  void _dragStart(DragStartDetails event) {
    if (pointer == null) return;
    dragged = true;
    lastHapticBucket = (_restPosition * 20).round();
    start = event.globalPosition;
    startPosition = widget.slider ? _restPosition : position.value;
  }

  void _dragUpdate(DragUpdateDetails event) {
    if (pointer == null) return;
    final delta = event.globalPosition.dx - start.dx;
    if (widget.slider)
      _choose(delta);
    else
      _positionTo(
        (startPosition + delta / 22).clamp(-5.333 / 22, 1 + 5.333 / 22),
      );
  }

  void _up(PointerEvent event, {bool cancel = false}) {
    if (event.pointer != pointer) return;
    pointer = null;
    double target;
    if (widget.slider) {
      if (!cancel && !dragged) {
        final box = boxKey.currentContext?.findRenderObject() as RenderBox?;
        if (box != null) {
          final local = box.globalToLocal(event.position);
          startPosition = 0;
          _choose(local.dx - 18.5);
        }
      }
      target = _restPosition;
    } else {
      final next = cancel
          ? widget.value
          : dragged
          ? targetPosition >= .5
          : !widget.value;
      target = next ? 1 : 0;
      if (!cancel && next != widget.value) widget.onChanged?.call(next);
    }
    _positionTo(target);
    if (!widget.slider) _revealTo(target > .5);
    retargetGlass(
      pressure,
      0,
      response: GlassResponse.release,
      reduceMotion: widget.settings.reduceMotion,
    );
  }

  @override
  void dispose() {
    overlay?.remove();
    overlay = null;
    source?.captureKey.currentState?.lensCapture.region = null;
    pressure.dispose();
    position.dispose();
    reveal.dispose();
    deformationTicker.dispose();
    lens?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final activeGlass = overlay != null && lens != null;
    return RecoveredPointerExclusionRegion(
      behavior: HitTestBehavior.deferToChild,
      child: Semantics(
        label: widget.label,
        slider: widget.slider,
        toggled: widget.slider ? null : widget.value,
        enabled: widget.enabled,
        value: widget.slider ? widget.scalarValue != null
            ? '${(_restPosition * 100).round()}%' : '${widget.settings.noteSize.toInt()}' : null,
        increasedValue: widget.scalarValue != null
            ? '${((_restPosition + .01).clamp(0, 1) * 100).round()}%'
            : widget.slider
            ? '${recoveredNoteSizes[(widget.settings.noteSizeIndex + 1).clamp(0, 6)].toInt()}'
            : null,
        decreasedValue: widget.scalarValue != null
            ? '${((_restPosition - .01).clamp(0, 1) * 100).round()}%'
            : widget.slider
            ? '${recoveredNoteSizes[(widget.settings.noteSizeIndex - 1).clamp(0, 6)].toInt()}'
            : null,
        onTap: widget.slider || !widget.enabled
            ? null
            : () => widget.onChanged?.call(!widget.value),
        onIncrease: !widget.slider || _restPosition >= 1
            ? null
            : () { if (widget.scalarValue != null) {
                widget.onScalar?.call((_restPosition + .01).clamp(0, 1));
              } else { widget.settings.change(() => widget.settings.noteSizeIndex++); } },
        onDecrease: !widget.slider || _restPosition <= 0
            ? null
            : () { if (widget.scalarValue != null) {
                widget.onScalar?.call((_restPosition - .01).clamp(0, 1));
              } else { widget.settings.change(() => widget.settings.noteSizeIndex--); } },
        child: Listener(
          behavior: HitTestBehavior.opaque,
          onPointerDown: _down,
          onPointerMove: _move,
          onPointerUp: _up,
          onPointerCancel: (e) => _up(e, cancel: true),
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: () {},
            onHorizontalDragStart: _dragStart,
            onHorizontalDragUpdate: _dragUpdate,
            child: SizedBox(
              key: boxKey,
              height: 44,
              width: widget.slider ? double.infinity : 63,
              child: CustomPaint(
                painter: _ControlPainter(
                  widget.settings,
                  widget.slider,
                  position.value,
                  pressure.value.clamp(-.12, 1.16),
                  activeGlass,
                  widget.enabled,
                  widget.scalarValue == null,
                  scaleX,
                  scaleY,
                  shiftX,
                  reveal.value,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ControlPainter extends CustomPainter {
  _ControlPainter(
    this.settings,
    this.slider,
    this.position,
    this.pressure,
    this.hideThumb,
    this.enabled,
    this.stepped,
    this.scaleX,
    this.scaleY,
    this.shiftX,
    this.reveal,
  );
  final AppearanceSettings settings;
  final bool slider, hideThumb, enabled, stepped;
  final double position, pressure, scaleX, scaleY, shiftX, reveal;
  @override
  void paint(Canvas canvas, Size size) {
    final center = slider
        ? 18.5 + (size.width - 37) * position
        : AkiraSwitchSpec.leadingInset + AkiraSwitchSpec.thumbWidth/2 + AkiraSwitchSpec.travel * position;
    final inactive = settings.dark
        ? const Color(AkiraSwitchSpec.inactiveDarkArgb)
        : const Color(AkiraSwitchSpec.inactiveLightArgb);
    if (slider) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(0, 19, size.width, 6),
          const Radius.circular(3),
        ),
        Paint()
          ..color = settings.dark
              ? const Color(AkiraSliderSpec.inactiveDarkArgb)
              : const Color(AkiraSliderSpec.inactiveLightArgb),
      );
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(0, 19, center, 6),
          const Radius.circular(3),
        ),
        Paint()..color = settings.accent,
      );
      if (stepped) for (var i = 0; i < 7; i++)
        canvas.drawCircle(
          Offset(18.5 + (size.width - 37) * i / 6, 31),
          2,
          Paint()
            ..color = settings.dark
                ? const Color(0x28ebebf5)
                : const Color(0xffc7c7cc),
        );
    } else {
      paintNativeSwitchTrack(
        canvas,
        const Rect.fromLTWH(0, 8, AkiraSwitchSpec.trackWidth, AkiraSwitchSpec.trackHeight),
        scalar: reveal,
        inactive: inactive,
        active: settings.dark
            ? settings.accent
            : settings.accent,
        enabled: enabled,
      );
    }
    if (!hideThumb) {
      final p = settings.style == 'Glass' && enabled ? pressure : 0.0;
      final width = (slider ? 37 * (1 + .55 * p) : AkiraSwitchSpec.thumbWidth + (AkiraSwitchSpec.heldWidth-AkiraSwitchSpec.thumbWidth) * p) * scaleX;
      final height = (slider ? 24 * (1 + .55 * p) : AkiraSwitchSpec.thumbHeight + (AkiraSwitchSpec.heldHeight-AkiraSwitchSpec.thumbHeight) * p) * scaleY;
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromCenter(
            center: Offset(center + shiftX, 22),
            width: width,
            height: height,
          ),
          Radius.circular(height / 2),
        ),
        Paint()..color = Colors.white,
      );
    }
  }

  @override
  bool shouldRepaint(_ControlPainter old) => true;
}

class _ThumbLensPainter extends CustomPainter {
  _ThumbLensPainter(this.control) : super(repaint: control.pressure);
  final _ThumbControlState control;
  @override
  void paint(Canvas canvas, Size size) {
    final source = control.source,
        shader = control.lens,
        recipe = control.optical;
    final host = source?.captureKey.currentState;
    final box = control.boxKey.currentContext?.findRenderObject() as RenderBox?;
    if (host == null ||
        box == null ||
        shader == null ||
        recipe == null ||
        host.capture.image == null)
      return;
    final p = control.pressure.value.clamp(-.12, 1.16);
    final amount = p.clamp(0.0, 1.0);
    final bodySize = control._baseThumbSize(p);
    final hw = bodySize.width / 2 * control.scaleX;
    final hh = bodySize.height / 2 * control.scaleY;
    final local = Offset(
      control.widget.slider
          ? 18.5 +
                (box.size.width - 37) * control.position.value +
                control.shiftX
          : AkiraSwitchSpec.leadingInset + AkiraSwitchSpec.thumbWidth/2 + AkiraSwitchSpec.travel * control.position.value + control.shiftX,
      22,
    );
    final center = box.localToGlobal(local);
    final origin = center - Offset(hw, hh);
    final stage =
        host.stageKey.currentContext!.findRenderObject()! as RenderBox;
    final stageOrigin = stage.globalToLocal(origin);
    final fields = copyRenderFields(recipe);
    (fields['uP'] as List)[0] = [hw, hh, hw, hh];
    (fields['uRad'] as List)[0] = -hh;
    fields['uBd'] = [
      stageOrigin.dx,
      stageOrigin.dy,
      1 / stage.size.width,
      1 / stage.size.height,
    ];
    final texture = host.lensCapture.image;
    final region = host.lensCapture.capturedRegion;
    if (texture == null || region == null) return;
    fields['uBd'] = [
      stageOrigin.dx - region.left,
      stageOrigin.dy - region.top,
      1 / region.width,
      1 / region.height,
    ];
    fields['uTap'] = [
      region.left - stageOrigin.dx,
      region.top - stageOrigin.dy,
      region.width,
      region.height,
    ];
    control.widget.settings.configureMaterial(fields);
    bindReviewFloats(shader, fields);
    for (final name in ['uLevel', 'uSharp'])
      shader.setImageSampler(
        reviewSamplerIndex(shader, name),
        texture,
        filterQuality: ui.FilterQuality.low,
      );
    canvas.save();
    canvas.translate(origin.dx, origin.dy);
    canvas.saveLayer(
      Rect.fromLTRB(-24, -24, hw * 2 + 24, hh * 2 + 24),
      Paint()..color = Colors.white.withValues(alpha: amount),
    );
    canvas.drawRect(
      Rect.fromLTRB(-24, -24, hw * 2 + 24, hh * 2 + 24),
      Paint()..shader = shader,
    );
    canvas.restore();
    if (amount < 1) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromCenter(
            center: Offset(hw, hh),
            width: hw * 2,
            height: hh * 2,
          ),
          Radius.circular(hh),
        ),
        Paint()..color = Colors.white.withValues(alpha: 1 - amount),
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_ThumbLensPainter old) => true;
}

class AkiraContactResponse extends StatefulWidget {
  const AkiraContactResponse({
    super.key,
    required this.child,
    this.elastic = true,
    this.localLight = AkiraContactSpec.glowAmount,
    this.showLight = true,
    this.contactTuning = const NativeContactTuning(),
    this.coordinateScale = 1,
    this.paintPadding = 0,
  });
  final Widget child;
  final bool elastic;
  final double localLight;
  final bool showLight;
  final NativeContactTuning contactTuning;
  final double coordinateScale, paintPadding;
  @override
  State<AkiraContactResponse> createState() => _PressGlowState();
}

class _PressGlowState extends State<AkiraContactResponse> with TickerProviderStateMixin {
  final hitKey = GlobalKey();
  final faceKey = GlobalKey();
  late final deformation = NativeContactDeformation(
    tuning: widget.contactTuning,
  );
  late final Ticker contactTicker = createTicker(_tickContact);
  late final intensity = _controller(0);
  late final reach = _controller(1);
  late final localFactor = _controller(1);
  late final boundedX = _controller(.5), boundedY = _controller(.5);
  late final unboundedX = _controller(.5), unboundedY = _controller(.5);
  Duration previous = Duration.zero;
  GlassMotionController _controller(double value) =>
      GlassMotionController.unbounded(vsync: this, value: value)
        ..addListener(_refresh);
  void _refresh() {
    if (mounted) setState(() {});
  }

  ui.FragmentShader? shader;
  ui.Image? profile;
  bool loading = false;
  Offset down = Offset.zero;
  Offset globalContact = Offset.zero;
  bool escaped = false;
  int? pointer;
  bool get reduced =>
      ReviewCaptureScope.of(context)?.settings.reduceMotion == true ||
      MediaQuery.disableAnimationsOf(context);
  void _release(PointerEvent event) {
    if (event.pointer != pointer) return;
    pointer = null;
    intensity.driveSpring(0, period: AkiraContactSpec.glowReleasePeriod, damping: AkiraContactSpec.glowDamping, immediate: reduced);
    reach.driveSpring(4, period: .5, damping: 1, immediate: reduced);
    _startContactClock();
  }

  RenderBox? get face =>
      faceKey.currentContext?.findRenderObject() as RenderBox?;

  void _startContactClock() {
    if (!contactTicker.isActive) {
      previous = Duration.zero;
      contactTicker.start();
    }
  }

  void _tickContact(Duration elapsed) {
    final dt = (elapsed - previous).inMicroseconds / 1e6;
    previous = elapsed;
    if (dt <= 0) return;
    final box = face;
    if (box == null || !box.hasSize) return;
    final size = box.size / widget.coordinateScale;
    final a =
        Offset(unboundedX.value * size.width, unboundedY.value * size.height) -
        down / widget.coordinateScale;
    final b =
        Offset(boundedX.value * size.width, boundedY.value * size.height) -
        down / widget.coordinateScale;
    deformation.updateTargets(
      size: size,
      held: pointer != null,
      unbounded: a,
      bounded: b,
    );
    deformation.step(dt, immediate: reduced);
    for (final (name, spring) in [
      ('contact-scale-x', deformation.x),
      ('contact-scale-y', deformation.y),
      ('contact-offset-x', deformation.dx),
      ('contact-offset-y', deformation.dy),
    ]) {
      ReviewFrameAudit.sample(
        name,
        spring.position,
        spring.target,
        spring.velocity,
      );
    }
    ReviewFrameAudit.sample(
      'contact-glow',
      intensity.value,
      pointer == null ? 0 : .8,
      intensity.velocity,
    );
    ReviewFrameAudit.sample(
      'contact-reach',
      reach.value,
      pointer == null
          ? 4
          : escaped
          ? 2
          : 1,
      reach.velocity,
    );
    if (pointer != null) {
      final local = box.globalToLocal(globalContact);
      final nx = local.dx / box.size.width, ny = local.dy / box.size.height;
      for (final (controller, target) in [
        (boundedX, nx.clamp(0.0, 1.0)),
        (boundedY, ny.clamp(0.0, 1.0)),
        (unboundedX, nx),
        (unboundedY, ny),
      ]) {
        controller.driveSpring(
          target,
          period: .15,
          damping: 1,
          immediate: reduced,
        );
      }
      if (!escaped && !(Offset.zero & box.size).contains(local)) {
        escaped = true;
        localFactor.driveSpring(.5, period: .5, damping: 1, immediate: reduced);
        reach.driveSpring(2, period: .5, damping: 1, immediate: reduced);
      }
    } else if (deformation.isSettled) {
      contactTicker.stop();
    }
    _refresh();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (widget.showLight &&
        !loading &&
        ReviewCaptureScope.of(context)?.enabled == true) {
      loading = true;
      _load();
    }
  }

  Future<void> _load() async {
    final program = await loadRecoveredProgram('press_glow');
    if (!mounted) return;
    setState(() {
      shader = program.fragmentShader();
      profile = recordPressGlowProfile();
    });
  }

  @override
  void dispose() {
    contactTicker.dispose();
    for (final c in [
      intensity,
      reach,
      localFactor,
      boundedX,
      boundedY,
      unboundedX,
      unboundedY,
    ]) {
      c.dispose();
    }
    shader?.dispose();
    profile?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final source = ReviewCaptureScope.of(context);
    final enabled =
        source?.settings.style == 'Glass' &&
        source?.settings.reduceTransparency == false;
    final filter = shader;
    final sx = enabled && widget.elastic ? deformation.x.position : 1.0;
    final sy = enabled && widget.elastic ? deformation.y.position : 1.0;
    final dx = enabled && widget.elastic
        ? deformation.dx.position * widget.coordinateScale
        : 0.0;
    final dy = enabled && widget.elastic
        ? deformation.dy.position * widget.coordinateScale
        : 0.0;
    if (filter != null) {
      final dpr = MediaQuery.devicePixelRatioOf(context);
      final size = face?.size ?? const Size(44, 44);
      final point = Offset(
        unboundedX.value * size.width,
        unboundedY.value * size.height,
      );
      final pad = widget.child is NativeGlassButton
          ? 16.0
          : widget.paintPadding;
      // The filter is outside the surface transform; its texture starts at
      // the transformed paint bounds, including the lean shader's overflow.
      bindReviewFloats(filter, {
        'uCenter': [(point.dx + pad) * sx * dpr, (point.dy + pad) * sy * dpr],
        'uReach': AkiraContactSpec.glowReach * size.shortestSide * sx * reach.value * dpr,
        'uBig': intensity.value,
        'uLittle': widget.localLight / AkiraContactSpec.glowAmount * intensity.value * localFactor.value,
        'uDodge': AkiraContactSpec.glowDodge,
      }, imageFilter: true);
      filter.setImageSampler(
        reviewSamplerIndex(filter, 'uProfile'),
        profile!,
        filterQuality: ui.FilterQuality.low,
      );
    }
    Widget child = SizedBox(key: faceKey, child: widget.child);
    if (enabled && widget.elastic) {
      child = Transform(
        alignment: Alignment.center,
        transform: Matrix4.identity()
          ..setEntry(0, 0, sx)
          ..setEntry(1, 1, sy)
          ..setEntry(0, 3, dx)
          ..setEntry(1, 3, dy),
        child: child,
      );
    }
    if (filter != null) {
      child = ImageFiltered(
        imageFilter: ui.ImageFilter.shader(filter),
        enabled: enabled && intensity.value > .0001,
        child: child,
      );
    }
    return RecoveredPointerExclusionRegion(
      behavior: HitTestBehavior.deferToChild,
      child: Listener(
        key: hitKey,
        behavior: HitTestBehavior.deferToChild,
        onPointerDown: (e) {
          if (pointer != null) return;
          ReviewFrameAudit.begin('Glass contact');
          pointer = e.pointer;
          globalContact = e.position;
          final box = face!;
          down = box.globalToLocal(e.position);
          escaped = false;
          boundedX.value = unboundedX.value = down.dx / box.size.width;
          boundedY.value = unboundedY.value = down.dy / box.size.height;
          reach.value = localFactor.value = 1;
          _startContactClock();
          intensity.driveSpring(AkiraContactSpec.glowAmount, period: AkiraContactSpec.glowPeriod, damping: AkiraContactSpec.glowDamping, immediate: reduced);
        },
        onPointerMove: (e) {
          if (pointer != e.pointer) return;
          globalContact = e.position;
        },
        onPointerUp: _release,
        onPointerCancel: _release,
        child: child,
      ),
    );
  }
}

typedef InteractiveToggle = AkiraSwitch;

typedef InteractiveValueSlider = AkiraSlider;

typedef InteractiveSizeSlider = AkiraSizeSlider;

typedef PressGlow = AkiraContactResponse;
