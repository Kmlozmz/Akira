import 'dart:io';
import 'dart:ui' as ui;
import '../recovered/render_bindings.dart';
import 'frame_audit.dart';
import 'pane_resources.dart';

Future<void> warmUpMenuComposition() async {
  if (!Platform.isAndroid) return;
  final watch = Stopwatch()..start();
  ui.FragmentShader? shader;
  ui.Picture? picture;
  ui.Image? image;
  try {
    final resources = await PaneResources.load();
    final program = resources.programs[2];
    final profile = resources.motion;
    final pose = profile.opening.first['morph'] as Map;
    final fields = copyRenderFields(pose)..['uFrag'] = [0, 0, 1, 1 / 255];
    shader = program.fragmentShader();
    bindReviewFloats(shader, fields, imageFilter: true);
    final recorder = ui.PictureRecorder();
    final canvas = ui.Canvas(recorder);
    const bounds = ui.Rect.fromLTWH(0, 0, 64, 64);
    canvas.saveLayer(
      bounds,
      ui.Paint()..imageFilter = ui.ImageFilter.shader(shader),
    );
    canvas.drawRect(bounds, ui.Paint()..color = const ui.Color(0xffffffff));
    canvas.restore();
    picture = recorder.endRecording();
    // Wait for the offscreen pass, so its first GPU use precedes interaction.
    image = await picture.toImage(64, 64);
    ReviewFrameAudit.surface('startup-menu-warmup', {
      'result': 'rendered',
      'elapsed_us': watch.elapsedMicroseconds,
    });
  } catch (error) {
    ReviewFrameAudit.surface('startup-menu-warmup', {
      'result': 'failed',
      'error': '$error',
      'elapsed_us': watch.elapsedMicroseconds,
    });
  } finally {
    image?.dispose();
    picture?.dispose();
    shader?.dispose();
  }
}
