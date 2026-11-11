import 'dart:ui' as ui;
import 'dart:typed_data';

import 'package:flutter/rendering.dart';
import 'package:flutter/widgets.dart';

class LiveCaptureController {
  ui.Image? image;
  int generation = 0;
}

/// Research stage: records background first, captures it, then paints foreground.
/// Notify [repaint] when a nested repaint boundary changes without repainting
/// this stage; scroll notifications can provide that invalidation in a host app.
class LiveCaptureStage extends MultiChildRenderObjectWidget {
  LiveCaptureStage({
    super.key,
    required this.controller,
    required this.pixelRatio,
    required this.repaint,
    required Widget background,
    required Widget foreground,
  }) : super(children: <Widget>[background, foreground]);

  final LiveCaptureController controller;
  final double pixelRatio;
  final Listenable repaint;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderLiveCapture(controller, pixelRatio, repaint);

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderLiveCapture renderObject,
  ) {
    renderObject.update(controller, pixelRatio, repaint);
  }
}

class _CaptureParentData extends ContainerBoxParentData<RenderBox> {}

class _RenderLiveCapture extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, _CaptureParentData>,
        RenderBoxContainerDefaultsMixin<RenderBox, _CaptureParentData> {
  _RenderLiveCapture(this._controller, this._pixelRatio, this._repaint) {
    _repaint.addListener(markNeedsPaint);
  }

  LiveCaptureController _controller;
  double _pixelRatio;
  Listenable _repaint;
  final LayerHandle<OffsetLayer> _sourceLayer = LayerHandle<OffsetLayer>();

  void update(
    LiveCaptureController controller,
    double pixelRatio,
    Listenable repaint,
  ) {
    assert(pixelRatio > 0 && pixelRatio.isFinite);
    if (!identical(_controller, controller)) {
      _controller.image?.dispose();
      _controller.image = null;
      _controller = controller;
    }
    if (!identical(_repaint, repaint)) {
      _repaint.removeListener(markNeedsPaint);
      _repaint = repaint;
      _repaint.addListener(markNeedsPaint);
    }
    _pixelRatio = pixelRatio;
    markNeedsPaint();
  }

  @override
  bool get alwaysNeedsCompositing => true;

  @override
  void setupParentData(RenderBox child) {
    if (child.parentData is! _CaptureParentData)
      child.parentData = _CaptureParentData();
  }

  @override
  void performLayout() {
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
      throw FlutterError('LiveCaptureStage requires bounded width and height.');
    }
    size = constraints.biggest;
    var child = firstChild;
    while (child != null) {
      child.layout(BoxConstraints.tight(size), parentUsesSize: false);
      final data = child.parentData! as _CaptureParentData;
      data.offset = Offset.zero;
      child = data.nextSibling;
    }
  }

  @override
  void paint(PaintingContext context, Offset offset) {
    final background = firstChild;
    if (background == null) return;
    final foreground = childAfter(background);
    _sourceLayer.layer ??= OffsetLayer();
    final layer = _sourceLayer.layer!..offset = offset;
    context.pushLayer(
      layer,
      (childContext, childOffset) =>
          childContext.paintChild(background, childOffset),
      Offset.zero,
      childPaintBounds: Offset.zero & size,
    );
    // Preserve exact physical sizes when DPR division leaves a near-integer ULP.
    int captureExtent(double extent) {
      final nearest = extent.round();
      return (extent - nearest).abs() < 1e-6 ? nearest : extent.ceil();
    }

    final builder = ui.SceneBuilder();
    builder.pushTransform(
      Float64List.fromList(<double>[
        _pixelRatio,
        0,
        0,
        0,
        0,
        _pixelRatio,
        0,
        0,
        0,
        0,
        1,
        0,
        -offset.dx * _pixelRatio,
        -offset.dy * _pixelRatio,
        0,
        1,
      ]),
    );
    final scene = layer.buildScene(builder);
    late ui.Image next;
    try {
      next = scene.toImageSync(
        captureExtent(size.width * _pixelRatio),
        captureExtent(size.height * _pixelRatio),
      );
    } finally {
      scene.dispose();
    }
    final previous = _controller.image;
    _controller.image = next;
    _controller.generation++;
    previous?.dispose();
    if (foreground != null) context.paintChild(foreground, offset);
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) =>
      defaultHitTestChildren(result, position: position);

  @override
  void dispose() {
    _repaint.removeListener(markNeedsPaint);
    _controller.image?.dispose();
    _controller.image = null;
    _sourceLayer.layer = null;
    super.dispose();
  }
}
