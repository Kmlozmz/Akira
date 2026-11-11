import 'dart:convert';
import 'dart:ui' as ui;
import 'package:flutter/services.dart';
import 'package:inotes_design_reference/inotes_design_reference.dart'
    as recovered;
import 'native_pane_motion.dart';

class PaneResources {
  PaneResources({
    required this.recipe,
    required this.programs,
    required this.interactions,
    required this.light,
    required this.palette,
    required this.motion,
  });

  final recovered.RecoveredCapturedSurfaceRecipe recipe;
  final List<ui.FragmentProgram> programs;
  final Map<String, dynamic> interactions, light, palette;
  final NativePaneMotion motion;
  static Future<PaneResources>? _loaded;
  static Future<PaneResources> load() => _loaded ??= _read();

  static Future<Map<String, dynamic>> _json(String asset) async =>
      jsonDecode(await rootBundle.loadString(asset)) as Map<String, dynamic>;

  static Future<PaneResources> _read() async {
    final recipe = recovered.RecoveredCapturedSurfaceRecipe.loadMore();
    final programs = Future.wait([
      recovered.loadRecoveredProgram('glass_lean'),
      recovered.loadRecoveredProgram('glass_mip'),
      recovered.loadRecoveredProgram('glass_lean_morph'),
    ]);
    final interactions = _json('assets/review/interaction-recipes.json');
    final light = _json('assets/recovered_components/light.json');
    final palette = _json('assets/review/palette-native.json');
    final motion = NativePaneMotion.load();
    final loaded = await (
      recipe,
      programs,
      interactions,
      light,
      palette,
      motion,
    ).wait;
    return PaneResources(
      recipe: loaded.$1,
      programs: loaded.$2,
      interactions: loaded.$3,
      light: loaded.$4,
      palette: loaded.$5,
      motion: loaded.$6,
    );
  }
}
