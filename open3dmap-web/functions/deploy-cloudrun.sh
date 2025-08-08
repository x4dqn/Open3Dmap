#!/bin/bash

# Simple Cloud Run deployment for COLMAP processing using official Docker image
# This bypasses Firebase Functions and deploys directly to Cloud Run with official COLMAP

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

# Get project ID
PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
if [ -z "$PROJECT_ID" ]; then
    print_error "No gcloud project set. Please run: gcloud config set project YOUR_PROJECT_ID"
    exit 1
fi

REGION="us-central1"
SERVICE_NAME="colmap-processor"

print_status "Deploying COLMAP processor to Cloud Run using official Docker image..."
print_status "Project: $PROJECT_ID"
print_status "Region: $REGION"
print_status "Service: $SERVICE_NAME"

# Enable required APIs
print_status "Enabling required APIs..."
gcloud services enable run.googleapis.com --project=$PROJECT_ID
gcloud services enable cloudbuild.googleapis.com --project=$PROJECT_ID

# Build and deploy to Cloud Run
print_status "Building and deploying to Cloud Run with official COLMAP image..."

gcloud run deploy $SERVICE_NAME \
    --source=. \
    --region=$REGION \
    --allow-unauthenticated \
    --max-instances=10 \
    --min-instances=0 \
    --timeout=3600 \
    --memory=16Gi \
    --cpu=4 \
    --concurrency=1 \
    --execution-environment=gen2 \
    --project=$PROJECT_ID \
    --set-env-vars="GOOGLE_CLOUD_PROJECT=$PROJECT_ID,NODE_ENV=production" \
    --quiet

if [ $? -eq 0 ]; then
    print_status "Cloud Run deployment successful!"
    
    # Get service URL
    SERVICE_URL=$(gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID --format="value(status.url)")
    
    print_status "Service URL: $SERVICE_URL"
    print_status "Health check: $SERVICE_URL/health"
    print_status "Process endpoint: $SERVICE_URL/process"
    
    print_status "Testing health check..."
    response=$(curl -s -w "%{http_code}" -o /tmp/health_response "$SERVICE_URL/health")
    
    if [ "$response" = "200" ]; then
        print_status "Health check passed!"
        echo "Response:"
        cat /tmp/health_response
    else
        print_warning "Health check returned status: $response"
    fi
    
    print_status "Deployment complete! Your COLMAP processor is running with:"
    print_status "- Official COLMAP Docker image (latest)"
    print_status "- 60-minute timeout"
    print_status "- 16GB memory"
    print_status "- 4 CPU cores"
    print_status "- No more build failures!"
    
else
    print_error "Cloud Run deployment failed"
    exit 1
fi 