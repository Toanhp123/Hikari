import 'dart:async';

import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

class LocalVideo extends StatefulWidget {
  const LocalVideo({super.key, required this.locator});

  final String locator;

  @override
  State<LocalVideo> createState() => _LocalVideoState();
}

class _LocalVideoState extends State<LocalVideo> with WidgetsBindingObserver {
  Player? _player;
  VideoController? _controller;
  StreamSubscription<String>? _errors;
  String? _error;
  bool _opening = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _open();
  }

  Future<void> _open() async {
    try {
      MediaKit.ensureInitialized();
      final player = _player ??= Player();
      _controller ??= VideoController(player);
      _errors ??= player.stream.error.listen((message) {
        if (mounted) {
          setState(() {
            _error = message;
            _opening = false;
          });
        }
      });
      await player.open(Media(widget.locator));
      if (mounted) setState(() => _opening = false);
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = error.toString();
          _opening = false;
        });
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      final player = _player;
      if (player != null) unawaited(player.pause());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_errors?.cancel());
    final player = _player;
    if (player != null) unawaited(player.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Could not play this video.'),
              const SizedBox(height: 8),
              Text(_error!),
              TextButton(
                onPressed: _opening
                    ? null
                    : () {
                        setState(() {
                          _opening = true;
                          _error = null;
                        });
                        _open();
                      },
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
      );
    }
    final controller = _controller;
    if (controller == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return Stack(
      fit: StackFit.expand,
      children: [
        Video(controller: controller),
        if (_opening) const Center(child: CircularProgressIndicator()),
      ],
    );
  }
}
