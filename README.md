<p align="center">
  <img src="assets/OpenARMap_Logo.png" alt="OpenARMap Logo" width="300"/>
</p>

# OpenARMap

**OpenARMap** is a community-driven initiative to build open, GPS-anchored 3D map infrastructure for spatial computing. Our goal is to enable anyone with a smartphone to capture, share, and reuse high-fidelity scans of the physical world—streets, parks, buildings, and public spaces—without relying on closed platforms or proprietary ecosystems.

Every scan becomes part of a living digital twin: anchored with transparent metadata (.splatjson), freely exportable, and interoperable with tools like Unity, WebXR, and Cesium. From education and research to public art and civic planning, OpenARMap is designed to support open participation, long-term accessibility, and real-world utility.

We believe spatial computing should be public infrastructure. OpenARMap is how we build it—together.

This repository contains the **first component** of that system: an Android application for capturing high-quality spatial data. The app records image sequences, camera poses (via ARCore), GPS coordinates, and IMU sensor data. This forms the foundation for downstream processing into 3D reconstructions and Earth-anchored localization.

While the current focus is on mobile capture, this repository will eventually house the full OpenARMap codebase, including reconstruction tools, metadata generation, cloud pipelines, and web-based visualization. 

We’re releasing this early-stage mobile app to kickstart development, invite collaborators, and begin testing participatory mobile scanning in real-world conditions.

## Platform Roadmap

The full OpenARMap platform will eventually include:

- A reconstruction pipeline extending INRIA’s Gaussian splatting, optimized for mobile and civic-scale use
- A public-facing web viewer to explore contributed 3D scans on a map
- Alignment tools that combine GPS and VIO data to accurately place scans in the global coordinate system, including support for correcting orientation/scale and anchoring via visual markers when GPS is unreliable.
- Custom metadata format (.splatjson) for each scan, containing global coordinates, orientation, device and capture metadata, privacy settings, and file links—enabling integration with viewers, maps, and spatial computing platforms
- Real-time feedback and scan quality visualization
- In-app guidance and tips for scanning in diverse environments
- Contributor dashboards, privacy controls, and governance features
- Contributor-defined licensing (e.g., CC-BY, CC0) and scan visibility settings
- Support for spatial querying, scan overlays, and metadata-rich exploration through a browser-based interface
- Web-based tools for labeling, describing, and organizing scans; supporting creative reuse (e.g. styling, tagging, forked versions) and curated public collections.
- Support for tracking changes to the same location over time (e.g., rescan comparisons, version history), enabling environmental monitoring or historical reconstruction.
- Role-based system that recognizes contributions (e.g., novice → trusted mapper), supports moderation privileges, and encourages long-term engagement.
- Open APIs for external integrations
- Developer SDKs and integration examples for Unity, WebXR, and Cesium
- Sample datasets and onboarding tools for new contributors and educators
- Tools to check scan quality, reject duplicates, and merge multiple scans of the same location into larger composite scenes.
- Offline-first or low-connectivity capture modes

At this stage, the repository includes only the Android app for data capture. Future components—including cloud processing, metadata generation, and web-based visualization—will be added incrementally as they are developed and tested.


## Coming Soon: Contributor Login and Cloud Pipeline

We are actively building out user authentication and cloud integration to support a seamless contributor workflow. This will enable users to:

- Log in via email-based authentication (mobile-first)
- Capture scans using the OpenARMap Android app
- Upload scans directly to the cloud
- View and manage their uploaded scans on the web, including metadata, filtering, and map-based visualization

As part of this pipeline, each scan will be automatically paired with a structured .splatjson metadata file. This file contains GPS coordinates, orientation, device and capture metadata, file URLs, and licensing information—enabling integration with maps, viewers, and third-party spatial tools.

We are also enhancing the mobile app to support more complete metadata capture, backend integration, and foundational features such as contributor identity, scan validation, and sync status tracking.

These features are under active development and will be released incrementally through this repository.

## Mobile App for Data Capture

The OpenARMap Android app is the foundation of our ecosystem. It allows anyone to capture georeferenced image sequences and sensor data that power downstream reconstruction, anchoring, and sharing. Whether you're contributing to public spatial datasets or exploring your environment in 3D, this tool is where it all begins.

The sections below refer specifically to the mobile capture tool. If you're looking to test early scanning workflows or contribute to real-world spatial data collection, you're in the right place.

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

### Export File Structure

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
        └── scan_id.splatjson   <-- (coming soon)
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

This project is licensed under CC BY-NC 4.0 - see the LICENSE file for details.

## Acknowledgments

- ARCore team for the excellent tracking capabilities
- INRIA team for their work on [3D Gaussian Splatting](https://repo-sam.inria.fr/fungraph/3d-gaussian-splatting/)
- All contributors and users of the project
