import 'package:flutter_bloc/flutter_bloc.dart';
import '../data/android_device_gateway.dart';

class HomeState {
  const HomeState({this.status, this.loading = false, this.message});
  final DeviceStatus? status;
  final bool loading;
  final String? message;
  HomeState copyWith({DeviceStatus? status, bool? loading, String? message}) =>
      HomeState(
        status: status ?? this.status,
        loading: loading ?? this.loading,
        message: message,
      );
}

class HomeCubit extends Cubit<HomeState> {
  HomeCubit(this._device) : super(const HomeState(loading: true));
  final AndroidDeviceGateway _device;
  Future<void> refresh() async {
    emit(state.copyWith(loading: true));
    try {
      emit(HomeState(status: await _device.status()));
    } catch (_) {
      emit(
        state.copyWith(
          loading: false,
          message: 'Could not read Android permissions.',
        ),
      );
    }
  }

  Future<void> perform(String method, String message) async {
    await _device.invoke(method);
    emit(state.copyWith(message: message));
  }
}
