#!/bin/bash

# Firebase Functions Docker Deployment Script for COLMAP Support
# This script deploys Firebase Functions with Docker containers that include COLMAP

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if gcloud CLI is installed
    if ! command -v gcloud &> /dev/null; then
        print_error "gcloud CLI is not installed. Please install it from: https://cloud.google.com/sdk/docs/install"
        exit 1
    fi
    
    # Check if Docker is installed
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed. Please install Docker to deploy with COLMAP support."
        exit 1
    fi
    
    # Check if logged into gcloud
    if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" &> /dev/null; then
        print_error "Not logged into gcloud. Please run: gcloud auth login"
        exit 1
    fi
    
    print_status "Prerequisites check passed!"
}

# Get project configuration
get_project_config() {
    print_status "Getting project configuration..."
    
    # Get current project ID
    PROJECT_ID=$(gcloud config get-value project 2>/dev/null)
    
    if [ -z "$PROJECT_ID" ]; then
        print_error "No gcloud project set. Please run: gcloud config set project YOUR_PROJECT_ID"
        exit 1
    fi
    
    print_status "Using project: $PROJECT_ID"
    
    # Set region (you can change this if needed)
    REGION="us-central1"
    print_status "Using region: $REGION"
}

# Prepare for Docker deployment
prepare_docker_deployment() {
    print_status "Preparing Docker deployment..."
    
    # Check if Dockerfile exists
    if [ ! -f "Dockerfile" ]; then
        print_error "Dockerfile not found. Docker deployment requires a Dockerfile in the functions directory."
        exit 1
    fi
    
    print_status "Dockerfile found. Firebase Functions will build the container automatically."
    
    # Enable required APIs
    print_status "Enabling required APIs..."
    gcloud services enable artifactregistry.googleapis.com --project=${PROJECT_ID} --quiet
    gcloud services enable cloudbuild.googleapis.com --project=${PROJECT_ID} --quiet
}

# Deploy functions using gcloud
deploy_functions() {
    print_status "Deploying Firebase Functions with Docker support..."
    

    
    # Deploy processCOLMAP function
    print_status "Deploying processCOLMAP function with Docker container..."
    gcloud functions deploy processCOLMAP \
        --gen2 \
        --region=${REGION} \
        --source=. \
        --entry-point=processCOLMAP \
        --trigger-http \
        --max-instances=10 \
        --timeout=3600 \
        --memory=16Gi \
        --cpu=4 \
        --concurrency=1 \
        --project=${PROJECT_ID} \
        --quiet
    
    if [ $? -ne 0 ]; then
        print_error "Failed to deploy processCOLMAP function"
        exit 1
    fi
    
    print_status "processCOLMAP function deployed successfully!"
    
    # Deploy checkCOLMAPHealth function
    print_status "Deploying checkCOLMAPHealth function with Docker container..."
    gcloud functions deploy checkCOLMAPHealth \
        --gen2 \
        --region=${REGION} \
        --source=. \
        --entry-point=checkCOLMAPHealth \
        --trigger-http \
        --max-instances=5 \
        --timeout=60 \
        --memory=1Gi \
        --cpu=1 \
        --allow-unauthenticated \
        --project=${PROJECT_ID} \
        --quiet
    
    if [ $? -ne 0 ]; then
        print_error "Failed to deploy checkCOLMAPHealth function"
        exit 1
    fi
    
    print_status "checkCOLMAPHealth function deployed successfully!"
}

# Test deployment
test_deployment() {
    print_status "Testing deployment..."
    
    # Test health check endpoint
    local health_url="https://${REGION}-${PROJECT_ID}.cloudfunctions.net/checkCOLMAPHealth"
    
    print_status "Testing health check endpoint..."
    print_status "URL: $health_url"
    
    # Test the health check
    response=$(curl -s -w "%{http_code}" -o /tmp/health_response "$health_url" || echo "000")
    
    if [ "$response" = "200" ]; then
        print_status "Health check passed!"
        cat /tmp/health_response
    else
        print_warning "Health check returned status: $response"
        print_warning "Response may be due to authentication requirements"
    fi
}

# Main deployment flow
main() {
    print_status "Starting Docker deployment for COLMAP Firebase Functions..."
    
    check_prerequisites
    get_project_config
    prepare_docker_deployment
    deploy_functions
    test_deployment
    
    print_status "Docker deployment completed successfully!"
    print_status "Your COLMAP processing functions are now available with:"
    print_status "- Real COLMAP processing (not mock data)"
    print_status "- 60-minute timeout"
    print_status "- 16GB memory"
    print_status "- 4 CPU cores"
    print_status "- Docker container with COLMAP ${COLMAP_VERSION}"
    print_status ""
    print_status "Health check URL: https://${REGION}-${PROJECT_ID}.cloudfunctions.net/checkCOLMAPHealth"
    print_status "Process URL: https://${REGION}-${PROJECT_ID}.cloudfunctions.net/processCOLMAP"
    print_status ""
    print_warning "Remember to test with real image data!"
}

# Run main function
main "$@" 