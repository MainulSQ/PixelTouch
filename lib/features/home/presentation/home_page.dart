import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../application/home_cubit.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: BlocConsumer<HomeCubit, HomeState>(
        listener: (context, state) {
          if (state.message != null)
            ScaffoldMessenger.of(
              context,
            ).showSnackBar(SnackBar(content: Text(state.message!)));
        },
        builder: (context, state) {
          final s = state.status;
          return RefreshIndicator(
            onRefresh: context.read<HomeCubit>().refresh,
            child: ListView(
              padding: const EdgeInsets.all(20),
              children: [
                const Text(
                  'PixelTouch',
                  style: TextStyle(
                    fontSize: 32,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -1,
                  ),
                ),
                const SizedBox(height: 4),
                const Text(
                  'Assistive controls for your Pixel',
                  style: TextStyle(color: Color(0xFFB9C0C8)),
                ),
                const SizedBox(height: 24),
                _BubbleCard(running: s?.running == true),
                const SizedBox(height: 24),
                const Text(
                  'Permissions',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 10),
                _PermissionTile(
                  'Floating bubble',
                  'Required to draw above other apps',
                  Icons.touch_app_rounded,
                  s?.overlay == true,
                  'openOverlayPermission',
                ),
                _PermissionTile(
                  'Sound control',
                  'Do Not Disturb access',
                  Icons.volume_up_rounded,
                  s?.dnd == true,
                  'openDndSettings',
                ),
                _PermissionTile(
                  'Hotspot control',
                  'Location permission',
                  Icons.location_on_rounded,
                  s?.location == true,
                  'requestLocation',
                ),
                _PermissionTile(
                  'Notifications',
                  'Keeps the bubble alive',
                  Icons.notifications_rounded,
                  s?.notifications == true,
                  'requestNotifications',
                ),
                const SizedBox(height: 16),
                OutlinedButton.icon(
                  onPressed: () => context.read<HomeCubit>().perform(
                    'openAccessibility',
                    'Enable PixelTouch Accessibility for screenshots',
                  ),
                  icon: const Icon(Icons.screenshot_monitor_rounded),
                  label: const Text('Set up screenshot accessibility'),
                ),
              ],
            ),
          );
        },
      ),
    ),
  );
}

class _BubbleCard extends StatelessWidget {
  const _BubbleCard({required this.running});
  final bool running;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(20),
    decoration: BoxDecoration(
      color: const Color(0xFF24262B),
      borderRadius: BorderRadius.circular(28),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: const BoxDecoration(
                color: Color(0xFFF4F5F5),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.touch_app_rounded,
                color: Color(0xFF1D1E22),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Text(
                running
                    ? 'Floating bubble is active'
                    : 'Floating bubble is stopped',
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),
        FilledButton.icon(
          onPressed: () => context.read<HomeCubit>().perform(
            running ? 'stopBubble' : 'startBubble',
            running ? 'Bubble stopped' : 'Bubble started',
          ),
          icon: Icon(running ? Icons.stop_rounded : Icons.play_arrow_rounded),
          label: Text(running ? 'Stop bubble' : 'Start floating bubble'),
        ),
      ],
    ),
  );
}

class _PermissionTile extends StatelessWidget {
  const _PermissionTile(
    this.title,
    this.detail,
    this.icon,
    this.granted,
    this.method,
  );
  final String title, detail, method;
  final IconData icon;
  final bool granted;
  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 10),
    child: ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(detail),
      trailing: Chip(label: Text(granted ? 'Granted' : 'Set up')),
      onTap: granted
          ? null
          : () => context.read<HomeCubit>().perform(
              method,
              'Complete setup in Android settings',
            ),
    ),
  );
}
