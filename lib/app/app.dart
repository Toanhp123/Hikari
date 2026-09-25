import 'package:flutter/material.dart';

class HikariApp extends StatelessWidget {
  const HikariApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: 'Hikari',
      home: Scaffold(body: Center(child: Text('Hikari'))),
    );
  }
}
