# SplatJSON Specification v1.1

## Overview
SplatJSON is a lightweight, extensible open format for describing 3D Gaussian splat scans anchored in physical space. Inspired by GeoJSON, SplatJSON provides a standard way to represent spatial metadata about a 3D scan, enabling interoperability across tools, platforms, and applications. It is designed to support a range of use cases including visualization, archiving, civic engagement, AR prototyping, and integration into spatial computing workflows.

## Goals
- Provide a common schema for georeferenced Gaussian splat files
- Enable easy sharing, indexing, and reuse of 3D scans across platforms
- Support metadata relevant for civic, artistic, educational, and research use cases
- Ensure compatibility with web standards and spatial computing platforms
- Promote transparency, openness, and long-term accessibility of spatial data

## Coordinate System Conventions
SplatJSON follows these spatial conventions for consistency across implementations:
- **Global coordinates**: WGS84 geodetic coordinates [longitude, latitude, altitude] in decimal degrees and meters
- **Local coordinate system**: Right-handed coordinate system with Y-up convention (Y-axis points upward, Z-axis points away from viewer)
- **Orientations**: Unit quaternions [qx, qy, qz, qw] representing rotation from local to world space
- **Transformations**: 4x4 transformation matrices in column-major order following OpenGL conventions

## File Structure
Each SplatJSON file contains either a single SplatFeature or a collection of features (SplatFeatureCollection). The structure is designed to be human-readable, versionable, and extendable.

### SplatFeature
Describes a single 3D splat and its associated metadata.

```json
{
  "spec_version": "1.1",
  "type": "SplatFeature",
  "id": "abc123-def456",
  "geometry": {
    "type": "Splat3D",
    "coordinates": [-77.6109, 43.1610, 153.5],
    "orientation": [0.0, 0.0, 0.0, 1.0],
    "scale": 1.0
  },
  "properties": {
    "timestamp": "2025-05-25T14:03:00Z",
    "splat_file_url": "https://openarmap.com/splats/abc123.splat",
    "raw_file_url": "https://openarmap.com/raw/abc123.zip",
    "preview_image": "https://openarmap.com/previews/abc123.jpg",
    "device_model": "iPhone 14 Pro",
    "localization_method": "VIO+GPS",
    "tags": ["public-art", "city-center"],
    "privacy": {
      "public": true,
      "license": "CC-BY-SA-4.0"
    },
    "anchor": {
      "type": "VIO+GPS",
      "reference_coordinates": [-77.6109, 43.1610, 153.5],
      "reference_orientation": [0.0, 0.0, 0.0, 1.0],
      "confidence": 0.92,
      "anchor_id": "rit-campus-quad",
      "alignment_transform": [
        [1.0, 0.0, 0.0, 0.02],
        [0.0, 1.0, 0.0, -0.01],
        [0.0, 0.0, 1.0, 0.03],
        [0.0, 0.0, 0.0, 1.0]
      ],
      "local_to_global_matrix": [
        [0.998, 0.001, 0.062, -1.24],
        [0.002, 1.000, -0.004, 0.51],
        [-0.062, 0.004, 0.998, 2.17],
        [0.0, 0.0, 0.0, 1.0]
      ],
      "anchor_fallback": {
        "method": "marker_image",
        "marker_image_url": "https://openarmap.com/markers/abc123.jpg"
      },
      "observed_at": [
        {
          "timestamp": "2025-05-25T14:03:00Z",
          "device_model": "iPhone 14 Pro"
        }
      ]
    },
    "composability": {
      "extends": ["scan456"],
      "scene_id": "campus_quad_v3",
      "version": 2,
      "merge_group_id": "cluster_north_quad",
      "is_primary_alignment": true
    }
  },
  "extensions": {
    "semantic_labels": ["building", "bench"],
    "pose_refinement": {
      "method": "marker-based",
      "data_url": "https://..."
    },
    "extra_properties": {
      "submitter": "alice@example.com",
      "environment": "outdoor",
      "lighting_conditions": "cloudy"
    }
  }
}
```

### SplatFeatureCollection
Groups multiple SplatFeature objects together.

```json
{
  "spec_version": "1.1",
  "type": "SplatFeatureCollection",
  "features": [
    { /* SplatFeature object */ },
    { /* SplatFeature object */ }
  ]
}
```

## Field Reference

### geometry
- **coordinates**: [longitude, latitude, altitude] — WGS84 geodetic coordinates for spatial anchoring
- **orientation**: [qx, qy, qz, qw] — Unit quaternion defining rotation from local to world space
- **scale**: Scalar multiplier applied to local splat units to convert to real-world scale (e.g., meters)

### properties
- **timestamp**: ISO 8601 UTC datetime string representing the time of scan or upload
- **splat_file_url**: Direct URL to the processed .splat file (INRIA Gaussian splat format)
- **raw_file_url**: URL to original data (e.g., RGB-D video, camera pose estimates)
- **preview_image**: JPEG/PNG file used for thumbnails or quick browser previews
- **device_model**: Human-readable string of device used to capture (e.g., iPhone 14 Pro, Pixel 8)
- **localization_method**: Description of localization tech used (e.g., "GPS only", "VIO+IMU", "ARKit")
- **tags**: Array of strings used for semantic categorization (e.g., ["mural", "park", "art"])
- **privacy**:
  - **public**: Boolean indicating visibility status
  - **license**: SPDX-compatible string or standard open license (e.g., CC-BY, MIT)

### anchor
- **type**: One of "GPS", "VIO", "marker_image", etc. Describes primary anchoring strategy.
- **reference_coordinates**: [lon, lat, alt] — Position anchor in world space
- **reference_orientation**: Quaternion used for rotational alignment
- **confidence**: Optional float 0.0–1.0 representing estimated positional certainty
- **anchor_id**: Optional unique identifier for canonical anchor regions
- **alignment_transform**: Optional 4x4 SE(3) matrix for transformation to scene alignment
- **local_to_global_matrix**: Optional 4x4 matrix representing the transformation from local scan space to global reference frame, useful for precise merging of multiple scans
- **anchor_fallback**: Optional fallback strategy (e.g., QR code, visual marker image)
- **observed_at**: Array of timestamp/device entries used to compute or verify anchor position

### composability
- **extends**: Array of scan IDs this scan references or augments
- **scene_id**: String identifier for shared composite environment
- **version**: Optional integer for versioned scan contribution
- **merge_group_id**: Optional string identifying a batch of scans intended to be merged together spatially
- **is_primary_alignment**: Optional boolean indicating whether this scan should serve as the reference alignment anchor for the group

### extensions (optional)
Use to define custom schema additions, platform-specific fields, or experimental metadata without breaking compatibility.

```json
"extensions": {
  "semantic_labels": ["bench", "tree"],
  "pose_refinement": {
    "method": "marker-based",
    "data_url": "https://..."
  },
  "extra_properties": {
    "submitter": "alice@example.com",
    "environment": "outdoor",
    "lighting_conditions": "cloudy"
  }
}
```

## Example: Collaborative Scanning Workflow

Here's how two overlapping scans would reference each other in a collaborative mapping scenario:

### Initial Base Scan
```json
{
  "spec_version": "1.1",
  "type": "SplatFeature",
  "id": "campus-quad-base-001",
  "properties": {
    "timestamp": "2025-05-25T14:00:00Z",
    "tags": ["campus", "quad", "baseline"],
    "composability": {
      "scene_id": "rit_campus_quad",
      "version": 1,
      "merge_group_id": "quad_mapping_session_1",
      "is_primary_alignment": true
    }
  }
}
```

### Extension Scan (References Base)
```json
{
  "spec_version": "1.1",
  "type": "SplatFeature", 
  "id": "campus-quad-ext-002",
  "properties": {
    "timestamp": "2025-05-25T14:15:00Z",
    "tags": ["campus", "quad", "detail"],
    "composability": {
      "extends": ["campus-quad-base-001"],
      "scene_id": "rit_campus_quad",
      "version": 2,
      "merge_group_id": "quad_mapping_session_1",
      "is_primary_alignment": false
    }
  }
}
```

### Subsequent Refinement Scan
```json
{
  "spec_version": "1.1",
  "type": "SplatFeature",
  "id": "campus-quad-detail-003", 
  "properties": {
    "timestamp": "2025-05-25T14:30:00Z",
    "tags": ["campus", "quad", "high-detail", "artwork"],
    "composability": {
      "extends": ["campus-quad-base-001", "campus-quad-ext-002"],
      "scene_id": "rit_campus_quad", 
      "version": 3,
      "merge_group_id": "quad_mapping_session_1",
      "is_primary_alignment": false
    }
  }
}
```

This workflow demonstrates how contributors can build upon each other's work: the base scan establishes the primary spatial alignment, the extension scan adds coverage to new areas while referencing the base, and the detail scan refines specific regions while building on both previous contributions.

## Future Extensions
- Local vs global alignment matrix for context-aware positioning
- Semantic region tagging (e.g., buildings, road types, access control)
- Time-series change detection between splats
- Contributor profiles, attribution, and reputation
- Cross-platform anchoring protocol compatibility (e.g., export to ARCore/ARKit anchors)
- Asset bundling + scripting support (trigger interactions, attach behaviors)

## License
SplatJSON is an open format maintained under the MIT License. Contributions, feedback, and pull requests are welcome at openarmap.org or via GitHub.

**Maintained by OpenARMap.org**
