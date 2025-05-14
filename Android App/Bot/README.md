# ARCore Scanner

An Android application that uses ARCore to capture and store detailed metadata about AR scanning sessions and individual frames.

## Features

- Captures comprehensive metadata for AR scanning sessions including:
  - Unique scan identifiers
  - Device information
  - GPS coordinates
  - Camera poses and transformations
  - Privacy settings
  - Scan type and area coverage
  - Camera intrinsics

- Per-frame metadata capture:
  - Frame identifiers and timestamps
  - Camera poses and matrices
  - GPS coordinates
  - IMU data (accelerometer and gyroscope)
  - Pose confidence
  - Image quality metrics
  - Exposure information
  - Manual tags

## Requirements

- Android 7.0 (API level 24) or higher
- ARCore supported device
- OpenGL ES 3.0 support
- Camera permission
- Location permissions
- Storage permissions (for Android 9 and below)

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Build and run the application

## Usage

1. Launch the application
2. Grant necessary permissions when prompted
3. Start a new scan session
4. Move the device around to capture frames
5. Stop the scan when finished

## Data Storage

All metadata is stored locally on the device using Room database. The data structure includes:

- Scan sessions table
- Frames table
- Type converters for complex data types

## Dependencies

- ARCore 1.40.0
- AndroidX Core
- Room Database
- Kotlin Coroutines
- Google Play Services Location

## License

This project is licensed under the MIT License - see the LICENSE file for details. 