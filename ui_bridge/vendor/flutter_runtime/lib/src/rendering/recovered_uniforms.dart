import 'dart:ui' as ui;

Iterable<double> _numbers(Object? value) sync* {
  if (value is num) {
    yield value.toDouble();
  } else if (value is List) {
    for (final item in value) {
      yield* _numbers(item);
    }
  } else {
    throw ArgumentError.value(value, 'uniform');
  }
}

void bindRecoveredFloats(
  ui.FragmentShader shader,
  Map<String, Object?> fields, {
  bool imageFilter = false,
}) {
  for (final field in fields.entries) {
    if (imageFilter && field.key == 'uSize') continue;
    var index = 0;
    for (final value in _numbers(field.value)) {
      shader.getUniformFloat(field.key, index++).set(value);
    }
  }
}

enum RecoveredProgramEncoding { original, structured }

Future<ui.FragmentProgram> loadRecoveredProgram(
  String name, {
  RecoveredProgramEncoding encoding = RecoveredProgramEncoding.structured,
}) {
  const names = {
    'bar_blur_mip',
    'glass_lean',
    'glass_lean_lens',
    'glass_lean_morph',
    'glass_mip',
    'pocket_blur',
    'press_glow',
    'text_loupe',
  };
  if (!names.contains(name)) throw ArgumentError.value(name, 'name');
  final relative = encoding == RecoveredProgramEncoding.original
      ? 'assets/original_programs/$name.iplr'
      : 'shaders/${name}_structured.frag';
  return ui.FragmentProgram.fromAsset('packages/inotes_recovered/$relative');
}
