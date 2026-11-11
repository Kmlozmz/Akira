import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'candidate_layouts.dart';

void main() => runApp(const ComparisonApp());

class ComparisonApp extends StatefulWidget {
  const ComparisonApp({super.key});
  @override
  State<ComparisonApp> createState() => _ComparisonState();
}

class _ComparisonState extends State<ComparisonApp> {
  final pairs = <String, (ui.FragmentShader, ui.FragmentShader)>{};
  final errors = <String, String>{};
  late final ui.Image image;

  @override
  void initState() {
    super.initState();
    final recorder = ui.PictureRecorder();
    final canvas = Canvas(recorder);
    final paint = Paint()..shader = const LinearGradient(
      colors: [Colors.red, Colors.blue, Colors.green],
    ).createShader(const Rect.fromLTWH(0, 0, 256, 256));
    canvas.drawRect(const Rect.fromLTWH(0, 0, 256, 256), paint);
    image = recorder.endRecording().toImageSync(256, 256);
    load();
  }

  Future<void> load() async {
    for (final name in candidateLayouts.keys) {
      try {
        final a = await ui.FragmentProgram.fromAsset('shaders/$name.frag');
        final b = await ui.FragmentProgram.fromAsset('shaders/${name}_structured.frag');
        pairs[name] = (a.fragmentShader(), b.fragmentShader());
      } catch (error) { errors[name] = '$error'; }
      if (mounted) setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) => MaterialApp(home: Scaffold(
    appBar: AppBar(title: const Text('Shader equivalence: synthetic inputs')),
    body: ListView(children: [for (final name in candidateLayouts.keys) ...[
      Text('$name: GLSL left / structured right'),
      if (errors.containsKey(name)) Text(errors[name]!),
      if (pairs[name] case final pair?) SizedBox(height: 120, child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Expanded(child: CustomPaint(painter: ComparisonPainter(name, pair.$1, image))),
        const SizedBox(width: 2),
        Expanded(child: CustomPaint(painter: ComparisonPainter(name, pair.$2, image))),
      ])),
    ]]),
  ));
}

class ComparisonPainter extends CustomPainter {
  ComparisonPainter(this.name, this.shader, this.image);
  final String name;
  final ui.FragmentShader shader;
  final ui.Image image;

  double value(String key, int i, Size s) {
    final w = s.width, h = s.height;
    switch (key) {
      case 'uFrag': return [0.0, 0.0, 1.0, 1.0][i];
      case 'uSize': return [w, h][i];
      case 'uBd': return [0.0, 0.0, 1.0/w, 1.0/h][i];
      case 'uRect': case 'uSrc': case 'uDst': case 'uTap': return [0.0, 0.0, w, h][i];
      case 'uCenter': return [w/2, h/2][i];
      case 'uReach': return w/2;
      case 'uP': return i < 4 ? [w/2,h/2,w*0.38,h*0.38][i] : 0.0;
      case 'uRad': return i == 0 ? 22.0 : 0.0;
      case 'uShape': return [0.0, 0.0, 1.0, 1.0][i];
      default: return 0.25;
    }
  }

  @override
  void paint(Canvas canvas, Size size) {
    for (final (key, _, count) in candidateLayouts[name] ?? <(String, int, int)>[]) {
      for (var i = 0; i < count; i++) { shader.getUniformFloat(key, i).set(value(key, i, size)); }
    }
    for (final (key, _) in candidateSamplers[name] ?? <(String, int)>[]) {
      shader.getImageSampler(key).set(image);
    }
    canvas.drawRect(Offset.zero & size, Paint()..shader = shader);
  }

  @override
  bool shouldRepaint(covariant ComparisonPainter oldDelegate) => false;
}
