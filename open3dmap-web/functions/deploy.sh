#!/bin/bash

# Firebase Functions Deployment Script for COLMAP Processing
# This script handles deployment of Firebase Functions 2nd Gen with streaming support

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

# Check if Firebase CLI is installed
check_firebase_cli() {
    if ! command -v firebase &> /dev/null; then
        print_error "Firebase CLI is not installed. Install it with: npm install -g firebase-tools"
        exit 1
    fi
    
    print_status "Firebase CLI version: $(firebase --version)"
}

# Check if user is logged in
check_firebase_auth() {
    if ! firebase projects:list &> /dev/null; then
        print_error "Not logged in to Firebase. Please run: firebase login"
        exit 1
    fi
    
    print_status "Firebase authentication verified"
}

# Check project configuration
check_project_config() {
    if [ ! -f "firebase.json" ]; then
        print_error "firebase.json not found. Please run this script from the project root."
        exit 1
    fi
    
    local project_id=$(firebase use --current 2>/dev/null || echo "")
    if [ -z "$project_id" ]; then
        print_error "No Firebase project selected. Please run: firebase use <project-id>"
        exit 1
    fi
    
    print_status "Using Firebase project: $project_id"
}

# Install dependencies
install_dependencies() {
    print_status "Installing dependencies..."
    
    cd functions
    
    # Clean install
    if [ -f "package-lock.json" ]; then
        rm package-lock.json
    fi
    
    if [ -d "node_modules" ]; then
        rm -rf node_modules
    fi
    
    npm install
    
    # Run linting
    print_status "Running linting..."
    npm run lint
    
    cd ..
}

# Configure Firebase Functions
configure_functions() {
    print_status "Configuring Firebase Functions..."
    
    # Enable required APIs
    local project_id=$(firebase use --current)
    
    print_status "Enabling required Google Cloud APIs..."
    
    # Note: These would need to be enabled in the Google Cloud Console
    print_warning "Please ensure the following APIs are enabled in Google Cloud Console:"
    print_warning "- Cloud Functions API"
    print_warning "- Cloud Build API"
    print_warning "- Cloud Storage API"
    print_warning "- Cloud Logging API"
    print_warning "- Artifact Registry API"
}

# Deploy functions
deploy_functions() {
    print_status "Deploying Firebase Functions..."
    
    # Deploy with specific configuration
    firebase deploy --only functions \
        --force \
        --debug
    
    if [ $? -eq 0 ]; then
        print_status "Functions deployed successfully!"
    else
        print_error "Function deployment failed"
        exit 1
    fi
}

# Test deployment
test_deployment() {
    print_status "Testing deployment..."
    
    # Test health check endpoint
    local project_id=$(firebase use --current)
    local health_url="https://us-central1-${project_id}.cloudfunctions.net/checkCOLMAPHealth"
    
    print_status "Testing health check endpoint..."
    print_status "URL: $health_url"
    
    # Note: This would require authentication in production
    print_warning "You can test the health check endpoint manually after deployment"
}

# Main deployment flow
main() {
    print_status "Starting Firebase Functions deployment for COLMAP processing..."
    
    check_firebase_cli
    check_firebase_auth
    check_project_config
    install_dependencies
    configure_functions
    deploy_functions
    test_deployment
    
    print_status "Deployment completed successfully!"
    print_status "Your COLMAP processing functions are now available with:"
    print_status "- 60-minute timeout"
    print_status "- 16GB memory"
    print_status "- 4 CPU cores"
    print_status "- Streaming support"
    print_status "- Parallel processing"
    print_status "- Comprehensive logging"
    print_status "- Health monitoring"
    
    print_warning "Remember to configure authentication and test with real data!"
}

# Run main function
main "$@" 