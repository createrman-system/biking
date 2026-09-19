# Biking

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Repository: [https://github.com/createrman-system/biking](https://github.com/createrman-system/biking)

Biking is a modern Android bike-tracking app focused on stable speed, clear ride stats, local history, and exportable ride data. It combines GPS with phone motion sensors so the speedometer stays responsive without jumping when the phone is in a pocket, bag, or angled mount.

## Features

- Hybrid speed tracking with GPS, rotation vector, linear acceleration, and an adaptive Kalman filter
- Robust moving / stopped detection with manual pause and auto-pause
- Foreground tracking service with live speed, distance, time, elevation, Pause, Resume, and Stop actions
- Large sunlight-friendly speedometer with landscape support
- Osmdroid map with route polyline, current position, follow mode, and bearing arrow
- Local ride history powered by Room
- Elevation gain and loss with smoothed GPS altitude
- GPX export with elevation, timestamps, and speed extensions
- Unit switch: km/h or mph
- Theme preference: light, dark, or system
- Ride name and note
- About screen with version and project link

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Kotlin Coroutines and StateFlow
- Hilt dependency injection
- Room local database
- DataStore Preferences
- Android foreground service
- Android location and sensor APIs
- Osmdroid OpenStreetMap rendering

## Build and Run

```bash
git clone https://github.com/createrman-system/biking.git
cd biking
./gradlew assembleDebug
```

Open the project in Android Studio, select the `app` run configuration, and run it on a physical Android device. Real GPS and motion sensors are required for meaningful tracking.

## Required Permissions

- Fine and coarse location: route, distance, speed, elevation, and map position
- Foreground service location: reliable tracking while the app is in the background
- Notifications: live tracking notification on Android 13+
- Internet and network state: OpenStreetMap tile loading

## How Hybrid Tracking Works

Biking reads GPS speed and location as the long-term ground truth. Between GPS updates it uses `TYPE_ROTATION_VECTOR` to understand phone orientation and `TYPE_LINEAR_ACCELERATION` to convert motion into horizontal, forward acceleration relative to the current course. An adaptive Kalman filter predicts short-term speed from acceleration and corrects drift with GPS, while a stationary detector combines low GPS speed with low acceleration variance to suppress false movement and trigger auto-pause.

## Privacy

All ride data is stored only locally on your device. Biking does not upload rides, locations, notes, or sensor data to any server. GPX files are created locally only when you export or share them.

## Contributing

Issues and pull requests are welcome:

- [Open an issue](https://github.com/createrman-system/biking/issues)
- [View the repository](https://github.com/createrman-system/biking)

## License

MIT. See [LICENSE](LICENSE).
