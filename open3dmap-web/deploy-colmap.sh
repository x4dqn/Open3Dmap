#!/bin/bash

# Deploy Firebase Functions with COLMAP support
# This script builds a Docker container with COLMAP pre-installed and deploys it

set -e

echo "🚀 Deploying Firebase Functions with COLMAP support..."

# Get current directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Get project ID
PROJECT_ID=$(firebase projects:list --json | jq -r '.[] | select(.displayName) | .projectId' | head -1)
if [ -z "$PROJECT_ID" ]; then
    echo "❌ Could not determine Firebase project ID"
    echo "Please run: firebase use <project-id>"
    exit 1
fi

echo "📁 Project: $PROJECT_ID"

# Build Docker image
echo "🔨 Building Docker image with COLMAP..."
cd functions
docker build -t gcr.io/$PROJECT_ID/colmap-functions:latest .

# Push to Google Container Registry
echo "📤 Pushing image to Google Container Registry..."
docker push gcr.io/$PROJECT_ID/colmap-functions:latest

# Deploy with Docker image
echo "🚀 Deploying functions with custom Docker image..."
cd ..
gcloud functions deploy processCOLMAP \
    --source=functions \
    --entry-point=processCOLMAP \
    --runtime=nodejs22 \
    --trigger=https \
    --memory=16GB \
    --timeout=3600s \
    --max-instances=10 \
    --set-env-vars="FUNCTION_TARGET=processCOLMAP" \
    --docker-registry=gcr.io \
    --docker-repository=gcr.io/$PROJECT_ID/colmap-functions \
    --region=us-central1 \
    --project=$PROJECT_ID

gcloud functions deploy checkCOLMAPHealth \
    --source=functions \
    --entry-point=checkCOLMAPHealth \
    --runtime=nodejs22 \
    --trigger=https \
    --memory=1GB \
    --timeout=60s \
    --max-instances=1 \
    --set-env-vars="FUNCTION_TARGET=checkCOLMAPHealth" \
    --docker-registry=gcr.io \
    --docker-repository=gcr.io/$PROJECT_ID/colmap-functions \
    --region=us-central1 \
    --project=$PROJECT_ID

echo "✅ Deployment completed successfully!"
echo ""
echo "🔍 Testing COLMAP installation..."
echo "You can test the installation with:"
echo "curl -X POST https://us-central1-$PROJECT_ID.cloudfunctions.net/checkCOLMAPHealth"
echo ""
echo "📋 Next steps:"
echo "1. Test the health check endpoint"
echo "2. Try processing your 99 images with the new COLMAP-enabled functions"
echo "3. Check the logs: firebase functions:log" 