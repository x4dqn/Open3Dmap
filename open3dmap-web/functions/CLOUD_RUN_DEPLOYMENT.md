# Cloud Run COLMAP Deployment Guide

## Overview

This guide shows how to deploy COLMAP processing to Google Cloud Run using the **official COLMAP Docker image**. This approach is much more reliable than building from source and avoids all the Docker build issues you've been experiencing.

## Key Benefits

- ✅ **Uses official COLMAP Docker image** - No more build failures
- ✅ **60-minute timeout** - Enough time for large datasets
- ✅ **16GB memory, 4 CPUs** - Powerful processing capabilities
- ✅ **Auto-scaling** - Scales to zero when not in use
- ✅ **No Firebase Functions limitations** - Full container control
- ✅ **Easy deployment** - Single command deployment

## Prerequisites

1. **Google Cloud CLI** installed and configured
2. **Docker** installed (for local testing)
3. **gcloud project** set up with billing enabled
4. **APIs enabled**: Cloud Run, Cloud Build

## Quick Deployment

### 1. Deploy to Cloud Run

```bash
# From the functions directory
cd open3dmap-web/functions

# Deploy using the automated script
./deploy-cloudrun.sh
```

### 2. Test the Deployment

```bash
# Test the deployment
./test-deployment.sh
```

### 3. Check Health

```bash
# Get your service URL
SERVICE_URL=$(gcloud run services describe colmap-processor --region=us-central1 --format="value(status.url)")

# Test health check
curl -s "$SERVICE_URL/health" | jq .
```

## Architecture

```
Web App → Cloud Run Service → Official COLMAP Docker Image
                ↓
         Firebase Storage (results)
```

## API Endpoints

### Health Check
```
GET /health
```

Response:
```json
{
  "status": "HEALTHY",
  "timestamp": "2025-01-17T15:45:28.885Z",
  "checks": [
    {
      "check": "COLMAP binary",
      "status": "FOUND",
      "path": "/usr/local/bin/colmap"
    }
  ]
}
```

### Process Images
```
POST /process
```

Request:
```json
{
  "scanId": "unique-scan-id",
  "imageUrls": ["url1", "url2", "url3"],
  "userId": "user-id"
}
```

Response:
```json
{
  "success": true,
  "results": {
    "sparseDir": "colmap-results/user-id/scan-id/sparse/",
    "imagesDir": "colmap-results/user-id/scan-id/images/",
    "databasePath": "colmap-results/user-id/scan-id/database.db",
    "uploadedFiles": ["sparse/cameras.txt", "sparse/images.txt", "sparse/points3D.txt"]
  }
}
```

## Configuration

### Resource Limits
- **Memory**: 16GB
- **CPU**: 4 cores
- **Timeout**: 60 minutes (3600 seconds)
- **Concurrency**: 1 (one request per instance)
- **Max instances**: 10
- **Min instances**: 0 (scales to zero)

### Environment Variables
- `NODE_ENV=production`
- `PORT=8080`
- `GOOGLE_CLOUD_PROJECT` (auto-set by Cloud Run)

## Monitoring

### View Logs
```bash
gcloud run logs tail colmap-processor --region=us-central1
```

### Check Service Status
```bash
gcloud run services describe colmap-processor --region=us-central1
```

### Get Service URL
```bash
gcloud run services describe colmap-processor --region=us-central1 --format="value(status.url)"
```

## Troubleshooting

### Common Issues

1. **Service not found**
   - Make sure you've deployed first: `./deploy-cloudrun.sh`

2. **Health check fails**
   - Check logs: `gcloud run logs tail colmap-processor --region=us-central1`
   - Verify COLMAP binary is available in container

3. **Processing timeouts**
   - Reduce image count or size
   - Check memory usage in Cloud Run console

4. **Authentication errors**
   - Ensure your web app includes user authentication
   - Check Firebase project configuration

### Debug Commands

```bash
# Test locally (if you have Docker)
docker build -t colmap-test .
docker run -p 8080:8080 colmap-test

# Test health check locally
curl http://localhost:8080/health

# Check COLMAP in container
docker run --rm colmap/colmap:latest colmap help
```

## Integration with Web App

Update your web app to call the Cloud Run service instead of Firebase Functions:

```javascript
// Replace Firebase Functions call
const processCOLMAP = httpsCallable(functions, 'processCOLMAP');

// With Cloud Run HTTP call
const SERVICE_URL = 'https://colmap-processor-[hash]-uc.a.run.app';

const processCOLMAP = async (data) => {
  const response = await fetch(`${SERVICE_URL}/process`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  return await response.json();
};
```

## Cost Optimization

- Service **scales to zero** when not in use
- **Pay per use** - only charged when processing
- **No idle costs** unlike Firebase Functions
- **Efficient resource usage** with official Docker image

## Security

- Service requires authentication (configure in your web app)
- Runs in isolated Cloud Run environment
- Uses Google's security-hardened container runtime
- All data encrypted in transit and at rest

## Next Steps

1. **Test with real data** - Process actual image scans
2. **Monitor performance** - Check processing times and success rates
3. **Scale if needed** - Adjust resource limits based on usage
4. **Add error handling** - Implement retry logic in your web app
5. **Add progress tracking** - Use Server-Sent Events for real-time updates

Your COLMAP processing is now running on a reliable, scalable platform! 