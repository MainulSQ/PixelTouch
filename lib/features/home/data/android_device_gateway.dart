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

class MenuControl {
  const MenuControl({
    required this.id,
    required this.label,
    required this.visible,
  });
  final int id;
  final String label;
  final bool visible;

  factory MenuControl.fromMap(Map<Object?, Object?> values) => MenuControl(
    id: values['id'] as int,
    label: values['label'] as String,
    visible: values['visible'] == true,
  );
}

/// The only Flutter-to-Android boundary. Device controls remain native.
class AndroidDeviceGateway {
  static const _channel = MethodChannel('com.shihan.pixeltouch/device');
  Future<DeviceStatus> status() async => DeviceStatus.fromMap(
    await _channel.invokeMapMethod<Object?, Object?>('status') ?? const {},
  );
  Future<void> invoke(String method) => _channel.invokeMethod<void>(method);

  Future<List<MenuControl>> menuControls() async {
    final values =
        await _channel.invokeListMethod<Object?>('menuControls') ?? const [];
    return values
        .whereType<Map<Object?, Object?>>()
        .map(MenuControl.fromMap)
        .toList();
  }

  Future<void> setMenuControlVisible(int id, bool visible) =>
      _channel.invokeMethod<void>('setMenuControlVisible', {
        'id': id,
        'visible': visible,
      });
}
