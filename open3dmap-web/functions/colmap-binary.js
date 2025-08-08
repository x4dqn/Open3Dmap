const path = require('path');
const fs = require('fs').promises;
const { execPromise } = require('./utils');

// Pre-built COLMAP binary download and setup
class COLMAPBinaryManager {
  constructor() {
    this.binariesPath = path.join(__dirname, 'binaries');
    this.colmapPath = path.join(this.binariesPath, 'colmap');
    this.installed = false;
  }

  async ensureInstalled() {
    if (this.installed) return;

    try {
      // Check if already exists
      await fs.access(this.colmapPath);
      this.installed = true;
      return;
    } catch (error) {
      // Need to download
    }

    console.log('Downloading COLMAP binary...');
    
    // Create binaries directory
    await fs.mkdir(this.binariesPath, { recursive: true });

    // Download pre-built binary (you'll need to host this)
    const binaryUrl = 'https://github.com/colmap/colmap/releases/download/3.9.1/colmap-3.9.1-linux-x86_64.tar.gz';
    
    try {
      // Simple approach: use a statically linked binary
      const staticBinaryContent = await this.downloadStaticBinary();
      
      await fs.writeFile(this.colmapPath, staticBinaryContent);
      await fs.chmod(this.colmapPath, '755');
      
      console.log('COLMAP binary installed successfully');
      this.installed = true;
      
    } catch (error) {
      console.error('Failed to install COLMAP binary:', error);
      throw error;
    }
  }

  async downloadStaticBinary() {
    // For now, return a placeholder - you would need to:
    // 1. Build a static COLMAP binary
    // 2. Host it somewhere accessible
    // 3. Download it here
    
    throw new Error('Static COLMAP binary not yet available. Please use Docker deployment instead.');
  }

  getColmapPath() {
    return this.colmapPath;
  }
}

module.exports = { COLMAPBinaryManager }; 