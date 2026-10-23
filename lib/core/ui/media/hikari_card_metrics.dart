import 'package:flutter/material.dart';

final _lineHeights =
    <
      ({
        TextStyle style,
        TextScaler scaler,
        TextDirection direction,
        int lines,
      }),
      double
    >{};

TextStyle hikariCardTitleStyle(BuildContext context) =>
    Theme.of(context).textTheme.labelMedium!
        .copyWith(fontWeight: FontWeight.w600, height: 1.25);

TextStyle hikariCardSubtitleStyle(BuildContext context) =>
    Theme.of(context).textTheme.labelSmall!
        .copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant);

// Fixed line reserves are shared by a viewport, not measured per media item.
// Bounded to 24 typography/scaler/direction combinations, as in legacy posters.
double _height(BuildContext context, TextStyle style, int lines) {
  final key = (
    style: style,
    scaler: MediaQuery.textScalerOf(context),
    direction: Directionality.of(context),
    lines: lines,
  );
  final cached = _lineHeights[key];
  if (cached != null) return cached;
  final painter = TextPainter(
    text: TextSpan(text: List.filled(lines, 'HgÁع').join('\n'), style: style),
    textDirection: key.direction,
    textScaler: key.scaler,
    maxLines: lines,
  )..layout();
  final result = painter.height.ceilToDouble();
  painter.dispose();
  if (_lineHeights.length >= 24) _lineHeights.clear();
  _lineHeights[key] = result;
  return result;
}

double hikariCardTitleHeight(BuildContext context) =>
    _height(context, hikariCardTitleStyle(context), 2);

double hikariCardExtent(
  BuildContext context, {
  required double width,
  bool hasSubtitle = false,
  double aspectRatio = 2 / 3,
}) =>
    width / aspectRatio +
    4 +
    hikariCardTitleHeight(context) +
    (hasSubtitle
        ? 2 + _height(context, hikariCardSubtitleStyle(context), 1)
        : 0);
