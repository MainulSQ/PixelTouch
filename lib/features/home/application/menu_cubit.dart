import 'package:flutter_bloc/flutter_bloc.dart';

import '../data/android_device_gateway.dart';

class MenuState {
  const MenuState({this.controls = const [], this.loading = true});
  final List<MenuControl> controls;
  final bool loading;
}

class MenuCubit extends Cubit<MenuState> {
  MenuCubit(this._device) : super(const MenuState());
  final AndroidDeviceGateway _device;

  Future<void> load() async =>
      emit(MenuState(controls: await _device.menuControls(), loading: false));

  Future<void> setVisible(MenuControl control, bool visible) async {
    await _device.setMenuControlVisible(control.id, visible);
    emit(
      MenuState(
        loading: false,
        controls: state.controls
            .map(
              (item) => item.id == control.id
                  ? MenuControl(
                      id: item.id,
                      label: item.label,
                      visible: visible,
                    )
                  : item,
            )
            .toList(),
      ),
    );
  }
}
