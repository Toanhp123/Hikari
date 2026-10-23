import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/feedback/hikari_async_state_view.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';

typedef ReaderLaunch = ({
  Widget reader,
  VoidCallback abandon,
  Future<void> settled,
});

/// The navigation destination exists before remote chapter loading starts.
/// On a cache miss, loading and failures belong to this route, not Home.
class ContinueReadingPage extends StatefulWidget {
  const ContinueReadingPage({super.key, required this.open});

  final Future<ReaderLaunch> Function() open;

  @override
  State<ContinueReadingPage> createState() => _ContinueReadingPageState();
}

class _ContinueReadingPageState extends State<ContinueReadingPage> {
  Widget? _reader;
  Object? _error;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_resolve(++_generation));
  }

  void _load() {
    final generation = ++_generation;
    setState(() {
      _error = null;
      _reader = null;
    });
    unawaited(_resolve(generation));
  }

  Future<void> _resolve(int generation) async {
    try {
      final ready = await widget.open();
      if (!mounted || generation != _generation) {
        ready.abandon();
        return;
      }
      setState(() => _reader = ready.reader);
    } catch (error) {
      if (mounted && generation == _generation) {
        setState(() => _error = error);
      }
    }
  }

  @override
  void dispose() {
    ++_generation;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final reader = _reader;
    if (reader != null) return reader;
    return HikariPageScaffold(
      appBar: AppBar(title: const Text('Continue reading')),
      body: HikariContentRegion(
        child: HikariAsyncStateView(
          status: _error == null
              ? HikariAsyncStatus.loading
              : HikariAsyncStatus.fatalError,
          message: _error == null ? null : 'Could not load this chapter.',
          onRetry: _error == null ? null : _load,
          child: const SizedBox.shrink(),
        ),
      ),
    );
  }
}
