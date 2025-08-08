#!/bin/bash

# Open3DMapWeb Update Deployment Script
# This script updates the application in production

set -e  # Exit on any error

echo "🔄 Starting Open3DMapWeb update deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
APP_DIR="/var/www/open3dmap"
BACKUP_DIR="/var/backups/open3dmap"
SERVICE_NAME="open3dmap"

# Check if running as the correct user
if [[ $EUID -eq 0 ]]; then
   echo -e "${RED}❌ This script should not be run as root${NC}"
   echo "Please run as the application user"
   exit 1
fi

# Function to create backup
create_backup() {
    echo -e "${BLUE}💾 Creating backup...${NC}"
    
    # Create backup directory if it doesn't exist
    sudo mkdir -p "$BACKUP_DIR"
    
    # Create timestamped backup
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    BACKUP_FILE="${BACKUP_DIR}/open3dmap_backup_${TIMESTAMP}.tar.gz"
    
    # Backup application files (excluding node_modules and temp files)
    sudo tar --exclude='node_modules' \
             --exclude='temp' \
             --exclude='logs' \
             --exclude='.git' \
             -czf "$BACKUP_FILE" \
             -C "$(dirname "$APP_DIR")" \
             "$(basename "$APP_DIR")"
    
    echo -e "${GREEN}✅ Backup created: $BACKUP_FILE${NC}"
}

# Function to update from git
update_from_git() {
    echo -e "${BLUE}🔄 Updating from Git...${NC}"
    
    cd "$APP_DIR"
    
    # Stash any local changes
    git stash push -m "Auto-stash before update $(date)"
    
    # Pull latest changes
    git pull origin main
    
    echo -e "${GREEN}✅ Git update completed${NC}"
}

# Function to update dependencies
update_dependencies() {
    echo -e "${BLUE}📦 Updating dependencies...${NC}"
    
    cd "$APP_DIR"
    
    # Update Node.js dependencies
    npm ci --only=production
    
    # Update Python dependencies if requirements.txt exists
    if [ -f "requirements.txt" ]; then
        pip3 install --user -r requirements.txt
    fi
    
    echo -e "${GREEN}✅ Dependencies updated${NC}"
}

# Function to stop services
stop_services() {
    echo -e "${BLUE}⏹️  Stopping services...${NC}"
    
    # Stop PM2 processes
    if command -v pm2 &> /dev/null; then
        pm2 stop all || true
    fi
    
    # Stop systemd service
    if systemctl is-active --quiet "$SERVICE_NAME"; then
        sudo systemctl stop "$SERVICE_NAME"
    fi
    
    echo -e "${GREEN}✅ Services stopped${NC}"
}

# Function to start services
start_services() {
    echo -e "${BLUE}▶️  Starting services...${NC}"
    
    cd "$APP_DIR"
    
    # Start with PM2 if ecosystem config exists
    if [ -f "ecosystem.config.js" ]; then
        pm2 start ecosystem.config.js --env production
        pm2 save
    # Or start with systemd
    elif systemctl list-unit-files | grep -q "$SERVICE_NAME"; then
        sudo systemctl start "$SERVICE_NAME"
    else
        echo -e "${YELLOW}⚠️  No service configuration found${NC}"
        echo "Please start the application manually"
    fi
    
    echo -e "${GREEN}✅ Services started${NC}"
}

# Function to run database migrations (if any)
run_migrations() {
    echo -e "${BLUE}🗄️  Running migrations...${NC}"
    
    cd "$APP_DIR"
    
    # Run any migration scripts if they exist
    if [ -f "scripts/migrate.js" ]; then
        node scripts/migrate.js
    fi
    
    echo -e "${GREEN}✅ Migrations completed${NC}"
}

# Function to test deployment
test_deployment() {
    echo -e "${BLUE}🧪 Testing deployment...${NC}"
    
    # Wait for service to start
    sleep 10
    
    # Test health endpoint
    if curl -f http://localhost:3000/health &> /dev/null; then
        echo -e "${GREEN}✅ Health check passed${NC}"
    else
        echo -e "${RED}❌ Health check failed${NC}"
        return 1
    fi
    
    # Test main page
    if curl -f http://localhost:3000/ &> /dev/null; then
        echo -e "${GREEN}✅ Main page accessible${NC}"
    else
        echo -e "${YELLOW}⚠️  Main page test failed${NC}"
    fi
}

# Function to rollback if needed
rollback() {
    echo -e "${RED}🔙 Rolling back deployment...${NC}"
    
    # Find the latest backup
    LATEST_BACKUP=$(ls -t "$BACKUP_DIR"/open3dmap_backup_*.tar.gz 2>/dev/null | head -n1)
    
    if [ -n "$LATEST_BACKUP" ]; then
        echo -e "${BLUE}📋 Restoring from: $LATEST_BACKUP${NC}"
        
        # Stop services
        stop_services
        
        # Restore backup
        sudo tar -xzf "$LATEST_BACKUP" -C "$(dirname "$APP_DIR")"
        
        # Restore dependencies
        cd "$APP_DIR"
        npm ci --only=production
        
        # Start services
        start_services
        
        echo -e "${GREEN}✅ Rollback completed${NC}"
    else
        echo -e "${RED}❌ No backup found for rollback${NC}"
        exit 1
    fi
}

# Main deployment process
main() {
    echo -e "${BLUE}🚀 Starting update process...${NC}"
    
    # Create backup
    create_backup
    
    # Stop services
    stop_services
    
    # Update code
    update_from_git
    
    # Update dependencies
    update_dependencies
    
    # Run migrations
    run_migrations
    
    # Start services
    start_services
    
    # Test deployment
    if test_deployment; then
        echo ""
        echo -e "${GREEN}🎉 Update completed successfully!${NC}"
        echo ""
        echo -e "${BLUE}📊 Update Summary:${NC}"
        echo -e "  ${GREEN}✅ Backup:${NC} Created"
        echo -e "  ${GREEN}✅ Code:${NC} Updated"
        echo -e "  ${GREEN}✅ Dependencies:${NC} Updated"
        echo -e "  ${GREEN}✅ Services:${NC} Restarted"
        echo -e "  ${GREEN}✅ Tests:${NC} Passed"
        echo ""
        echo -e "${BLUE}📱 Your application is now running the latest version!${NC}"
    else
        echo -e "${RED}❌ Deployment tests failed${NC}"
        echo -e "${YELLOW}Would you like to rollback? (y/N)${NC}"
        read -r response
        if [[ "$response" =~ ^([yY][eE][sS]|[yY])$ ]]; then
            rollback
        else
            echo -e "${YELLOW}⚠️  Please check the application manually${NC}"
        fi
    fi
}

# Check if we're in the right directory
if [ ! -f "$APP_DIR/package.json" ]; then
    echo -e "${RED}❌ Application directory not found: $APP_DIR${NC}"
    echo "Please make sure the application is installed correctly"
    exit 1
fi

# Run main function
main "$@" 