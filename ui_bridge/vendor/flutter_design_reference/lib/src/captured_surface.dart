import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/services.dart';
import 'package:inotes_recovered/inotes_recovered.dart';

class RecoveredCapturedSurfaceRecipe {
  RecoveredCapturedSurfaceRecipe.fromJson(Map<String, dynamic> json)
    : data = json {
    if (json['schema_version'] != 1 ||
        json['material']['shape_kind'] != 'rect') {
      throw const FormatException('Unsupported captured surface schema');
    }
    _size(json['input_size']);
    _size(json['output_size']);
    final format = json['target_pixel_format_index'] as int;
    if (format < 0 || format >= ui.TargetPixelFormat.values.length) {
      throw const FormatException('Unknown original target pixel format');
    }
    for (final stage in levels) {
      _size(stage['output_size']);
      if (stage['program'] != 'glass_mip') {
        throw const FormatException('Unsupported captured reduction program');
      }
      _quality(stage['sampling'] as int);
    }
    final bindings = material['samplers'] as List;
    if (levels.isEmpty || bindings.length != 4) {
      throw const FormatException('Missing captured chain or samplers');
    }
    final slots = <int>{};
    for (final binding in bindings) {
      final slot = binding['slot'] as int;
      final level = binding['chain_level'] as int;
      if (!slots.add(slot) ||
          slot < 0 ||
          slot >= 4 ||
          level < 0 ||
          level >= levels.length) {
        throw const FormatException('Invalid captured sampler reference');
      }
      _quality(binding['sampling'] as int);
    }
    if ((material['rect'] as List).length != 4 ||
        (material['matrix_column_major'] as List).length != 16) {
      throw const FormatException('Missing captured draw geometry');
    }
  }

  final Map<String, dynamic> data;
  List<dynamic> get levels => data['levels'] as List<dynamic>;
  Map<String, dynamic> get material => data['material'] as Map<String, dynamic>;
  List<int> get inputSize => (data['input_size'] as List).cast<int>();
  List<int> get outputSize => (data['output_size'] as List).cast<int>();

  static Future<RecoveredCapturedSurfaceRecipe> loadMore({
    AssetBundle? bundle,
  }) async => RecoveredCapturedSurfaceRecipe.fromJson(
    jsonDecode(
      await (bundle ?? rootBundle).loadString(
        'packages/inotes_design_reference/assets/recovered_components/more_surface.json',
      ),
    ) as Map<String, dynamic>,
  );

  static void _size(dynamic value) {
    if (value is! List ||
        value.length != 2 ||
        value.any((dynamic n) => n is! int || n < 1 || n > 8192)) {
      throw const FormatException('Invalid captured image size');
    }
  }

  static ui.FilterQuality _quality(int index) {
    if (index < 0 || index >= ui.FilterQuality.values.length) {
      throw const FormatException('Unknown native sampling mode');
    }
    return ui.FilterQuality.values[index];
  }
}

class RecoveredCapturedSurfaceFrame {
  RecoveredCapturedSurfaceFrame(this.image, this.levels);
  final ui.Image image;
  final List<ui.Image> levels;

  void dispose() {
    image.dispose();
    for (final level in levels) {
      level.dispose();
    }
  }
}

/// Replays a captured background recipe; foreground clipping stays with its host.
class RecoveredCapturedSurfaceRenderer {
  RecoveredCapturedSurfaceRenderer._(
    this.recipe,
    this._reductions,
    this._material,
  );
  final RecoveredCapturedSurfaceRecipe recipe;
  final List<ui.FragmentShader> _reductions;
  final ui.FragmentShader _material;

  static Future<RecoveredCapturedSurfaceRenderer> create(
    RecoveredCapturedSurfaceRecipe recipe,
  ) async {
    final mip = await loadRecoveredProgram('glass_mip');
    final material = await loadRecoveredProgram(
      recipe.material['program'] as String,
    );
    return RecoveredCapturedSurfaceRenderer._(
      recipe,
      List.generate(recipe.levels.length, (_) => mip.fragmentShader()),
      material.fragmentShader(),
    );
  }

  RecoveredCapturedSurfaceFrame render(ui.Image background) {
    if (background.width != recipe.inputSize[0] ||
        background.height != recipe.inputSize[1]) {
      throw ArgumentError('Background must match the original capture size');
    }
    final levels = <ui.Image>[];
    try {
      var previous = background;
      for (var i = 0; i < recipe.levels.length; i++) {
        final stage = recipe.levels[i] as Map<String, dynamic>;
        final shader = _reductions[i];
        bindRecoveredFloats(
          shader,
          Map<String, Object?>.from(stage['fields'] as Map),
        );
        shader.setImageSampler(
          shader.getImageSampler('uTex').shaderIndex,
          previous,
          filterQuality: RecoveredCapturedSurfaceRecipe._quality(
            stage['sampling'] as int,
          ),
        );
        final size = (stage['output_size'] as List).cast<int>();
        final recorder = ui.PictureRecorder();
        ui.Canvas(recorder).drawRect(
          ui.Rect.fromLTWH(0, 0, size[0].toDouble(), size[1].toDouble()),
          ui.Paint()..shader = shader,
        );
        final picture = recorder.endRecording();
        try {
          previous = picture.toImageSync(
            size[0],
            size[1],
            targetFormat: ui.TargetPixelFormat.dontCare,
          );
          levels.add(previous);
        } finally {
          picture.dispose();
        }
      }
      final material = recipe.material;
      bindRecoveredFloats(
        _material,
        Map<String, Object?>.from(material['fields'] as Map),
      );
      for (final raw in material['samplers'] as List) {
        final binding = raw as Map<String, dynamic>;
        final slot = _material
            .getImageSampler(binding['sampler_name'] as String)
            .shaderIndex;
        if (slot != binding['slot']) {
          throw StateError(
            'Runtime sampler reflection differs from the original',
          );
        }
        _material.setImageSampler(
          slot,
          levels[binding['chain_level'] as int],
          filterQuality: RecoveredCapturedSurfaceRecipe._quality(
            binding['sampling'] as int,
          ),
        );
      }
      final nativePaint = material['paint'] as Map<String, dynamic>;
      final flags = nativePaint['flags_low13'] as int;
      if (((flags >> 5) & 3) != 0) {
        throw UnsupportedError('This captured surface requires a fill paint');
      }
      final argb = (nativePaint['argb_f32'] as List).cast<num>();
      final paint = ui.Paint()
        ..shader = _material
        ..blendMode = ui.BlendMode.values[flags & 31]
        ..isAntiAlias = (flags & (1 << 11)) != 0
        ..invertColors = (flags & (1 << 12)) != 0
        ..color = ui.Color.from(
          alpha: argb[0].toDouble(),
          red: argb[1].toDouble(),
          green: argb[2].toDouble(),
          blue: argb[3].toDouble(),
          colorSpace: ui.ColorSpace.values[nativePaint['color_space'] as int],
        );
      final rect = (material['rect'] as List).cast<num>();
      final recorder = ui.PictureRecorder();
      ui.Canvas(recorder)
        ..transform(
          Float64List.fromList(
            (material['matrix_column_major'] as List)
                .map((dynamic n) => (n as num).toDouble())
                .toList(),
          ),
        )
        ..drawRect(
          ui.Rect.fromLTRB(
            rect[0].toDouble(),
            rect[1].toDouble(),
            rect[2].toDouble(),
            rect[3].toDouble(),
          ),
          paint,
        );
      final picture = recorder.endRecording();
      try {
        final output = picture.toImageSync(
          recipe.outputSize[0],
          recipe.outputSize[1],
          targetFormat: ui
              .TargetPixelFormat
              .values[recipe.data['target_pixel_format_index'] as int],
        );
        return RecoveredCapturedSurfaceFrame(output, levels);
      } finally {
        picture.dispose();
      }
    } catch (_) {
      for (final level in levels) {
        level.dispose();
      }
      rethrow;
    }
  }

  void dispose() {
    for (final shader in _reductions) {
      shader.dispose();
    }
    _material.dispose();
  }
}
