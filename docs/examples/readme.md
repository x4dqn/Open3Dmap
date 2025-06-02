# SplatJSON Examples

This directory contains example SplatJSON files demonstrating various use cases and features of the open3dmap spatial data format.

## Overview

SplatJSON is designed to represent GPS-anchored 3D Gaussian splat scans with rich metadata for collaborative mapping. These examples show how the format supports different scenarios from simple individual scans to complex multi-contributor workflows.

## Example Files

### `simple-scan.splatjson`
A basic example showing a single 3D scan with minimal required fields. Demonstrates:
- GPS anchoring with VIO tracking
- Basic metadata structure
- Privacy and licensing fields

### `collaborative-workflow/`
A three-part example showing how multiple contributors can build upon each other's work:

#### `01-base-scan.splatjson`
- Initial scan establishing primary spatial alignment
- Sets up the shared coordinate frame for subsequent scans
- Marked as `is_primary_alignment: true`

#### `02-extension-scan.splatjson`
- References the base scan via the `extends` field
- Adds coverage to new areas while maintaining spatial consistency
- Shows how scans can build incrementally

#### `03-detail-scan.splatjson`
- References both previous scans
- Demonstrates refinement and detail addition
- Shows version progression and merge group management

### `feature-collection.splatjson`
Shows how multiple SplatFeature objects can be grouped together in a single file for batch operations or regional exports.

## Key Concepts Demonstrated

### Spatial Anchoring
All examples show GPS+VIO anchoring with confidence scores and fallback strategies for robust localization across different devices and environments.

### Collaborative Scanning
The workflow examples demonstrate how the `extends`, `scene_id`, and `merge_group_id` fields enable community-driven mapping where contributors build upon each other's work.

### Metadata Richness
Examples include realistic metadata for:
- Device information and capture methods
- Privacy settings and licensing
- Semantic tags for searchability
- Contributor attribution

### Extensibility
The `extensions` field shows how platforms can add custom metadata without breaking compatibility with the core specification.

## Usage

These files can be used to:
- Test SplatJSON parsers and validators
- Understand the collaborative workflow concepts
- Prototype applications that consume spatial scan data
- Validate implementations against realistic data structures

## Validation

All example files conform to the SplatJSON v1.1 specification documented in [`../docs/splatjson-spec.md`](https://github.com/x4dqn/open3dmap/blob/main/docs/splatjson-spec.md)

## Contributing

When adding new examples:
1. Ensure they validate against the current SplatJSON schema
2. Include realistic metadata and coordinates
3. Add documentation explaining the specific use case
4. Consider privacy implications and use appropriate license fields
