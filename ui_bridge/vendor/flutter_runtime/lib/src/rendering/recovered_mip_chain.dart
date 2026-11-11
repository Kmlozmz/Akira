import 'dart:ui' as ui;

class RecoveredMipChain {
  RecoveredMipChain(ui.FragmentProgram program, {this.levelCount = 4})
    : _shader = program.fragmentShader();

  final int levelCount;
  final ui.FragmentShader _shader;
  List<ui.Image> _levels = <ui.Image>[];

  ui.Image render(ui.Image base) {
    final next = <ui.Image>[];
    var input = base;
    for (var i = 0; i < levelCount; i++) {
      final width = (input.width + 1) ~/ 2;
      final height = (input.height + 1) ~/ 2;
      _write('uSize', <double>[
        input.width.toDouble(),
        input.height.toDouble(),
      ]);
      _write('uSrc', <double>[
        0,
        0,
        input.width.toDouble(),
        input.height.toDouble(),
      ]);
      _write('uDst', <double>[0, 0, width.toDouble(), height.toDouble()]);
      _shader.setImageSampler(
        _shader.getImageSampler('uTex').shaderIndex,
        input,
        filterQuality: ui.FilterQuality.low,
      );
      final recorder = ui.PictureRecorder();
      final canvas = ui.Canvas(recorder);
      canvas.drawRect(
        ui.Rect.fromLTWH(0, 0, width.toDouble(), height.toDouble()),
        ui.Paint()..shader = _shader,
      );
      final picture = recorder.endRecording();
      input = picture.toImageSync(
        width,
        height,
        targetFormat: ui.TargetPixelFormat.dontCare,
      );
      picture.dispose();
      next.add(input);
    }
    final previous = _levels;
    _levels = next;
    for (final image in previous) {
      image.dispose();
    }
    return input;
  }

  void _write(String name, List<double> values) {
    for (var i = 0; i < values.length; i++) {
      _shader.getUniformFloat(name, i).set(values[i]);
    }
  }

  void dispose() {
    for (final image in _levels) {
      image.dispose();
    }
    _levels = <ui.Image>[];
    _shader.dispose();
  }
}
