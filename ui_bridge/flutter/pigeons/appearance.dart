import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(PigeonOptions(
  dartOut: 'lib/bridge/appearance.g.dart',
  kotlinOut: '../../app/src/main/kotlin/com/akira/app/feature/appearance/Appearance.g.kt',
  kotlinOptions: KotlinOptions(package: 'com.akira.app.feature.appearance'),
))
class VisualPreferences {
  VisualPreferences({required this.revision, required this.theme,
    required this.accentArgb, required this.glass, required this.font,
    required this.fontName, required this.sizeIndex,
    required this.reduceTransparency, required this.reduceMotion,
    required this.fullscreenArtwork, required this.animatedArtwork,
    required this.glassOpacity, required this.diffusion,
    required this.dockDiffusion, required this.locale});
  int revision;
  String theme;
  int accentArgb;
  bool glass;
  String font;
  String fontName;
  int sizeIndex;
  bool reduceTransparency;
  bool reduceMotion;
  bool fullscreenArtwork;
  bool animatedArtwork;
  double glassOpacity;
  double diffusion;
  bool dockDiffusion;
  String locale;
  String? fontId;
}

class MusicPreview {
  MusicPreview({required this.id, required this.title, required this.artist,
    required this.album, required this.durationMs, this.artwork});
  String id;
  String title;
  String artist;
  String album;
  int durationMs;
  Uint8List? artwork;
}

class FontData {
  FontData({required this.name, required this.bytes});
  String name;
  Uint8List bytes;
  String? id;
}

@HostApi()
abstract class AppearanceHost {
  @asyncCallback
  VisualPreferences read();
  @asyncCallback
  VisualPreferences apply(VisualPreferences preferences);
  @asyncCallback
  List<MusicPreview> preview();
  @asyncCallback
  FontData? pickFont();
  @asyncCallback
  FontData? readFont();
  void close();
}

@FlutterApi()
abstract class AppearanceClient {
  void preferencesChanged(VisualPreferences preferences);
  void refresh();
}
