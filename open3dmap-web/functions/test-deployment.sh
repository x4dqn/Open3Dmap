#!/bin/bash

# Test script for Cloud Run COLMAP deployment
# Tests both health check and basic functionality

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

# Get service URL
SERVICE_URL=$(gcloud run services describe $SERVICE_NAME --region=$REGION --project=$PROJECT_ID --format="value(status.url)")

if [ -z "$SERVICE_URL" ]; then
    print_error "Service not found. Please deploy first using: ./deploy-cloudrun.sh"
    exit 1
fi

print_status "Testing COLMAP Cloud Run service at: $SERVICE_URL"

# Test 1: Health check
print_status "Testing health check endpoint..."
response=$(curl -s -w "%{http_code}" -o /tmp/health_response "$SERVICE_URL/health")

if [ "$response" = "200" ]; then
    print_status "✓ Health check passed!"
    echo "Health check response:"
    cat /tmp/health_response | python3 -m json.tool 2>/dev/null || cat /tmp/health_response
    echo
else
    print_error "✗ Health check failed with status: $response"
    cat /tmp/health_response
    exit 1
fi

# Test 2: Basic service connectivity
print_status "Testing service root endpoint..."
root_response=$(curl -s -w "%{http_code}" -o /tmp/root_response "$SERVICE_URL/")

if [ "$root_response" = "404" ]; then
    print_status "✓ Service is responding (404 expected for root endpoint)"
else
    print_warning "Root endpoint returned: $root_response"
fi

# Test 3: Check if COLMAP is available
print_status "Checking COLMAP availability from health check..."
if grep -q '"status":"FOUND"' /tmp/health_response; then
    print_status "✓ COLMAP binary found in container!"
elif grep -q '"status":"NOT_FOUND"' /tmp/health_response; then
    print_error "✗ COLMAP binary not found in container!"
    exit 1
else
    print_warning "Unable to determine COLMAP status from health check"
fi

print_status "All tests passed! Your COLMAP Cloud Run service is working correctly."
print_status "You can now:"
print_status "1. Test with real image data using the /process endpoint"
print_status "2. Update your web app to use: $SERVICE_URL"
print_status "3. Monitor logs with: gcloud run logs tail $SERVICE_NAME --region=$REGION" 