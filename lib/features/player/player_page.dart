import 'dart:async';

import 'package:flutter/material.dart';

class PlayerPage extends StatefulWidget {
  const PlayerPage({
    super.key,
    required this.title,
    required this.playback,
    this.beforeExit,
  });

  final String title;
  final Widget playback;
  final Future<void> Function()? beforeExit;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  bool _exiting = false;
  bool _canPop = false;

  Future<void> _exit() async {
    if (_exiting) return;
    _exiting = true;
    try {
      await widget.beforeExit?.call();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save playback progress.')),
        );
      }
    }
    if (!mounted) return;
    setState(() => _canPop = true);
    // PopScope must publish eligibility before the final pop notification.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && ModalRoute.of(context)!.isCurrent) {
        Navigator.of(context).pop();
      }
    });
  }

  @override
  void dispose() {
    // Forced removal only; normal back awaits the same idempotent callback.
    if (!_exiting) {
      unawaited(widget.beforeExit?.call().catchError((Object _) {}));
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PopScope<void>(
    canPop: _canPop,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop) _exit();
    },
    child: Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: SafeArea(child: Center(child: widget.playback)),
    ),
  );
}
