import 'package:flutter/material.dart';

import '../foundation/hikari_size.dart';
import '../foundation/hikari_spacing.dart';

/// Controlled single selection; local constraints choose wrapping or vertical
/// presentation without shrinking targets or dropping translated labels.
class HikariSegmentedControl<T> extends StatelessWidget {
  const HikariSegmentedControl({
    super.key,
    required this.segments,
    required this.value,
    required this.onChanged,
  });

  final List<ButtonSegment<T>> segments;
  final T value;
  final ValueChanged<T>? onChanged;

  @override
  Widget build(BuildContext context) {
    if (segments.isEmpty) return const SizedBox.shrink();
    return LayoutBuilder(
      builder: (context, constraints) {
        final text = Theme.of(context).textTheme.labelLarge!;
        var requiredWidth = 0.0;
        for (final segment in segments) {
          final label = segment.label;
          if (label is! Text || label.data == null) {
            requiredWidth = double.infinity;
            break;
          }
          final painter = TextPainter(
            text: TextSpan(text: label.data, style: text.merge(label.style)),
            textDirection: label.textDirection ?? Directionality.of(context),
            textScaler: label.textScaler ?? MediaQuery.textScalerOf(context),
          )..layout();
          requiredWidth +=
              painter.width +
              HikariSpacing.xxl +
              (segment.icon == null ? 0 : HikariSize.icon);
          painter.dispose();
        }
        return SegmentedButton<T>(
          segments: [
            for (final segment in segments)
              ButtonSegment<T>(
                value: segment.value,
                enabled: segment.enabled,
                icon: segment.icon,
                tooltip: segment.tooltip,
                label: segment.label == null
                    ? null
                    : ConstrainedBox(
                        constraints: BoxConstraints(
                          maxWidth: constraints.maxWidth.isFinite
                              ? (constraints.maxWidth -
                                        HikariSpacing.xxl -
                                        HikariSize.icon)
                                    .clamp(0, double.infinity)
                              : double.infinity,
                        ),
                        child: segment.label,
                      ),
              ),
          ],
          selected: {value},
          showSelectedIcon: false,
          direction: requiredWidth > constraints.maxWidth
              ? Axis.vertical
              : Axis.horizontal,
          onSelectionChanged: onChanged == null
              ? null
              : (values) => onChanged!(values.single),
          style: const ButtonStyle(
            minimumSize: WidgetStatePropertyAll(
              Size(HikariSize.touchTarget, HikariSize.touchTarget),
            ),
          ),
        );
      },
    );
  }
}
