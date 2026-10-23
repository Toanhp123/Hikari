import 'package:flutter/material.dart';

class HikariMediaProgress extends StatelessWidget {
  const HikariMediaProgress({super.key, required this.progress});
  final double progress;
  @override
  Widget build(BuildContext context) {
    final normalized = progress.isNaN ? 0.0 : progress.clamp(0.0, 1.0);
    return LinearProgressIndicator(
      value: normalized,
      minHeight: 3.5,
      semanticsLabel: 'Reading progress',
      semanticsValue: '${(normalized * 100).round()}%',
    );
  }
}
