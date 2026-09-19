# Changelog

## 2.0.0 - 2026-09-18

- Reworked speed estimation with rotation-vector based orientation, horizontal forward acceleration, adaptive Kalman filtering, and acceleration-variance stationary detection.
- Added manual pause, resume, stop/save, and configurable auto-pause foundations.
- Added Hilt, ViewModel, repository/use-case structure, StateFlow app state, Room ride history, DataStore settings, and GPX export.
- Added elevation gain/loss filtering, ride naming, notes, unit switching, theme mode preference, large speedometer UI, history screen, settings screen, and About screen.
- Improved foreground tracking notification with speed, distance, time, elevation, Pause/Resume, and Stop actions.
- Improved Osmdroid route rendering with follow mode and bearing-aware current marker.
- Added unit tests for `KalmanFilter` and `HybridSpeedCalculator`.
- Rewrote README, added MIT license, and refreshed `.gitignore`.

## 1.0.0

- Initial MVP with GPS tracking, basic sensor speed estimation, foreground service, speedometer, and map route display.
