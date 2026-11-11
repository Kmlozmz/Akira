import 'dart:ui';

/// Captured path-paint subset; filters, transforms and layer effects are separate.
class RecoveredPathPaintSpec {
  const RecoveredPathPaintSpec(
    this.flags,
    this.argb,
    this.colorSpace,
    this.strokeWidth,
    this.strokeMiter,
    this.hasColorSource,
  );
  final int flags;
  final List<double> argb;
  final int colorSpace;
  final double strokeWidth;
  final double strokeMiter;
  final bool hasColorSource;

  Paint toPaint({Color? color}) {
    if (hasColorSource)
      throw UnsupportedError('Native color source is required');
    final style = (flags >> 5) & 3;
    if (style > 1)
      throw UnsupportedError(
        'Native strokeAndFill requires a separate adapter',
      );
    return Paint()
      ..blendMode = BlendMode.values[flags & 31]
      ..style = style == 0 ? PaintingStyle.fill : PaintingStyle.stroke
      ..strokeCap = StrokeCap.values[(flags >> 7) & 3]
      ..strokeJoin = StrokeJoin.values[(flags >> 9) & 3]
      ..isAntiAlias = (flags & (1 << 11)) != 0
      ..invertColors = (flags & (1 << 12)) != 0
      ..strokeWidth = strokeWidth
      ..strokeMiterLimit = strokeMiter
      ..color =
          color ??
          Color.from(
            alpha: argb[0],
            red: argb[1],
            green: argb[2],
            blue: argb[3],
            colorSpace: ColorSpace.values[colorSpace],
          );
  }
}

const inotesObservedPathPaints = <String, List<RecoveredPathPaintSpec>>{
  '0fba6a5d8659': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '8e5e2e46173e': [
    RecoveredPathPaintSpec(
      2723,
      <double>[
        0.32999998331069946,
        0.23529411852359772,
        0.23529411852359772,
        0.26274511218070984,
      ],
      1,
      1.9199999570846558,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2723,
      <double>[
        0.32999998331069946,
        0.9215686321258545,
        0.9215686321258545,
        0.9607843160629272,
      ],
      1,
      1.9199999570846558,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2723,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      1.9199999570846558,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2723,
      <double>[
        0.6299999952316284,
        0.9215686321258545,
        0.9215686321258545,
        0.9607843160629272,
      ],
      1,
      1.9199999570846558,
      4.0,
      false,
    ),
  ],
  'b3aff182411f': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.33713626861572266, 0.34890028834342957],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.4318782687187195, 0.435730904340744],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.480512410402298, 0.4904627799987793],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '4abdb10f975a': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '4bb70c945ee3': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[
        0.6000000238418579,
        0.9215686321258545,
        0.9215686321258545,
        0.9607843160629272,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'eb04ab1e5fce': [
    RecoveredPathPaintSpec(
      2723,
      <double>[1.0, 0.0, 0.0, 0.0],
      1,
      2.5,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2723,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      2.5,
      4.0,
      false,
    ),
  ],
  '62b1a803db01': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'ffec3ca1340f': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '7a5cd9dcd8e2': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '7f0e066a4567': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '737efb8377e4': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '94f9f8839695': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '251e3626cdbc': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'f272a212fe61': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'cf6717ee3bd1': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '9c077634e158': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '37837094f890': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'b80f01e70d78': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '435ceacdb96a': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '03becb16f09c': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '58a63153a803': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'f9696bbfa285': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'aac99e90e15a': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '2c4626e6f520': [
    RecoveredPathPaintSpec(
      2051,
      <double>[0.19999998807907104, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '5362f3d6677e': [
    RecoveredPathPaintSpec(
      2723,
      <double>[
        0.6000000238418579,
        0.9215686321258545,
        0.9215686321258545,
        0.9607843160629272,
      ],
      1,
      1.3899999856948853,
      4.0,
      false,
    ),
  ],
  '161d0026e6bb': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '4e388e31671f': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'd3cd624a3915': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '2d0ba49aa8f2': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.772549033164978, 0.772549033164978, 0.7803921699523926],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '6438f0e428ed': [
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '716c9c64caa2': [
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '3c184f3fefcd': [
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'cb9032938a56': [
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'bc5bd1ec697e': [
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 0.0, 0.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
    RecoveredPathPaintSpec(
      2051,
      <double>[0.9599999785423279, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '529925d6d0c2': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '99fd4e318735': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '24ae79d644c7': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '579d4bab9d44': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'd59d65ed432f': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '5ce45c774367': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'a05a4cc1acf4': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'af93f21d9462': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '40d058de62ab': [
    RecoveredPathPaintSpec(
      2723,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      1.5,
      4.0,
      false,
    ),
  ],
  'a3b9061d7509': [
    RecoveredPathPaintSpec(
      2723,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      7.0,
      4.0,
      false,
    ),
  ],
  '05de4e4681f2': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'e42d4aeb51dc': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8941176533699036, 0.686274528503418, 0.03921568766236305],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'b4c3a9cfeeb6': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.95686274766922, 0.26274511218070984, 0.21176470816135406],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '779a075f261b': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.32156863808631897, 0.32156863808631897],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '4b7ff9e0a5e9': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.9137254953384399,
        0.11764705926179886,
        0.38823530077934265,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'b8e9e84a65eb': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.250980406999588, 0.5058823823928833],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '99555835a55d': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.6117647290229797,
        0.15294118225574493,
        0.6901960968971252,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'a28f07ae5980': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8784313797950745, 0.250980406999588, 0.9843137264251709],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'f26d03c752ad': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.40392157435417175,
        0.22745098173618317,
        0.7176470756530762,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'd7d803be4184': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.48627451062202454, 0.3019607961177826, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'ad715c450972': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.24705882370471954,
        0.3176470696926117,
        0.7098039388656616,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '6905fda51cf5': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.32549020648002625,
        0.4274509847164154,
        0.9960784316062927,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '915a579c4757': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.12941177189350128,
        0.5882353186607361,
        0.9529411792755127,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '3fc529cca2ba': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.2666666805744171, 0.5411764979362488, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '809eecdd6d64': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0117647061124444, 0.6627451181411743, 0.95686274766922],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '643f829e7eed': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.250980406999588, 0.7686274647712708, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '2f316d171b81': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0, 0.7372549176216125, 0.8313725590705872],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '734d543d14e9': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0941176488995552, 1.0, 1.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '47dd432e6535': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.0, 0.5882353186607361, 0.5333333611488342],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '9b04a5ea5c14': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.3921568691730499, 1.0, 0.8549019694328308],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'e4553ab0d2f3': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.2980392277240753, 0.686274528503418, 0.3137255012989044],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'e5e0b68844eb': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.4117647111415863, 0.9411764740943909, 0.6823529601097107],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'ba5a9872a0ea': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.545098066329956, 0.7647058963775635, 0.29019609093666077],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'f5ac73d49581': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.6980392336845398, 1.0, 0.3490196168422699],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'f0c1d320ff02': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8039215803146362, 0.8627451062202454, 0.2235294133424759],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '99f99489a60d': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.9333333373069763, 1.0, 0.2549019753932953],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '23d937767274': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.9215686321258545, 0.23137255012989044],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'aa77776448d2': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 1.0, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'a78628043aa0': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.7568627595901489, 0.027450980618596077],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'e146ef199cf0': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.843137264251709, 0.250980406999588],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '31ce7d264ede': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.5960784554481506, 0.0],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'b43346c5109c': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.6705882549285889, 0.250980406999588],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '631d58eb675b': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.34117648005485535, 0.13333334028720856],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'e9c66679a410': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.4313725531101227, 0.250980406999588],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'bb20a8d222e6': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.4745098054409027, 0.3333333432674408, 0.2823529541492462],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '1e6cee2a6435': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.3764705955982208, 0.4901960790157318, 0.545098066329956],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '6720fe7ba176': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.6196078658103943, 0.6196078658103943, 0.6196078658103943],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '0a7fa23da793': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.9215686321258545, 0.9333333373069763],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'a51e3d38c4a5': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 1.0, 0.8039215803146362, 0.8235294222831726],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'c8ffb6abd727': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.9372549057006836, 0.6039215922355652, 0.6039215922355652],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'fc817ee2098c': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.8980392217636108,
        0.45098039507865906,
        0.45098039507865906,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '362ac41cc17d': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.9372549057006836,
        0.32549020648002625,
        0.3137255012989044,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '753feb42066a': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.95686274766922, 0.26274511218070984, 0.21176470816135406],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  '6f3aff67a265': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.8980392217636108, 0.2235294133424759, 0.2078431397676468],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'a7d5309532d4': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.8274509906768799,
        0.18431372940540314,
        0.18431372940540314,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'c2bde3b64224': [
    RecoveredPathPaintSpec(
      2051,
      <double>[1.0, 0.7764706015586853, 0.1568627506494522, 0.1568627506494522],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
  'f32174bcc0d6': [
    RecoveredPathPaintSpec(
      2051,
      <double>[
        1.0,
        0.7176470756530762,
        0.10980392247438431,
        0.10980392247438431,
      ],
      1,
      0.0,
      4.0,
      false,
    ),
  ],
};
