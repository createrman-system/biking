# 🚴 Biking - High Precision Tracker

![Android](https://img.shields.io/badge/Platform-Android-brightgreen.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-orange.svg)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)
![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)

**Biking** is a high-precision speedometer and route tracking application for Android. It combines GPS data with device sensors using advanced filtering techniques to provide smooth, real-time speed and location tracking, even in challenging environments.

---

## ✨ Features

- **🚀 Hybrid Speed Tracking**: Combines GPS and Accelerometer/Gyroscope data for ultra-responsive speed readings.
- **🛡️ Kalman Filtering**: Implements a Kalman filter to smooth out GPS noise and provide a stable speed display.
- **🗺️ Interactive Maps**: Full map integration using OpenStreetMap (Osmdroid) to visualize your route in real-time.
- **📊 Live Statistics**: Track your current speed, average speed, distance, and total duration.
- **🌙 Dark Mode UI**: A sleek, high-contrast Material 3 interface optimized for visibility during outdoor rides.
- **🔋 Background Tracking**: Reliable foreground service ensures tracking continues even when the screen is off or the app is in the background.

---

## 🛠️ Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3.
- **Programming Language**: [Kotlin](https://kotlinlang.org/) with Coroutines for asynchronous processing.
- **Maps**: [Osmdroid](https://github.com/osmdroid/osmdroid) for OpenStreetMap integration.
- **Architecture**: Modern Android architecture with Flow-based state management.
- **Sensors**: Android Sensor API for motion detection and hybrid speed calculation.

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Koala (or newer)
- Android SDK 30+ (Min SDK)
- A device with GPS and Motion Sensors (Accelerometer/Gyroscope)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/biking.git
   ```
2. Open the project in **Android Studio**.
3. Sync the project with Gradle files.
4. Run the app on your physical device (highly recommended for sensor accuracy).

### Permissions

The app requires the following permissions to function correctly:
- `ACCESS_FINE_LOCATION`: For GPS tracking.
- `BODY_SENSORS`: For high-precision motion data.
- `POST_NOTIFICATIONS`: For the tracking service notification (Android 13+).

---

## 🧠 How it Works

The core of the app is the `HybridSpeedCalculator`. It doesn't just rely on GPS, which can be laggy or jumpy. Instead, it uses a **Kalman Filter** to fuse GPS velocity with linear acceleration from the device's IMU. This results in:
- Faster response to acceleration and braking.
- Smoother speed transitions.
- Better performance in "urban canyons" where GPS might be weak.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

*Made with ❤️ for cyclists.*
