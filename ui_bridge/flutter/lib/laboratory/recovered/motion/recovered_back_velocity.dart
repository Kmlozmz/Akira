class RecoveredBackVelocity {
  RecoveredBackVelocity({
    required this.lastProcessedTime,
    required this.lastSeenTime,
    required this.position,
    required this.primaryVelocity,
    required this.clampedAcceleration,
    required this.averageVelocity,
    required this.averageAcceleration,
    required this.count,
    required this.boost,
    required this.shortSampleFlag,
    required List<double> velocityRing,
    required List<double> accelerationRing,
  }) : velocityRing = List.of(velocityRing),
       accelerationRing = List.of(accelerationRing) {
    if (velocityRing.length != 3 || accelerationRing.length != 3) {
      throw ArgumentError('Native tracker rings require three entries');
    }
  }

  double lastProcessedTime, lastSeenTime, position, primaryVelocity;
  double clampedAcceleration, averageVelocity, averageAcceleration;
  int count;
  bool boost, shortSampleFlag;
  final List<double> velocityRing, accelerationRing;

  void reset(double time, double nextPosition, double velocity) {
    lastProcessedTime = lastSeenTime = time;
    position = nextPosition;
    velocityRing[0] = primaryVelocity = averageVelocity = velocity;
    accelerationRing[0] = clampedAcceleration = averageAcceleration = 0;
    count = 1;
    boost = true;
    shortSampleFlag = false;
  }

  bool update(double time, double nextPosition, double velocity, bool flag) {
    shortSampleFlag = shortSampleFlag || flag;
    final gap = time - lastSeenTime;
    lastSeenTime = time;
    if (gap < .01 && (gap <= .001 || nextPosition == position)) return false;
    final dt = time - lastProcessedTime;
    final index = count % 3;
    averageVelocity = count < 3
        ? (velocity + averageVelocity * count) / (count + 1)
        : (velocity + averageVelocity * 3 - velocityRing[index]) / 3;
    velocityRing[index] = velocity;
    if (boost && averageVelocity.abs() < 1.5) boost = false;
    final acceleration = (velocity - primaryVelocity) / dt;
    final clamped = acceleration
        .clamp(clampedAcceleration - 5, clampedAcceleration + 5)
        .toDouble();
    averageAcceleration = count < 3
        ? (clamped + averageAcceleration * count) / (count + 1)
        : (clamped + averageAcceleration * 3 - accelerationRing[index]) / 3;
    clampedAcceleration = clamped;
    accelerationRing[index] = clamped;
    primaryVelocity = velocity;
    position = nextPosition;
    lastProcessedTime = time;
    count++;
    return true;
  }

  Map<String, Object> toNativeState() => {
    'last_processed_time': lastProcessedTime,
    'last_seen_time': lastSeenTime,
    'position': position,
    'primary_velocity': primaryVelocity,
    'clamped_acceleration': clampedAcceleration,
    'average_velocity': averageVelocity,
    'average_acceleration': averageAcceleration,
    'count': count,
    'boost': boost,
    'short_sample_flag': shortSampleFlag,
    'velocity_ring': List<double>.of(velocityRing),
    'acceleration_ring': List<double>.of(accelerationRing),
  };
}
