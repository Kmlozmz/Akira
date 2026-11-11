import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui';
import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import '../recovered/regional_capture_stage.dart' show RegionalCaptureMetrics;

/// Bounded profile-only evidence. No user text or settings are recorded.
class ReviewFrameAudit {
  static final frames = <List<Object>>[];
  static final motion = <List<Object>>[];
  static final surfaces = <String, Map<String, Object?>>{};
  static void surface(String name, Map<String, Object?> fields) {
    if (kProfileMode && (surfaces.containsKey(name) || surfaces.length < 16)) {
      surfaces[name] = fields;
    }
  }

  static Timer? _timer;
  static String phase = 'startup';
  static int _written = -1;
  static bool _writing = false;
  static void start() {
    if (!kProfileMode || _timer != null) return;
    SchedulerBinding.instance.addTimingsCallback(_timings);
    _timer = Timer.periodic(const Duration(seconds: 2), (_) => _flush());
  }

  static void stop() {
    if (_timer == null) return;
    _timer!.cancel();
    _timer = null;
    SchedulerBinding.instance.removeTimingsCallback(_timings);
  }

  static void begin(String value) {
    if (kProfileMode) phase = value;
  }

  static void sample(
    String name,
    double value,
    double target,
    double velocity,
  ) {
    if (!kProfileMode) return;
    motion.add([
      SchedulerBinding.instance.currentSystemFrameTimeStamp.inMicroseconds,
      name,
      value,
      target,
      velocity,
    ]);
    if (motion.length > 8000) motion.removeRange(0, motion.length - 8000);
  }

  static void _timings(List<FrameTiming> batch) {
    for (final f in batch) {
      frames.add([
        f.frameNumber,
        phase,
        f.buildDuration.inMicroseconds,
        f.rasterDuration.inMicroseconds,
        f.totalSpan.inMicroseconds,
        f.timestampInMicroseconds(FramePhase.vsyncStart),
        RegionalCaptureMetrics.backgrounds,
        RegionalCaptureMetrics.lenses,
        RegionalCaptureMetrics.paneMipChains,
        RegionalCaptureMetrics.headerMipChains,
      ]);
    }
    if (frames.length > 1200) frames.removeRange(0, frames.length - 1200);
  }

  static Future<void> _flush() async {
    if (_writing || frames.isEmpty || _written == frames.last.first) return;
    // Keep profile serialization out of the animation being measured.
    if (SchedulerBinding.instance.transientCallbackCount != 0 ||
        SchedulerBinding.instance.hasScheduledFrame)
      return;
    _writing = true;
    try {
      final lastEncodedFrame = frames.last.first as int;
      final data = jsonEncode({
        'frame_columns': [
          'number',
          'phase',
          'build_us',
          'raster_us',
          'total_us',
          'vsync_start_us',
          'background_captures',
          'lens_captures',
          'pane_mip_chains',
          'header_mip_chains',
        ],
        'motion_columns': [
          'system_frame_us',
          'property',
          'value',
          'target',
          'velocity',
        ],
        'frames': frames,
        'motion': motion,
        'surfaces': surfaces,
      });
      final path = '${Directory.systemTemp.path}/rea-review-frames-v11.json';
      final pending = File('$path.pending');
      await pending.writeAsString(data);
      await pending.rename(path);
      _written = lastEncodedFrame;
    } finally {
      _writing = false;
    }
  }
}
