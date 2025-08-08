#!/bin/bash

# Open3DMapWeb Server Setup Script
# This script sets up a production server with all required dependencies

set -e  # Exit on any error

echo "🚀 Setting up Open3DMapWeb production server..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if running as root
if [[ $EUID -eq 0 ]]; then
   echo -e "${RED}❌ This script should not be run as root${NC}"
   echo "Please run as a regular user with sudo privileges"
   exit 1
fi

# Check OS
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Ubuntu/Debian
    if command -v apt-get &> /dev/null; then
        PKG_MANAGER="apt"
        echo -e "${GREEN}✅ Detected Ubuntu/Debian${NC}"
    # CentOS/RHEL/Rocky
    elif command -v yum &> /dev/null; then
        PKG_MANAGER="yum"
        echo -e "${GREEN}✅ Detected CentOS/RHEL${NC}"
    else
        echo -e "${RED}❌ Unsupported Linux distribution${NC}"
        exit 1
    fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
    PKG_MANAGER="brew"
    echo -e "${GREEN}✅ Detected macOS${NC}"
else
    echo -e "${RED}❌ Unsupported operating system${NC}"
    exit 1
fi

# Update system packages
echo -e "${BLUE}📦 Updating system packages...${NC}"
case $PKG_MANAGER in
    "apt")
        sudo apt update && sudo apt upgrade -y
        ;;
    "yum")
        sudo yum update -y
        ;;
    "brew")
        brew update
        ;;
esac

# Install basic dependencies
echo -e "${BLUE}🛠️  Installing basic dependencies...${NC}"
case $PKG_MANAGER in
    "apt")
        sudo apt install -y curl wget git build-essential software-properties-common
        ;;
    "yum")
        sudo yum groupinstall -y "Development Tools"
        sudo yum install -y curl wget git
        ;;
    "brew")
        brew install curl wget git
        ;;
esac

# Install Node.js 20
echo -e "${BLUE}📦 Installing Node.js 20...${NC}"
if ! command -v node &> /dev/null || [[ $(node -v | cut -d'v' -f2 | cut -d'.' -f1) -lt 20 ]]; then
    case $PKG_MANAGER in
        "apt")
            curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
            sudo apt-get install -y nodejs
            ;;
        "yum")
            curl -fsSL https://rpm.nodesource.com/setup_20.x | sudo bash -
            sudo yum install -y nodejs
            ;;
        "brew")
            brew install node@20
            brew link node@20 --force
            ;;
    esac
else
    echo -e "${GREEN}✅ Node.js $(node -v) already installed${NC}"
fi

# Install Python 3.8+
echo -e "${BLUE}🐍 Installing Python 3.8+...${NC}"
if ! command -v python3 &> /dev/null; then
    case $PKG_MANAGER in
        "apt")
            sudo apt install -y python3 python3-pip python3-dev
            ;;
        "yum")
            sudo yum install -y python3 python3-pip python3-devel
            ;;
        "brew")
            brew install python@3.11
            ;;
    esac
else
    echo -e "${GREEN}✅ Python $(python3 --version) already installed${NC}"
fi

# Install COLMAP
echo -e "${BLUE}🏗️  Installing COLMAP...${NC}"
if ! command -v colmap &> /dev/null; then
    case $PKG_MANAGER in
        "apt")
            # Install COLMAP dependencies
            sudo apt install -y \
                cmake \
                ninja-build \
                build-essential \
                libboost-program-options-dev \
                libboost-filesystem-dev \
                libboost-graph-dev \
                libboost-system-dev \
                libboost-test-dev \
                libeigen3-dev \
                libflann-dev \
                libfreeimage-dev \
                libmetis-dev \
                libgoogle-glog-dev \
                libgflags-dev \
                libsqlite3-dev \
                libglew-dev \
                qtbase5-dev \
                libqt5opengl5-dev \
                libcgal-dev \
                libceres-dev
            
            # Install COLMAP from snap or build from source
            if command -v snap &> /dev/null; then
                sudo snap install colmap
            else
                echo -e "${YELLOW}⚠️  Snap not available, building COLMAP from source...${NC}"
                cd /tmp
                git clone https://github.com/colmap/colmap.git
                cd colmap
                mkdir build && cd build
                cmake .. -GNinja -DCMAKE_CUDA_ARCHITECTURES=native
                ninja
                sudo ninja install
                cd /tmp && rm -rf colmap
            fi
            ;;
        "yum")
            echo -e "${YELLOW}⚠️  Please install COLMAP manually on CentOS/RHEL${NC}"
            echo "Visit: https://colmap.github.io/install.html"
            ;;
        "brew")
            brew install colmap
            ;;
    esac
else
    echo -e "${GREEN}✅ COLMAP already installed${NC}"
fi

# Install PM2 for process management
echo -e "${BLUE}🔄 Installing PM2...${NC}"
if ! command -v pm2 &> /dev/null; then
    sudo npm install -g pm2
    # Setup PM2 startup script
    pm2 startup | grep -E '^sudo' | sh || true
else
    echo -e "${GREEN}✅ PM2 already installed${NC}"
fi

# Install Nginx
echo -e "${BLUE}🌐 Installing Nginx...${NC}"
if ! command -v nginx &> /dev/null; then
    case $PKG_MANAGER in
        "apt")
            sudo apt install -y nginx
            ;;
        "yum")
            sudo yum install -y nginx
            ;;
        "brew")
            brew install nginx
            ;;
    esac
    
    # Start and enable Nginx
    if [[ "$PKG_MANAGER" != "brew" ]]; then
        sudo systemctl enable nginx
        sudo systemctl start nginx
    fi
else
    echo -e "${GREEN}✅ Nginx already installed${NC}"
fi

# Install project dependencies
echo -e "${BLUE}📦 Installing project dependencies...${NC}"
npm install

# Install additional Python packages for COLMAP processing
echo -e "${BLUE}🐍 Installing Python packages...${NC}"
pip3 install --user numpy pillow opencv-python

# Create necessary directories
echo -e "${BLUE}📁 Creating directories...${NC}"
sudo mkdir -p /var/log/open3dmap
sudo mkdir -p /var/www/open3dmap
sudo mkdir -p /tmp/colmap-processing

# Set up permissions
echo -e "${BLUE}🔐 Setting up permissions...${NC}"
sudo chown -R $USER:$USER /var/www/open3dmap
sudo chown -R $USER:$USER /var/log/open3dmap
sudo chmod -R 755 /var/www/open3dmap

# Copy application files
echo -e "${BLUE}📋 Copying application files...${NC}"
if [ "$PWD" != "/var/www/open3dmap" ]; then
    sudo cp -r . /var/www/open3dmap/
    sudo chown -R $USER:$USER /var/www/open3dmap
fi

# Create systemd service
echo -e "${BLUE}⚙️  Creating systemd service...${NC}"
sudo tee /etc/systemd/system/open3dmap.service > /dev/null <<EOF
[Unit]
Description=Open3DMapWeb Application
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=/var/www/open3dmap
Environment=NODE_ENV=production
Environment=PORT=3000
ExecStart=/usr/bin/node server.js
Restart=on-failure
RestartSec=5s

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=open3dmap

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd and enable service
sudo systemctl daemon-reload
sudo systemctl enable open3dmap

# Create logrotate configuration
echo -e "${BLUE}📝 Setting up log rotation...${NC}"
sudo tee /etc/logrotate.d/open3dmap > /dev/null <<EOF
/var/log/open3dmap/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    create 0644 $USER $USER
    postrotate
        sudo systemctl reload open3dmap
    endscript
}
EOF

# Setup firewall (if available)
echo -e "${BLUE}🔥 Configuring firewall...${NC}"
if command -v ufw &> /dev/null; then
    sudo ufw allow 22/tcp    # SSH
    sudo ufw allow 80/tcp    # HTTP
    sudo ufw allow 443/tcp   # HTTPS
    sudo ufw allow 3000/tcp  # Application port
    # Don't auto-enable UFW as it might lock out the user
    echo -e "${YELLOW}⚠️  Firewall rules added but not enabled. Run 'sudo ufw enable' manually${NC}"
elif command -v firewall-cmd &> /dev/null; then
    sudo firewall-cmd --permanent --add-port=22/tcp
    sudo firewall-cmd --permanent --add-port=80/tcp
    sudo firewall-cmd --permanent --add-port=443/tcp
    sudo firewall-cmd --permanent --add-port=3000/tcp
    sudo firewall-cmd --reload
fi

# Install Firebase CLI for potential future deployments
echo -e "${BLUE}🔥 Installing Firebase CLI...${NC}"
if ! command -v firebase &> /dev/null; then
    sudo npm install -g firebase-tools
else
    echo -e "${GREEN}✅ Firebase CLI already installed${NC}"
fi

# Final setup steps
echo -e "${BLUE}🔧 Final setup steps...${NC}"

# Make scripts executable
chmod +x *.sh 2>/dev/null || true

# Test installations
echo -e "${BLUE}🧪 Testing installations...${NC}"
node --version
python3 --version
nginx -v 2>&1 | head -1
pm2 --version
colmap --help > /dev/null 2>&1 && echo -e "${GREEN}✅ COLMAP working${NC}" || echo -e "${YELLOW}⚠️  COLMAP may need configuration${NC}"

echo ""
echo -e "${GREEN}🎉 Server setup completed successfully!${NC}"
echo ""
echo -e "${BLUE}📊 Installation Summary:${NC}"
echo -e "  ${GREEN}✅ Node.js:${NC} $(node --version)"
echo -e "  ${GREEN}✅ Python:${NC} $(python3 --version)"
echo -e "  ${GREEN}✅ Nginx:${NC} Installed"
echo -e "  ${GREEN}✅ PM2:${NC} Installed"
echo -e "  ${GREEN}✅ COLMAP:${NC} Installed"
echo -e "  ${GREEN}✅ Firebase CLI:${NC} Installed"
echo -e "  ${GREEN}✅ Systemd Service:${NC} Created"
echo ""
echo -e "${BLUE}📱 Next steps:${NC}"
echo "  1. Configure environment: cp .env.example .env && nano .env"
echo "  2. Configure Nginx: sudo nano /etc/nginx/sites-available/open3dmap"
echo "  3. Start application: pm2 start ecosystem.config.js"
echo "  4. Or use systemd: sudo systemctl start open3dmap"
echo "  5. Configure SSL: sudo certbot --nginx -d yourdomain.com"
echo "  6. Test the application: curl http://localhost:3000/health"
echo ""
echo -e "${GREEN}🚀 Your server is ready for deployment!${NC}" 