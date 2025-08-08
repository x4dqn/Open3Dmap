#!/bin/bash

# Open3DMapWeb Firebase Deployment Script
# This script deploys the entire application to Firebase

set -e  # Exit on any error

echo "🚀 Starting Firebase deployment for Open3DMapWeb..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if Firebase CLI is installed
if ! command -v firebase &> /dev/null; then
    echo -e "${RED}❌ Firebase CLI is not installed. Please install it first:${NC}"
    echo "npm install -g firebase-tools"
    exit 1
fi

# Check if user is logged in
if ! firebase projects:list &> /dev/null; then
    echo -e "${YELLOW}⚠️  You are not logged in to Firebase. Please login first:${NC}"
    echo "firebase login"
    exit 1
fi

# Get the current directory
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$DEPLOY_DIR")"

echo -e "${BLUE}📁 Deploy directory: $DEPLOY_DIR${NC}"
echo -e "${BLUE}📁 Project root: $PROJECT_ROOT${NC}"

# Check if firebase.json exists
if [ ! -f "$PROJECT_ROOT/firebase.json" ]; then
    echo -e "${RED}❌ firebase.json not found in project root${NC}"
    exit 1
fi

# Set the Firebase project (if not already set)
echo -e "${BLUE}🔧 Setting up Firebase project...${NC}"
cd "$PROJECT_ROOT"

# Check if project is already set
if ! firebase use --version &> /dev/null; then
    echo -e "${YELLOW}⚠️  Firebase project not set. Please set your project:${NC}"
    echo "firebase use --add"
    echo "Select your project: openarmap"
    exit 1
fi

# Step 1: Copy necessary files to deploy directory
echo -e "${BLUE}📋 Copying files to deploy directory...${NC}"

# Copy main HTML files
cp "$PROJECT_ROOT/../dashboard.html" "$DEPLOY_DIR/" 2>/dev/null || echo "dashboard.html not found in root"
cp "$PROJECT_ROOT/../index.html" "$DEPLOY_DIR/" 2>/dev/null || echo "index.html not found in root"

# Copy JS files
if [ -d "$PROJECT_ROOT/../js" ]; then
    cp -r "$PROJECT_ROOT/../js" "$DEPLOY_DIR/"
fi

# Copy styles
if [ -d "$PROJECT_ROOT/../styles" ]; then
    cp -r "$PROJECT_ROOT/../styles" "$DEPLOY_DIR/"
fi

# Copy assets
if [ -d "$PROJECT_ROOT/assets" ]; then
    cp -r "$PROJECT_ROOT/assets" "$DEPLOY_DIR/"
fi

# Step 2: Build/prepare functions
echo -e "${BLUE}🔧 Preparing Firebase Functions...${NC}"
cd "$PROJECT_ROOT/functions"

# Install function dependencies
echo -e "${YELLOW}📦 Installing function dependencies...${NC}"
npm install

# Optional: Run linting
if [ -f "package.json" ] && grep -q "lint" package.json; then
    echo -e "${YELLOW}🔍 Running linting...${NC}"
    npm run lint --if-present || echo "Linting failed, continuing..."
fi

# Step 3: Deploy Functions
echo -e "${BLUE}🚀 Deploying Firebase Functions...${NC}"
cd "$PROJECT_ROOT"
firebase deploy --only functions

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Functions deployed successfully${NC}"
else
    echo -e "${RED}❌ Functions deployment failed${NC}"
    exit 1
fi

# Step 4: Deploy Firestore Rules
echo -e "${BLUE}🔐 Deploying Firestore Rules...${NC}"
if [ -f "$PROJECT_ROOT/firestore.rules" ]; then
    firebase deploy --only firestore:rules
    echo -e "${GREEN}✅ Firestore rules deployed${NC}"
else
    echo -e "${YELLOW}⚠️  No firestore.rules file found${NC}"
fi

# Step 5: Deploy Storage Rules
echo -e "${BLUE}🗄️  Deploying Storage Rules...${NC}"
if [ -f "$PROJECT_ROOT/storage.rules" ]; then
    firebase deploy --only storage
    echo -e "${GREEN}✅ Storage rules deployed${NC}"
else
    echo -e "${YELLOW}⚠️  No storage.rules file found${NC}"
fi

# Step 6: Deploy Hosting
echo -e "${BLUE}🌐 Deploying Firebase Hosting...${NC}"
cd "$DEPLOY_DIR"

# Create a temporary firebase.json for hosting deployment
cat > firebase.json << EOF
{
  "hosting": {
    "public": ".",
    "ignore": [
      "firebase.json",
      "**/.*",
      "**/node_modules/**",
      "**/*.sh",
      "**/*.md",
      "temp/**",
      "output/**"
    ],
    "rewrites": [
      {
        "source": "**",
        "destination": "/index.html"
      }
    ],
    "headers": [
      {
        "source": "**/*.@(js|css)",
        "headers": [
          {
            "key": "Cache-Control",
            "value": "max-age=31536000"
          }
        ]
      },
      {
        "source": "**/*.@(jpg|jpeg|gif|png|svg|webp|wasm)",
        "headers": [
          {
            "key": "Cache-Control",
            "value": "max-age=31536000"
          }
        ]
      },
      {
        "source": "**/*.@(html)",
        "headers": [
          {
            "key": "Cache-Control",
            "value": "no-cache, must-revalidate"
          }
        ]
      }
    ],
    "cleanUrls": false,
    "trailingSlash": false
  }
}
EOF

firebase deploy --only hosting

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Hosting deployed successfully${NC}"
else
    echo -e "${RED}❌ Hosting deployment failed${NC}"
    exit 1
fi

# Clean up temporary firebase.json
rm firebase.json

# Step 7: Get deployment URLs
echo -e "${BLUE}🔗 Getting deployment information...${NC}"
cd "$PROJECT_ROOT"

# Get the hosting URL
HOSTING_URL=$(firebase hosting:channel:list --json | jq -r '.result[0].url' 2>/dev/null || echo "Unable to get URL")

echo ""
echo -e "${GREEN}🎉 Deployment completed successfully!${NC}"
echo ""
echo -e "${BLUE}📊 Deployment Summary:${NC}"
echo -e "  ${GREEN}✅ Functions:${NC} Deployed"
echo -e "  ${GREEN}✅ Hosting:${NC} Deployed"
echo -e "  ${GREEN}✅ Firestore Rules:${NC} Deployed"
echo -e "  ${GREEN}✅ Storage Rules:${NC} Deployed"
echo ""
echo -e "${BLUE}🌐 Your app is available at:${NC}"
echo -e "  ${GREEN}$HOSTING_URL${NC}"
echo ""
echo -e "${BLUE}📱 Next steps:${NC}"
echo "  1. Test your application"
echo "  2. Update DNS if using custom domain"
echo "  3. Monitor logs: firebase functions:log"
echo "  4. Set up monitoring in Firebase Console"
echo ""
echo -e "${GREEN}🚀 Happy deploying!${NC}" 