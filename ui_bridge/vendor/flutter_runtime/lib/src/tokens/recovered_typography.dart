import 'package:flutter/painting.dart';

class RecoveredTextSpec {
  const RecoveredTextSpec({required this.families, required this.size,
    required this.weight, required this.italic, required this.height,
    required this.letterSpacing, required this.wordSpacing,
    required this.features, required this.variations, required this.capturedColor});

  final List<String> families;
  final double size;
  final int weight;
  final bool italic;
  final double? height;
  final double letterSpacing;
  final double wordSpacing;
  final Map<String,int> features;
  final Map<String,double> variations;
  final Color capturedColor;

  TextStyle toTextStyle({required Color color, double? fontSize}) => TextStyle(
    fontFamily: families.first,
    fontFamilyFallback: families.skip(1).toList(),
    fontSize: fontSize ?? size,
    fontWeight: FontWeight.values[weight ~/ 100 - 1],
    fontStyle: italic ? FontStyle.italic : FontStyle.normal,
    height: height,
    letterSpacing: letterSpacing,
    wordSpacing: wordSpacing,
    fontFeatures: [for(final entry in features.entries) FontFeature(entry.key,entry.value)],
    fontVariations: [for(final entry in variations.entries) FontVariation(entry.key,entry.value)],
    color: color,
  );
}

// IDs belong to one native capture; they are not global component identifiers.
const inotesNativeTextSpecs = <int, RecoveredTextSpec>{
  0: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 20.0, weight: 600, italic: false, height: 1.25, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  1: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: null, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  2: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: null, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xa13c3c43)),
  3: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesMaterial"], size: 26.0, weight: 400, italic: false, height: 1.0, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{"GRAD": 0.0, "wght": 400.0, "opsz": 48.0, "FILL": 0.0}, capturedColor: Color(0xffe4af0a)),
  4: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: null, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xffe4af0a)),
  5: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 34.0, weight: 700, italic: false, height: null, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  6: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 600, italic: false, height: 1.2941176470588236, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  7: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 15.0, weight: 400, italic: false, height: 1.3333333333333333, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0x993c3c43)),
  8: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 600, italic: false, height: null, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  9: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 500, italic: false, height: 1.2941176470588236, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  10: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 500, italic: false, height: 1.2941176470588236, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff979797)),
  11: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesCupertino"], size: 20.7, weight: 400, italic: false, height: 1.0, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{"GRAD": 0.0, "wght": 400.0, "opsz": 48.0, "FILL": 0.0}, capturedColor: Color(0xff000000)),
  12: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 15.0, weight: 600, italic: false, height: 1.3333333333333333, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  13: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 15.0, weight: 400, italic: false, height: 1.3333333333333333, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0x993c3c43)),
  14: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 12.0, weight: 600, italic: false, height: 1.3333333333333333, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xffffffff)),
  15: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 12.0, weight: 400, italic: false, height: 1.3333333333333333, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xffffffff)),
  16: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 15.0, weight: 400, italic: false, height: null, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff8e8e93)),
  17: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 28.0, weight: 400, italic: false, height: 1.3298321428571427, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  18: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 28.0, weight: 700, italic: false, height: 1.3298321428571427, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  19: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  20: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.48, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff9e9e9e)),
  21: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xffe4af0a)),
  22: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 700, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  23: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff8e8e93)),
  24: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 600, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff8e8e93)),
  25: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 600, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  26: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 7.0, weight: 400, italic: false, height: 1.411764705882353, letterSpacing: 0.25, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  27: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.411764705882353, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  28: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 600, italic: false, height: 1.411764705882353, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  29: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: true, height: 1.411764705882353, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  30: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.411764705882353, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xffe4af0a)),
  31: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.48, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  32: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 14.0, weight: 400, italic: false, height: 1.43, letterSpacing: 0.25, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  33: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.2941176470588236, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
  34: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xffcb30e0)),
  35: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xfffe2c54)),
  36: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xffff8c27)),
  37: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff00c8b3)),
  38: RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 400, italic: false, height: 1.5294117647058822, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff0088ff)),
};

const inotesVerifiedTextRoles = <String, RecoveredTextSpec>{
  "notes-header": RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 34.0, weight: 700, italic: false, height: null, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  "note-row-title": RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 17.0, weight: 600, italic: false, height: 1.2941176470588236, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff000000)),
  "editor-h1": RecoveredTextSpec(families: <String>["packages/inotes_recovered/InotesPretendard"], size: 28.0, weight: 700, italic: false, height: 1.3298321428571427, letterSpacing: 0.0, wordSpacing: 0.0, features: <String, int>{"cv11": 1}, variations: <String, double>{}, capturedColor: Color(0xff454545)),
};
