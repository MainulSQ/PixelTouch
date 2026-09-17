import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import 'features/home/application/home_cubit.dart';
import 'features/home/data/android_device_gateway.dart';
import 'features/home/presentation/home_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const PixelTouchApp());
}

class PixelTouchApp extends StatelessWidget {
  const PixelTouchApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'PixelTouch',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xFF8E9BA6),
        brightness: Brightness.dark,
      ),
    ),
    home: BlocProvider(
      create: (_) => HomeCubit(AndroidDeviceGateway())..refresh(),
      child: const HomePage(),
    ),
  );
}
