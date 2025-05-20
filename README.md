# OpenARMap

**openARMap** is a community-driven initiative to build open, GPS-anchored 3D map infrastructure for spatial computing. Our long-term goal is to enable anyone with a smartphone to help create, maintain, and govern high-fidelity digital twins of the physical world—parks, streets, buildings, and public spaces—without relying on closed platforms or proprietary systems.

This repository contains the **first component** of that system: an Android application for capturing high-quality spatial data. The app records image sequences, camera poses (via ARCore), GPS coordinates, and IMU sensor data. This forms the foundation for downstream processing into 3D reconstructions and Earth-anchored localization.

The full openARMap platform will eventually include:

- A reconstruction pipeline extending INRIA’s Gaussian splatting, optimized for mobile and civic-scale use
- A public-facing web viewer to explore contributed 3D scans on a map
- Alignment tools to anchor scans globally using VIO + GPS
- Real-time feedback and scan quality visualization
- Contributor dashboards, privacy controls, and governance features
- Support for spatial querying, scan overlays, and metadata-rich exploration through a browser-based interface
- Temporal versioning and change detection tools
- Contributor progression and community roles
- Open APIs for external integrations
- Scan validation and composability tools
- Offline-first or low-connectivity capture modes
- Educational modules for classroom and community use

We're releasing this early-stage app to kickstart development, invite collaborators, and begin testing participatory mobile scanning in real-world conditions.


## About This Repository

This repository will contain the **entire openARMap pipeline**, including:

-  The mobile app (this component)
-  Reconstruction pipeline using mobile-optimized Gaussian splatting
- GPS + VIO-based global alignment and relocalization
-  Visualization tools and scan quality viewers
-  Privacy-preserving tools and contributor governance system

At this stage, the repository includes **only the Android app** for data capture. Future components will be added here incrementally as they are developed and tested.

The sections below refer specifically to the mobile capture tool. If you're looking to test early scanning workflows or contribute to real-world spatial data collection, you're in the right place.

## Mobile App for Data Capture

## Features

- Real-time camera tracking using ARCore
- Automatic frame capture with quality assessment
- GPS location tracking for outdoor scans
- IMU data capture (accelerometer and gyroscope)
- Export functionality with INRIA-compatible format
- Scan management (rename, delete, export)
- Quality metrics for optimal capture

## Prerequisites

- Android device with ARCore support
- Android 8.0 (API level 26) or higher
- Google Play Services
- Camera and location permissions
- Storage permissions for exporting data

## Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/OpenARMap.git
cd OpenARMap
```

2. Open the project in Android Studio:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the `AndroidApp/Bot` directory
   - Click "OK"

3. Build and run:
   - Connect your Android device
   - Click the "Run" button (green play icon)
   - Select your device from the list
   - Wait for the app to install and launch

## Usage

### Capturing Scans

1. Launch the app and grant necessary permissions
2. Press "Start Scan" to begin a new capture session
3. Enter a name for your scan (optional)
4. Move your device slowly through the space:
   - Keep the camera pointed at textured surfaces
   - Maintain good lighting conditions
   - Move at a walking pace
   - Avoid rapid movements or rotations
5. Press "Stop Scan" when finished

### Managing Scans

- **Export**: Press the export button to view all scans
- **Rename**: Long-press a scan or use the rename button
- **Delete**: Use the delete button or long-press menu
- **View Details**: Tap a scan to see its details

### Export Format

The app exports data in a format compatible with the INRIA Gaussian Splatting pipeline:

```
OpenARMaps/Exports/
└── ScanName_YYYY-MM-DD_HH-mm/
    ├── images/
    │   ├── frame_000.jpg
    │   ├── frame_001.jpg
    │   └── ...
    └── metadata/
        ├── transforms.json
        └── session_[ID].json
```

## Using with INRIA Gaussian Splatting

1. Export your scan from the app
2. Copy the exported folder to your computer
3. Follow the INRIA pipeline setup:
```bash
# Clone the INRIA repository
git clone https://github.com/graphdeco-inria/gaussian-splatting.git
cd gaussian-splatting

# Install dependencies
pip install -r requirements.txt

# Process your scan
python train.py --source_path /path/to/your/scan
```

### Accessing the Viewer

The INRIA pipeline includes a built-in viewer for visualizing the results:

1. After training, locate the output directory (usually `output/[timestamp]`)
2. Launch the viewer:
```bash
python viewer.py --path /path/to/output/directory
```

The viewer supports:
- Interactive camera controls
- Point cloud visualization
- Splat rendering
- Quality metrics display

## Troubleshooting

### Common Issues

1. **Poor Tracking**
   - Ensure good lighting conditions
   - Move more slowly
   - Point camera at textured surfaces
   - Avoid reflective or transparent surfaces

2. **Export Failures**
   - Check storage permissions
   - Ensure sufficient storage space
   - Try restarting the app

3. **ARCore Issues**
   - Update Google Play Services
   - Clear ARCore app data
   - Restart device

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- ARCore team for the excellent tracking capabilities
- INRIA team for the Gaussian Splatting pipeline
- All contributors and users of the project
