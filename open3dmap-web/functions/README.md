# Firebase Functions for COLMAP 3D Reconstruction

This directory contains Firebase Functions 2nd Generation implementation for processing COLMAP (Structure from Motion) 3D reconstruction with streaming support, designed to handle large datasets up to 2000+ images.

## Features

### 🚀 **Enhanced Performance**
- **Memory**: 16GB (up from 2GB) for large dataset processing
- **CPU**: 4 cores for parallel processing
- **Timeout**: 60 minutes (up from 9 minutes)
- **Concurrency**: Optimized for resource-intensive tasks

### 📡 **Real-time Progress Streaming**
- Live progress updates during processing
- Stage-by-stage status reporting
- Error streaming with detailed messages
- Progress percentages and time estimates

### 🔄 **Parallel Processing**
- Batch image downloads (10 images per batch)
- Exponential backoff retry logic
- Concurrent feature extraction and matching
- Optimized resource utilization

### 📊 **Comprehensive Logging**
- Structured logging with timestamps
- Execution time tracking
- Detailed error reporting
- Performance metrics collection

### 🏥 **Health Monitoring**
- COLMAP installation verification
- System dependency checks
- Runtime environment validation
- Health check endpoint

## Architecture

### Functions

#### `processCOLMAP`
Main processing function that handles the complete COLMAP pipeline:

```javascript
// Configuration
{
    timeoutSeconds: 3600,    // 60 minutes
    memory: '16GiB',         // 16GB memory
    cpu: 4,                  // 4 CPU cores
    concurrency: 1,          // One request per instance
    invoker: 'private'       // Requires authentication
}
```

**Processing Stages:**
1. **Initialization** (0-5%): Validate inputs and setup
2. **Downloading** (5-30%): Parallel image downloads with retry
3. **Feature Extraction** (30-50%): SIFT feature detection
4. **Feature Matching** (50-70%): Exhaustive feature matching
5. **Sparse Reconstruction** (70-90%): 3D point cloud generation
6. **Uploading** (90-100%): Results upload to Firebase Storage

#### `checkCOLMAPHealth`
Health monitoring function that verifies system readiness:

```javascript
// Returns status of:
{
    status: 'HEALTHY' | 'UNHEALTHY' | 'ERROR',
    checks: [
        { check: 'COLMAP binary', status: 'FOUND', path: '/usr/local/bin/colmap' },
        { check: 'COLMAP version', status: 'OK', version: '3.9.1' },
        { check: 'cmake', status: 'FOUND', path: '/usr/bin/cmake' },
        { check: 'python3', status: 'FOUND', path: '/usr/bin/python3' },
        { check: 'gcc', status: 'FOUND', path: '/usr/bin/gcc' }
    ]
}
```

## Installation

### Prerequisites
- Node.js 20+
- Firebase CLI: `npm install -g firebase-tools`
- Docker (for containerized deployment)

### Setup

1. **Install dependencies:**
```bash
cd functions
npm install
```

2. **Configure Firebase:**
```bash
firebase login
firebase use <your-project-id>
```

3. **Set environment variables (public-friendly):**
```bash
export GOOGLE_CLOUD_PROJECT=YOUR_PROJECT_ID
export GCS_BUCKET=YOUR_PROJECT_ID.firebasestorage.app
```

3. **Enable required APIs in Google Cloud Console:**
   - Cloud Functions API
   - Cloud Build API
   - Cloud Storage API
   - Cloud Logging API
   - Artifact Registry API

### Deployment

#### Option 1: Quick Deploy
```bash
# From project root
firebase deploy --only functions
```

#### Option 2: Comprehensive Deploy Script
```bash
# From project root
chmod +x functions/deploy.sh
./functions/deploy.sh
```

## Usage

### Client-side Integration

#### With Streaming Support
```javascript
import { getFunctions, httpsCallable } from 'firebase/functions';

const functions = getFunctions();
const processCOLMAP = httpsCallable(functions, 'processCOLMAP');

// Call with streaming support
const { stream, data } = await processCOLMAP.stream({
    scanId: 'unique-scan-id',
    imageUrls: ['url1', 'url2', 'url3', /* ... up to 2000 URLs */]
});

// Listen to real-time progress updates
for await (const chunk of stream) {
    console.log(`Stage: ${chunk.stage}`);
    console.log(`Progress: ${chunk.progress}%`);
    console.log(`Message: ${chunk.message}`);
    
    // Update UI with progress
    updateProgressBar(chunk.progress);
    updateStatusMessage(chunk.message);
}

// Get final results
const finalResult = await data;
console.log('Processing complete:', finalResult);
```

#### Without Streaming (Fallback)
```javascript
const processCOLMAP = httpsCallable(functions, 'processCOLMAP');

try {
    const result = await processCOLMAP({
        scanId: 'unique-scan-id',
        imageUrls: ['url1', 'url2', 'url3']
    });
    
    console.log('Processing complete:', result.data);
} catch (error) {
    console.error('Processing failed:', error);
}
```

### Health Check
```javascript
const checkHealth = httpsCallable(functions, 'checkCOLMAPHealth');

const healthStatus = await checkHealth();
console.log('System health:', healthStatus.data);
```

## Processing Pipeline

### 1. Image Download
- **Parallel downloads** in batches of 10 images
- **Retry logic** with exponential backoff
- **Progress tracking** with real-time updates
- **Error handling** for failed downloads

### 2. COLMAP Processing
- **Feature Extraction**: SIFT features from all images
- **Feature Matching**: Exhaustive matching between image pairs
- **Sparse Reconstruction**: 3D point cloud generation
- **Output**: cameras.txt, images.txt, points3D.txt, database.db

### 3. Result Upload
- **Firebase Storage** integration
- **Structured paths**: `colmap-results/{userId}/{scanId}/`
- **Metadata tracking**: Upload timestamps and user info
- **File organization**: Separate folders for sparse, images, database

## Output Structure

```
colmap-results/{userId}/{scanId}/
├── sparse/
│   ├── cameras.txt      # Camera parameters
│   ├── images.txt       # Image poses and observations
│   └── points3D.txt     # 3D point cloud
├── images/              # Original input images
│   ├── image1.jpg
│   ├── image2.jpg
│   └── ...
└── database.db          # COLMAP database file
```

## Monitoring and Logging

### Log Format
```
[timestamp] [scanId] [userId] [stage] message
```

### Log Levels
- **INFO**: Normal operation progress
- **WARN**: Non-critical issues (e.g., mock data usage)
- **ERROR**: Critical failures with stack traces

### Monitoring Dashboard
Access logs in Firebase Console:
1. Go to Functions section
2. Select function name
3. View logs tab for real-time monitoring

## Error Handling

### Common Issues

#### 1. COLMAP Not Available
- **Fallback**: Automatic mock data generation
- **Detection**: Health check verifies installation
- **Solution**: Ensure Dockerfile builds correctly

#### 2. Image Download Failures
- **Retry Logic**: Up to 3 attempts with exponential backoff
- **Batch Processing**: Failed images don't block others
- **Detailed Logging**: URLs and error messages logged

#### 3. Memory/Timeout Issues
- **Large Datasets**: 16GB memory, 60-minute timeout
- **Resource Monitoring**: Performance metrics logged
- **Optimization**: Parallel processing reduces total time

#### 4. Authentication Issues
- **Private Functions**: Require authenticated users
- **Error Messages**: Clear authentication failure messages
- **Security**: User-specific file paths and permissions

## Performance Optimization

### For Large Datasets (1000+ images)

1. **Batch Processing**: Images downloaded in parallel batches
2. **Memory Management**: Efficient cleanup of temporary files
3. **CPU Utilization**: Multi-core processing for COLMAP stages
4. **Storage Optimization**: Compressed uploads and efficient paths

### Expected Performance

| Dataset Size | Download Time | Processing Time | Total Time |
|-------------|---------------|-----------------|------------|
| 50 images   | 1-2 minutes   | 5-10 minutes    | 6-12 minutes |
| 200 images  | 3-5 minutes   | 15-25 minutes   | 18-30 minutes |
| 500 images  | 8-12 minutes  | 25-35 minutes   | 33-47 minutes |
| 1000 images | 15-20 minutes | 35-45 minutes   | 50-65 minutes |

## Development

### Local Testing
```bash
# Start emulator
firebase emulators:start --only functions

# Test health check
curl http://localhost:5001/your-project/us-central1/checkCOLMAPHealth
```

### Linting
```bash
npm run lint
```

### Building
```bash
npm run build
```

## Troubleshooting

### Common Solutions

1. **Function Timeout**: Increase timeout in function configuration
2. **Memory Issues**: Monitor memory usage and increase allocation
3. **COLMAP Errors**: Check health endpoint and Dockerfile
4. **Authentication**: Verify Firebase auth configuration
5. **Storage Issues**: Check Firebase Storage rules and permissions

### Debug Mode
Enable debug logging by setting environment variables:
```bash
export DEBUG=true
firebase deploy --only functions --debug
```

## Support

For issues and questions:
1. Check the logs in Firebase Console
2. Use the health check endpoint for system status
3. Review error messages in streaming responses
4. Monitor resource usage and performance metrics

## License

MIT License - see LICENSE file for details. 