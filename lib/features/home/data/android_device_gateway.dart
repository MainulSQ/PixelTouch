import 'package:flutter/services.dart';

class DeviceStatus {
  const DeviceStatus({
    required this.overlay,
    required this.dnd,
    required this.location,
    required this.notifications,
    required this.running,
  });
  final bool overlay, dnd, location, notifications, running;

  factory DeviceStatus.fromMap(Map<Object?, Object?> values) => DeviceStatus(
    overlay: values['overlay'] == true,
    dnd: values['dnd'] == true,
    location: values['location'] == true,
    notifications: values['notifications'] == true,
    running: values['running'] == true,
  );
}

/// The only Flutter-to-Android boundary. Device controls remain native.
class AndroidDeviceGateway {
  static const _channel = MethodChannel('com.shihan.pixeltouch/device');
  Future<DeviceStatus> status() async => DeviceStatus.fromMap(
    await _channel.invokeMapMethod<Object?, Object?>('status') ?? const {},
  );
  Future<void> invoke(String method) => _channel.invokeMethod<void>(method);
}
