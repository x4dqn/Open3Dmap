#!/bin/bash
set -e

# Install COLMAP and dependencies during function startup
echo "Installing COLMAP dependencies..."

# Update package lists
apt-get update

# Install required packages
apt-get install -y \
    build-essential \
    cmake \
    git \
    wget \
    libboost-program-options-dev \
    libboost-filesystem-dev \
    libboost-graph-dev \
    libboost-regex-dev \
    libboost-system-dev \
    libboost-test-dev \
    libeigen3-dev \
    libsuitesparse-dev \
    libfreeimage-dev \
    libgoogle-glog-dev \
    libgflags-dev \
    libglew-dev \
    libcgal-dev \
    libflann-dev \
    libmetis-dev \
    libsqlite3-dev \
    libatlas-base-dev \
    liblapack-dev \
    libblas-dev \
    libceres-dev

# Download and install COLMAP
cd /tmp
wget -q https://github.com/colmap/colmap/releases/download/3.9.1/colmap-3.9.1-linux.tar.gz
tar -xzf colmap-3.9.1-linux.tar.gz
cp colmap-3.9.1-linux/bin/colmap /usr/local/bin/
chmod +x /usr/local/bin/colmap

# Verify installation
colmap --version

echo "COLMAP installation completed!" 