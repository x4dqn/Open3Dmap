# Firebase Functions Docker Deployment Guide

## Problem: Mock Data Instead of Real COLMAP Processing

If you're seeing "3 sample images" and "realistic training simulation" in your browser console, it means your Firebase Functions are falling back to mock data because COLMAP is not installed in the runtime environment.

## Solution: Docker Deployment with COLMAP

This guide will help you deploy Firebase Functions with Docker containers that include COLMAP, enabling real processing of your scan data.

## Prerequisites

1. **Firebase CLI**: `npm install -g firebase-tools`
2. **Google Cloud CLI**: [Install from here](https://cloud.google.com/sdk/docs/install)
3. **Docker**: [Install Docker Desktop](https://www.docker.com/products/docker-desktop)
4. **Authentication**: Make sure you're logged into both Firebase and Google Cloud

## Quick Start

### 1. Check Current Status

First, check if your functions are using mock data:

```bash
# Test the health check endpoint
curl -s https://YOUR-REGION-YOUR-PROJECT-ID.cloudfunctions.net/checkCOLMAPHealth
```

If you see `"status": "NOT_FOUND"` for COLMAP binary, you need Docker deployment.

### 2. Deploy with Docker

#### For Linux/Mac:

```bash
# Make the deployment script executable
chmod +x deploy-docker.sh

# Run the Docker deployment
./deploy-docker.sh
```

#### For Windows:

```powershell
# Run the PowerShell deployment script
.\deploy-docker.ps1
```

This script will:
- Check all prerequisites
- Enable required Google Cloud APIs
- Create Artifact Registry repository
- Build Docker image with COLMAP
- Deploy functions with Docker containers
- Test the deployment

### 3. Verify the Fix

After deployment, test the health check again:

```bash
curl -s https://YOUR-REGION-YOUR-PROJECT-ID.cloudfunctions.net/checkCOLMAPHealth
```

You should now see `"status": "FOUND"` for COLMAP binary.

## Manual Deployment Steps

If you prefer to run the deployment manually:

### 1. Authentication

```bash
# Login to Firebase
firebase login

# Login to Google Cloud
gcloud auth login

# Set your project
firebase use YOUR-PROJECT-ID
gcloud config set project YOUR-PROJECT-ID
```

### 2. Enable APIs

```bash
gcloud services enable cloudfunctions.googleapis.com
gcloud services enable cloudbuild.googleapis.com
gcloud services enable artifactregistry.googleapis.com
gcloud services enable run.googleapis.com
```

### 3. Create Artifact Registry

```bash
gcloud artifacts repositories create functions \
  --repository-format=docker \
  --location=us-central1
```

### 4. Build and Push Docker Image

```bash
# Configure Docker authentication
gcloud auth configure-docker us-central1-docker.pkg.dev

# Build image
cd functions
docker build -t us-central1-docker.pkg.dev/YOUR-PROJECT-ID/functions/colmap-function:latest .

# Push image
docker push us-central1-docker.pkg.dev/YOUR-PROJECT-ID/functions/colmap-function:latest
cd ..
```

### 5. Deploy Functions

```bash
# Update firebase-docker.json with your project ID
sed -i 's/PROJECT_ID/YOUR-PROJECT-ID/g' firebase-docker.json

# Deploy with Docker configuration
firebase deploy --only functions --config=firebase-docker.json
```

## Troubleshooting

### Common Issues

1. **"Docker not found"**: Install Docker Desktop and make sure it's running
2. **"gcloud not found"**: Install Google Cloud CLI
3. **"Permission denied"**: Make sure you're authenticated with correct permissions
4. **"API not enabled"**: The script will enable required APIs automatically

### Testing Your Deployment

1. **Health Check**: Visit your health check URL to verify COLMAP is installed
2. **Process a Scan**: Try processing a real scan to see actual COLMAP output
3. **Check Console**: You should no longer see "3 sample images" or "realistic training simulation"

### Rolling Back

If you need to roll back to the standard deployment:

```bash
# Deploy with standard configuration
firebase deploy --only functions --config=firebase.json
```

## What This Fixes

- ✅ **Real COLMAP processing** instead of mock data
- ✅ **Actual sparse reconstruction** from your images
- ✅ **Proper 3D point clouds** for training
- ✅ **Real camera poses** and parameters
- ✅ **Better training results** with actual data

## Performance Benefits

Docker deployment provides:
- **60-minute timeout** (vs 9 minutes standard)
- **16GB memory** (vs 8GB standard)
- **4 CPU cores** (vs 2 cores standard)
- **Full COLMAP suite** with all tools

## Cost Considerations

Docker deployment may cost more than standard functions due to:
- Longer execution time
- More memory usage
- Container startup time

However, the cost is justified by getting real processing results instead of mock data.

## Next Steps

After successful deployment:
1. Test with a real scan
2. Monitor function logs for any issues
3. Verify training results are using real data
4. Consider setting up monitoring for the health check endpoint

## Support

If you encounter issues:
1. Check the function logs in Google Cloud Console
2. Verify all prerequisites are installed
3. Ensure you have proper permissions
4. Test the health check endpoint 