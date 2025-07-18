#!/bin/bash

# Firebase Functions Docker Deployment Script for COLMAP Support
# This script deploys Firebase Functions with Docker containers that include COLMAP

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

print_header() {
    echo -e "${BLUE}[DEPLOY]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if Firebase CLI is installed
    if ! command -v firebase &> /dev/null; then
        print_error "Firebase CLI is not installed. Please install it with: npm install -g firebase-tools"
        exit 1
    fi
    
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
    
    # Check if logged into Firebase
    if ! firebase projects:list &> /dev/null; then
        print_error "Not logged into Firebase. Please run: firebase login"
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
    
    # Get Firebase project ID
    PROJECT_ID=$(firebase use | grep -o 'active project: [^[:space:]]*' | cut -d' ' -f3 || echo "")
    
    if [ -z "$PROJECT_ID" ]; then
        print_error "No Firebase project selected. Please run: firebase use <project-id>"
        exit 1
    fi
    
    # Get gcloud project
    GCLOUD_PROJECT=$(gcloud config get-value project)
    
    if [ "$PROJECT_ID" != "$GCLOUD_PROJECT" ]; then
        print_warning "Firebase project ($PROJECT_ID) differs from gcloud project ($GCLOUD_PROJECT)"
        print_status "Setting gcloud project to match Firebase project..."
        gcloud config set project "$PROJECT_ID"
    fi
    
    # Set default values
    REGION=${REGION:-us-central1}
    COLMAP_VERSION=${COLMAP_VERSION:-3.9.1}
    
    print_status "Project ID: $PROJECT_ID"
    print_status "Region: $REGION"
    print_status "COLMAP Version: $COLMAP_VERSION"
}

# Enable required APIs
enable_apis() {
    print_status "Enabling required Google Cloud APIs..."
    
    local apis=(
        "cloudfunctions.googleapis.com"
        "cloudbuild.googleapis.com"
        "artifactregistry.googleapis.com"
        "run.googleapis.com"
        "eventarc.googleapis.com"
        "logging.googleapis.com"
        "storage.googleapis.com"
        "firestore.googleapis.com"
    )
    
    for api in "${apis[@]}"; do
        print_status "Enabling $api..."
        gcloud services enable "$api" --project="$PROJECT_ID" || {
            print_error "Failed to enable $api"
            exit 1
        }
    done
    
    print_status "All required APIs enabled!"
}

# Create Artifact Registry repository
create_artifact_registry() {
    print_status "Setting up Artifact Registry..."
    
    # Check if repository exists
    if gcloud artifacts repositories describe functions --location="$REGION" --project="$PROJECT_ID" &> /dev/null; then
        print_status "Artifact Registry repository 'functions' already exists"
    else
        print_status "Creating Artifact Registry repository..."
        gcloud artifacts repositories create functions \
            --repository-format=docker \
            --location="$REGION" \
            --project="$PROJECT_ID" || {
            print_error "Failed to create Artifact Registry repository"
            exit 1
        }
    fi
    
    # Configure Docker authentication
    print_status "Configuring Docker authentication..."
    gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet || {
        print_error "Failed to configure Docker authentication"
        exit 1
    }
    
    print_status "Artifact Registry setup complete!"
}

# Build and push Docker image
build_and_push_image() {
    print_status "Building and pushing Docker image..."
    
    # Update firebase-docker.json with correct project ID
    sed -i.bak "s/PROJECT_ID/$PROJECT_ID/g" firebase-docker.json
    
    # Build the Docker image
    local image_name="$REGION-docker.pkg.dev/$PROJECT_ID/functions/colmap-function:latest"
    
    print_status "Building Docker image: $image_name"
    cd functions
    docker build -t "$image_name" . || {
        print_error "Failed to build Docker image"
        exit 1
    }
    
    # Push the image
    print_status "Pushing Docker image to Artifact Registry..."
    docker push "$image_name" || {
        print_error "Failed to push Docker image"
        exit 1
    }
    
    cd ..
    print_status "Docker image built and pushed successfully!"
}

# Deploy functions with Docker
deploy_functions() {
    print_status "Deploying Firebase Functions with Docker..."
    
    # Use the Docker configuration
    firebase deploy --only functions --config=firebase-docker.json || {
        print_error "Failed to deploy functions"
        exit 1
    }
    
    print_status "Functions deployed successfully!"
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
        print_status "Response:"
        cat /tmp/health_response
        echo
    else
        print_warning "Health check returned status: $response"
        print_warning "Response may be due to authentication requirements"
    fi
}

# Cleanup
cleanup() {
    print_status "Cleaning up..."
    
    # Restore original firebase-docker.json
    if [ -f firebase-docker.json.bak ]; then
        mv firebase-docker.json.bak firebase-docker.json
    fi
    
    # Clean up temporary files
    rm -f /tmp/health_response
}

# Main deployment flow
main() {
    print_header "Starting Docker deployment for COLMAP Firebase Functions..."
    
    # Set up cleanup trap
    trap cleanup EXIT
    
    check_prerequisites
    get_project_config
    enable_apis
    create_artifact_registry
    build_and_push_image
    deploy_functions
    test_deployment
    
    print_header "Docker deployment completed successfully!"
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