import 'dart:ui' as ui;

import 'package:inotes_recovered/inotes_recovered.dart'
    show LiveCaptureController;
import 'package:flutter/rendering.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

class RegionalCaptureMetrics {
  static int backgrounds = 0, lenses = 0;
  static int paneMipChains = 0, headerMipChains = 0;
}

class RegionalLensCapture {
  ui.Rect? region;
  ui.Rect? capturedRegion;
  ui.Image? image;
  double pixelRatio = 1;
  ui.Offset alignmentOrigin = ui.Offset.zero;
  int generation = 0;
}

/// Research stage: records background first, captures it, then paints foreground.
/// Notify [repaint] when a nested repaint boundary changes without repainting
/// this stage; scroll notifications can provide that invalidation in a host app.
class RegionalCaptureStage extends MultiChildRenderObjectWidget {
  RegionalCaptureStage({
    super.key,
    required this.controller,
    required this.lens,
    required this.pixelRatio,
    required this.repaint,
    required Widget background,
    required Widget foreground,
  }) : super(children: <Widget>[background, foreground]);

  final LiveCaptureController controller;
  final RegionalLensCapture lens;
  final double pixelRatio;
  final Listenable repaint;

  @override
  RenderObject createRenderObject(BuildContext context) =>
      _RenderRegionalCapture(controller, lens, pixelRatio, repaint);

  @override
  void updateRenderObject(
    BuildContext context,
    covariant _RenderRegionalCapture renderObject,
  ) {
    renderObject.update(controller, lens, pixelRatio, repaint);
  }
}

class _CaptureParentData extends ContainerBoxParentData<RenderBox> {}

class _RenderRegionalCapture extends RenderBox
    with
        ContainerRenderObjectMixin<RenderBox, _CaptureParentData>,
        RenderBoxContainerDefaultsMixin<RenderBox, _CaptureParentData> {
  _RenderRegionalCapture(
    this._controller,
    this._lens,
    this._pixelRatio,
    this._repaint,
  ) {
    _repaint.addListener(markNeedsPaint);
  }

  LiveCaptureController _controller;
  RegionalLensCapture _lens;
  double _pixelRatio;
  Listenable _repaint;
  final LayerHandle<OffsetLayer> _sourceLayer = LayerHandle<OffsetLayer>();

  void update(
    LiveCaptureController controller,
    RegionalLensCapture lens,
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
    _lens = lens;
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
      throw FlutterError(
        'RegionalCaptureStage requires bounded width and height.',
      );
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
    if (kProfileMode) RegionalCaptureMetrics.backgrounds++;
    previous?.dispose();
    final region = _lens.region;
    final priorLens = _lens.image;
    if (region != null && !region.isEmpty) {
      final ratio = _lens.pixelRatio;
      // Native lens textures retain full DPR; horizontal ROI extents use 8px tiles.
      final anchor = _lens.alignmentOrigin.dx * ratio;
      final left = ((region.left * ratio - anchor) / 8).round() * 8.0 + anchor;
      final right =
          ((region.right * ratio - anchor) / 8).round() * 8.0 + anchor;
      final top = region.top * ratio;
      final bottom = top + (region.height * ratio).ceil();
      final regionalBuilder = ui.SceneBuilder();
      regionalBuilder.pushTransform(
        Float64List.fromList([
          ratio,
          0,
          0,
          0,
          0,
          ratio,
          0,
          0,
          0,
          0,
          1,
          0,
          -offset.dx * ratio - left,
          -offset.dy * ratio - top,
          0,
          1,
        ]),
      );
      final regionalScene = layer.buildScene(regionalBuilder);
      try {
        _lens.image = regionalScene.toImageSync(
          (right - left).round(),
          (bottom - top).round(),
        );
        _lens.capturedRegion = ui.Rect.fromLTRB(
          left / ratio,
          top / ratio,
          right / ratio,
          bottom / ratio,
        );
        _lens.generation++;
        if (kProfileMode) RegionalCaptureMetrics.lenses++;
      } finally {
        regionalScene.dispose();
      }
    } else {
      _lens.image = null;
      _lens.capturedRegion = null;
    }
    priorLens?.dispose();
    if (foreground != null) {
      // A dirty child boundary may have painted before this capture was published.
      // Repaint it against the new generation before retaining it in this layer.
      if (foreground.isRepaintBoundary) foreground.markNeedsPaint();
      context.paintChild(foreground, offset);
    }
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) =>
      defaultHitTestChildren(result, position: position);

  @override
  void dispose() {
    _repaint.removeListener(markNeedsPaint);
    _controller.image?.dispose();
    _controller.image = null;
    _lens.image?.dispose();
    _lens.image = null;
    _sourceLayer.layer = null;
    super.dispose();
  }
}
