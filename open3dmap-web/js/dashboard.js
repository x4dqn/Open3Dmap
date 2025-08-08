// Firebase imports will be used from window.firebaseModules (loaded in HTML)
// This ensures version consistency with the main Firebase SDK

class DashboardManager {
    constructor(authManager) {
        this.authManager = authManager;
        this.firestore = authManager.firestore;
        this.currentUser = authManager.currentUser;
        this.scans = [];
        this.currentViewMode = 'grid'; // 'grid' or 'list'
        this.currentScanForViewer = null;
        this.init();
    }

    init() {
        if (!this.currentUser) {
            console.log('Dashboard: No user found, waiting for auth ready.');
            return;
        }
        console.log('DashboardManager initialized for user:', this.currentUser.uid);
        this.loadUserScans();
        this.setupEventListeners();
    }

    // Resolve Cloud Run COLMAP service URL from global/window configuration or fallback placeholder
    getColmapServiceUrl() {
        // Allow override via window.COLMAP_SERVICE_URL or meta tag
        if (window.COLMAP_SERVICE_URL && typeof window.COLMAP_SERVICE_URL === 'string') {
            return window.COLMAP_SERVICE_URL;
        }
        const meta = document.querySelector('meta[name="colmap-service-url"]');
        if (meta && meta.content) return meta.content;
        // Default placeholder for open source users to replace
        return 'https://YOUR_CLOUD_RUN_SERVICE_URL';
    }

    setupEventListeners() {
        // Handle upload button click
        const uploadBtn = document.getElementById('upload-btn');
        if (uploadBtn) {
            uploadBtn.addEventListener('click', () => {
                window.location.href = 'index.html';
            });
        }

        // Handle view mode toggle
        const viewModeBtn = document.getElementById('view-mode-btn');
        if (viewModeBtn) {
            viewModeBtn.addEventListener('click', () => {
                this.toggleViewMode();
            });
        }

        // Setup mobile navigation
        this.setupMobileMenu();
    }

    setupMobileMenu() {
        const navToggle = document.getElementById('nav-toggle');
        const navClose = document.getElementById('nav-close');
        const navOverlay = document.getElementById('nav-overlay');
        const navMobile = document.getElementById('nav-menu-mobile');
        
        if (navToggle) {
            navToggle.addEventListener('click', () => {
                this.openMobileMenu();
            });
        }
        
        if (navClose) {
            navClose.addEventListener('click', () => {
                this.closeMobileMenu();
            });
        }
        
        if (navOverlay) {
            navOverlay.addEventListener('click', () => {
                this.closeMobileMenu();
            });
        }
        
        // Close mobile menu when clicking on nav links
        if (navMobile) {
            const navLinks = navMobile.querySelectorAll('.nav-link');
            navLinks.forEach(link => {
                link.addEventListener('click', () => {
                    this.closeMobileMenu();
                });
            });
        }
        
        // Handle escape key to close mobile menu
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                this.closeMobileMenu();
            }
        });
    }

    openMobileMenu() {
        const navOverlay = document.getElementById('nav-overlay');
        const navMobile = document.getElementById('nav-menu-mobile');
        
        if (navOverlay) {
            navOverlay.classList.add('active');
        }
        
        if (navMobile) {
            navMobile.classList.add('active');
        }
        
        // Prevent body scroll when mobile menu is open
        document.body.style.overflow = 'hidden';
    }

    closeMobileMenu() {
        const navOverlay = document.getElementById('nav-overlay');
        const navMobile = document.getElementById('nav-menu-mobile');
        
        if (navOverlay) {
            navOverlay.classList.remove('active');
        }
        
        if (navMobile) {
            navMobile.classList.remove('active');
        }
        
        // Restore body scroll
        document.body.style.overflow = '';
    }

    async loadUserScans() {
        if (!this.currentUser) {
            console.error('No user logged in to load scans for.');
            return;
        }
        
        this.authManager.showLoading(true);
        const { collection, query, where, orderBy, getDocs } = window.firebaseModules;
        
        try {
            const scansRef = collection(this.firestore, 'ar_scans');
            const q = query(
                scansRef,
                where('userId', '==', this.currentUser.uid),
                orderBy('createdAt', 'desc')
            );
            
            const querySnapshot = await getDocs(q);
            this.scans = querySnapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
            
            // Debug: Log scan data to see available fields
            if (this.scans.length > 0) {
                console.log('Sample scan data:', this.scans[0]);
                console.log('Available fields:', Object.keys(this.scans[0]));
            }

            this.renderScans(this.scans);
        } catch (error) {
            console.error('Error loading user scans:', error);
            this.authManager.showToast('Failed to load scans.', 'error');
        } finally {
            this.authManager.showLoading(false);
        }
    }

    renderScans(scans) {
        const scansGrid = document.getElementById('scans-grid');
        if (!scansGrid) return;

        if (scans.length === 0) {
            scansGrid.innerHTML = `
                <div class="no-scans-message">
                    <i class="fas fa-camera" style="font-size: 3rem; margin-bottom: 1rem; color: var(--text-muted);"></i>
                    <h3>No scans yet</h3>
                    <p>Start by uploading your first 3D scan to see it here.</p>
                </div>
            `;
            return;
        }

        scansGrid.innerHTML = scans.map(scan => {
            const thumbnailUrl = scan.thumbnailUrl || scan.photoUrls?.[0] || 'assets/placeholder.png';
            const scanName = scan.title || scan.name || 'Untitled Scan';
            const location = this.formatLocationDisplay(scan);
            const date = scan.createdAt ? this.formatDate(scan.createdAt) : 'Unknown date';
            const imageCount = scan.photoUrls ? scan.photoUrls.length : 0;

            return `
                <div class="scan-card" data-scan-id="${scan.id}">
                    <div class="scan-card-thumbnail" style="background-image: url('${thumbnailUrl}')">
                        ${imageCount > 0 ? `<div class="image-count-badge">${imageCount} images</div>` : ''}
                    </div>
                    <div class="scan-card-info">
                        <h3>${scanName}</h3>
                        <p><i class="fas fa-map-marker-alt"></i> ${location}</p>
                        <p><i class="fas fa-calendar-alt"></i> ${date}</p>
                    </div>
                    <div class="scan-card-actions">
                        <button class="btn-icon" onclick="window.dashboardManager.viewScan('${scan.id}')" title="View Images">
                            <i class="fas fa-images"></i>
                        </button>
                        <button class="btn-icon" onclick="window.dashboardManager.editScan('${scan.id}')" title="Edit">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="btn-icon btn-primary" onclick="window.dashboardManager.showTrainingModal('${scan.id}')" title="Train Gaussian Splat">
                            <i class="fas fa-magic"></i>
                        </button>
                        <button class="btn-icon" onclick="window.dashboardManager.downloadScanImages('${scan.id}')" title="Download Images">
                            <i class="fas fa-download"></i>
                        </button>
                        <button class="btn-icon" onclick="window.dashboardManager.deleteScan('${scan.id}')" title="Delete">
                            <i class="fas fa-trash"></i>
                        </button>
                    </div>
                </div>
            `;
        }).join('');
    }

    formatLocationDisplay(scan) {
        if (!scan) return 'Location unknown';

        // Try to get location from anchorGps first
        if (scan.anchorGps?.latitude && scan.anchorGps?.longitude) {
            const lat = scan.anchorGps.latitude.toFixed(6);
            const lng = scan.anchorGps.longitude.toFixed(6);
            const accuracy = scan.anchorGps.accuracy ? ` (±${Math.round(scan.anchorGps.accuracy)}m)` : '';
            const altitude = scan.anchorGps.altitude ? ` • ${Math.round(scan.anchorGps.altitude)}m alt` : '';
            return `${lat}, ${lng}${accuracy}${altitude}`;
        }

        // Fallback to location field
        if (scan.location?.latitude && scan.location?.longitude) {
            const lat = scan.location.latitude.toFixed(6);
            const lng = scan.location.longitude.toFixed(6);
            return `${lat}, ${lng}`;
        }

        return 'Location unknown';
    }

    formatDate(timestamp) {
        if (!timestamp) return 'Unknown date';
        const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp);
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    // View scan images in modal
    async viewScan(scanId) {
        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            this.authManager.showToast('Scan not found.', 'error');
            return;
        }

        this.currentScanForViewer = scan;
        const modal = document.getElementById('image-viewer-modal');
        const scanName = document.getElementById('viewer-scan-name');
        const imageCount = document.getElementById('image-count');
        const imagesContainer = document.getElementById('images-container');

        // Set scan name
        scanName.textContent = scan.title || scan.name || 'Untitled Scan';

        // Set image count
        const totalImages = scan.photoUrls ? scan.photoUrls.length : 0;
        imageCount.textContent = `${totalImages} image${totalImages !== 1 ? 's' : ''}`;

        // Render images
        this.renderImages(scan.photoUrls || []);

        // Show modal
        modal.style.display = 'flex';
        setTimeout(() => modal.classList.add('active'), 10);
    }

    renderImages(imageUrls) {
        const imagesContainer = document.getElementById('images-container');
        imagesContainer.className = `images-container ${this.currentViewMode}-view`;

        if (imageUrls.length === 0) {
            imagesContainer.innerHTML = `
                <div style="text-align: center; padding: 2rem; color: var(--text-secondary);">
                    <i class="fas fa-image" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                    <p>No images available for this scan.</p>
                </div>
            `;
            return;
        }

        imagesContainer.innerHTML = imageUrls.map((url, index) => {
            const fileName = url.split('/').pop().split('?')[0] || `Image ${index + 1}`;
            const fileSize = 'Unknown size'; // We could fetch this if needed

            if (this.currentViewMode === 'grid') {
                return `
                    <div class="image-item" onclick="window.dashboardManager.openFullscreenImage('${url}', '${fileName}')">
                        <img src="${url}" alt="${fileName}" loading="lazy">
                        <div class="image-info">
                            <div class="image-name">${fileName}</div>
                            <div class="image-size">${fileSize}</div>
                        </div>
                    </div>
                `;
            } else {
                return `
                    <div class="image-item">
                        <img src="${url}" alt="${fileName}" loading="lazy" onclick="window.dashboardManager.openFullscreenImage('${url}', '${fileName}')">
                        <div class="image-info">
                            <div class="image-details">
                                <div class="image-name">${fileName}</div>
                                <div class="image-size">${fileSize}</div>
                            </div>
                            <div class="image-actions">
                                <button class="btn-icon" onclick="window.dashboardManager.openFullscreenImage('${url}', '${fileName}')" title="View Full Size">
                                    <i class="fas fa-expand"></i>
                                </button>
                                <button class="btn-icon" onclick="window.dashboardManager.downloadImage('${url}', '${fileName}')" title="Download">
                                    <i class="fas fa-download"></i>
                                </button>
                            </div>
                        </div>
                    </div>
                `;
            }
        }).join('');
    }

    toggleViewMode() {
        this.currentViewMode = this.currentViewMode === 'grid' ? 'list' : 'grid';
        
        // Update button
        const toggleBtn = document.getElementById('view-mode-btn');
        const icon = toggleBtn.querySelector('i');
        const text = document.getElementById('view-mode-text');
        
        if (this.currentViewMode === 'grid') {
            icon.className = 'fas fa-th';
            text.textContent = 'Grid View';
        } else {
            icon.className = 'fas fa-list';
            text.textContent = 'List View';
        }

        // Re-render images with new view
        if (this.currentScanForViewer) {
            this.renderImages(this.currentScanForViewer.photoUrls || []);
        }
    }

    openFullscreenImage(imageUrl, fileName) {
        // Create fullscreen modal
        const modal = document.createElement('div');
        modal.className = 'fullscreen-image-modal';
        modal.innerHTML = `
            <div class="fullscreen-image-container">
                <button class="fullscreen-close" onclick="window.dashboardManager.closeFullscreenImage()">
                    <i class="fas fa-times"></i>
                </button>
                <img src="${imageUrl}" alt="${fileName}" class="fullscreen-image">
            </div>
        `;
        
        document.body.appendChild(modal);
        
        // Add click to close
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                this.closeFullscreenImage();
            }
        });
    }

    closeFullscreenImage() {
        const modal = document.querySelector('.fullscreen-image-modal');
        if (modal) {
            modal.remove();
        }
    }

    async downloadImage(url, targetPath) {
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error(`Failed to download image: ${response.statusText}`);
            
            const blob = await response.blob();
            const formData = new FormData();
            formData.append('image', blob, path.basename(targetPath));
            formData.append('targetPath', targetPath);

            const uploadResponse = await fetch('/api/colmap/upload', {
                method: 'POST',
                body: formData
            });

            if (!uploadResponse.ok) {
                throw new Error(`Failed to upload image: ${uploadResponse.statusText}`);
            }

            return await uploadResponse.json();
        } catch (error) {
            console.error('Error downloading/uploading image:', error);
            throw error;
        }
    }

    closeImageViewer() {
        const modal = document.getElementById('image-viewer-modal');
        if (modal) {
            modal.classList.remove('active');
            setTimeout(() => {
                modal.style.display = 'none';
            }, 300);
        }
        this.currentScanForViewer = null;
    }

    // Edit scan functionality
    async editScan(scanId) {
        const modal = document.getElementById('edit-scan-modal');
        modal.style.display = 'flex';
        
        // Store scan ID in the form for later use
        document.getElementById('edit-scan-form').dataset.scanId = scanId;
        
        // Load scan data
        await this.loadScanData(scanId);
    }

    async loadScanData(scanId) {
        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            this.authManager.showToast('Scan not found.', 'error');
            return;
        }

        // Populate edit form
        document.getElementById('edit-scan-name').value = scan.title || scan.name || '';
        document.getElementById('edit-scan-location').value = scan.location || '';
        document.getElementById('edit-scan-description').value = scan.description || '';

        // Display GPS information if available
        this.displayGpsInfo(scan);
    }

    displayGpsInfo(scan) {
        const gpsSection = document.getElementById('gps-info-section');
        const gpsDisplay = document.getElementById('gps-info-display');
        
        // Get GPS data from the anchorGps object
        const gpsData = scan.anchorGps;
        if (gpsData) {
            const latitude = gpsData.latitude;
            const longitude = gpsData.longitude;
            const accuracy = gpsData.accuracy;
            const altitude = gpsData.altitude;
            
            if (latitude !== undefined && longitude !== undefined && 
                latitude !== null && longitude !== null &&
                typeof latitude === 'number' && typeof longitude === 'number') {
                
                let gpsHtml = `
                    <div class="gps-coordinate">
                        <strong>Coordinates:</strong> ${latitude.toFixed(6)}, ${longitude.toFixed(6)}
                    </div>
                `;
                
                if (accuracy !== undefined && accuracy !== null && typeof accuracy === 'number') {
                    gpsHtml += `
                        <div class="gps-accuracy">
                            <strong>Accuracy:</strong> ±${Math.round(accuracy)} meters
                        </div>
                    `;
                }
                
                if (altitude !== undefined && altitude !== null && typeof altitude === 'number') {
                    gpsHtml += `
                        <div class="gps-altitude">
                            <strong>Altitude:</strong> ${Math.round(altitude)} meters
                        </div>
                    `;
                }
                
                gpsDisplay.innerHTML = gpsHtml;
                gpsSection.style.display = 'block';
                return;
            }
        }
        
        // Hide GPS section if no valid coordinates found
        gpsSection.style.display = 'none';
    }

    closeEditModal() {
        const modal = document.getElementById('edit-scan-modal');
        modal.style.display = 'none';
        
        // Clear the stored scan ID
        const form = document.getElementById('edit-scan-form');
        if (form) {
            delete form.dataset.scanId;
        }
    }

    // Delete scan functionality  
    async deleteScan(scanId) {
        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            this.authManager.showToast('Scan not found.', 'error');
            return;
        }

        // Store scan ID for deletion
        document.getElementById('delete-confirmation-modal').dataset.scanId = scanId;

        // Show confirmation modal
        const modal = document.getElementById('delete-confirmation-modal');
        modal.style.display = 'flex';
        setTimeout(() => modal.classList.add('active'), 10);
    }

    closeDeleteModal() {
        const modal = document.getElementById('delete-confirmation-modal');
        if (modal) {
            modal.classList.remove('active');
            setTimeout(() => {
                modal.style.display = 'none';
            }, 300);
        }
    }

    // Save edited scan
    async saveEditedScan() {
        const form = document.getElementById('edit-scan-form');
        const scanId = form.dataset.scanId;
        
        if (!scanId) {
            this.authManager.showToast('Error: No scan selected for editing.', 'error');
            return;
        }

        const scanName = document.getElementById('edit-scan-name').value.trim();
        const scanLocation = document.getElementById('edit-scan-location').value.trim();
        const scanDescription = document.getElementById('edit-scan-description').value.trim();

        if (!scanName) {
            this.authManager.showToast('Please enter a scan name.', 'error');
            return;
        }

        this.authManager.showLoading(true);
        const { doc, updateDoc } = window.firebaseModules;

        try {
            const scanRef = doc(this.authManager.firestore, 'ar_scans', scanId);
            const updateData = {
                title: scanName,
                name: scanName // Keep both for backwards compatibility
            };

            if (scanLocation) {
                updateData.location = scanLocation;
            }

            if (scanDescription) {
                updateData.description = scanDescription;
            }

            await updateDoc(scanRef, updateData);

            // Update local scan data
            const scanIndex = this.scans.findIndex(s => s.id === scanId);
            if (scanIndex !== -1) {
                this.scans[scanIndex] = { ...this.scans[scanIndex], ...updateData };
                this.renderScans(this.scans);
            }

            this.authManager.showToast('Scan updated successfully!', 'success');
            this.closeEditModal();
        } catch (error) {
            console.error('Error updating scan:', error);
            this.authManager.showToast('Failed to update scan.', 'error');
        } finally {
            this.authManager.showLoading(false);
        }
    }

    // Confirm and execute scan deletion
    async confirmDelete() {
        const modal = document.getElementById('delete-confirmation-modal');
        const scanId = modal.dataset.scanId;
        
        if (!scanId) {
            this.authManager.showToast('Error: No scan selected for deletion.', 'error');
            return;
        }

        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            this.authManager.showToast('Scan not found.', 'error');
            return;
        }

        this.authManager.showLoading(true);
        const { doc, deleteDoc, storageRef, deleteObject } = window.firebaseModules;

        try {
            // First, delete all images from Firebase Storage
            if (scan.photoUrls && scan.photoUrls.length > 0) {
                const deletePromises = scan.photoUrls.map(async (url) => {
                    try {
                        // Extract the file path from the URL
                        const urlParts = url.split('/');
                        const pathIndex = urlParts.findIndex(part => part.includes('ar_scans'));
                        if (pathIndex !== -1) {
                            const filePath = urlParts.slice(pathIndex).join('/').split('?')[0];
                            const fileRef = storageRef(window.firebaseServices.storage, filePath);
                            await deleteObject(fileRef);
                        }
                    } catch (error) {
                        console.warn('Failed to delete image:', url, error);
                    }
                });
                
                await Promise.allSettled(deletePromises);
            }

            // Delete the scan document from Firestore
            const scanRef = doc(this.authManager.firestore, 'ar_scans', scanId);
            await deleteDoc(scanRef);

            // Remove from local scans array
            this.scans = this.scans.filter(s => s.id !== scanId);
            this.renderScans(this.scans);

            this.authManager.showToast('Scan deleted successfully!', 'success');
            this.closeDeleteModal();
        } catch (error) {
            console.error('Error deleting scan:', error);
            this.authManager.showToast('Failed to delete scan.', 'error');
        } finally {
            this.authManager.showLoading(false);
        }
    }

    // Train Gaussian Splat functionality
    async trainSplat(scanId) {
        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            this.authManager.showToast('Scan not found.', 'error');
            return;
        }

        // Check if scan has sufficient images
        if (!scan.photoUrls || scan.photoUrls.length < 3) {
            this.authManager.showToast('At least 3 images are required for training.', 'error');
            return;
        }

        // Check WebGPU support
        if (!navigator.gpu) {
            this.authManager.showToast('WebGPU is not supported in this browser. Please use Chrome 113+ with hardware acceleration enabled.', 'error');
            return;
        }

        try {
            // Prepare the scan data for Brush
            await this.prepareScanForBrush(scan);
            
            // Launch Brush viewer/trainer
            this.launchBrushTrainer(scan);
            
        } catch (error) {
            console.error('Failed to prepare scan for training:', error);
            this.authManager.showToast('Failed to prepare scan: ' + error.message, 'error');
        }
    }

    checkWebGPUSupport() {
        return 'gpu' in navigator;
    }

    // Prepare scan data for Brush training
    async prepareScanForBrush(scan) {
        console.log('Preparing scan for Brush:', scan.title);
        
        // Create a dataset format that Brush can understand
        const dataset = {
            name: scan.title || scan.name || 'Untitled Scan',
            images: scan.photoUrls || [],
            metadata: {
                created: scan.created?.toDate?.() || new Date(),
                location: scan.location || 'Unknown',
                gps: scan.anchorGps || null,
                description: scan.description || '',
                imageCount: (scan.photoUrls || []).length
            }
        };

        // Store the dataset in sessionStorage so Brush can access it
        sessionStorage.setItem('brushDataset', JSON.stringify(dataset));
        
        return dataset;
    }

    // Launch Brush trainer in a new window
    launchBrushTrainer(scan) {
        const scanName = scan.title || scan.name || 'Untitled Scan';
        this.authManager.showToast(`Launching Brush trainer for "${scanName}"...`, 'success');
        
        // Try to open Brush in a new window/tab
        // First, try to serve Brush from a local server
        const brushUrl = this.getBrushUrl();
        
        const brushWindow = window.open(
            brushUrl,
            'brush-trainer',
            'width=1200,height=800,scrollbars=yes,resizable=yes'
        );
        
        if (brushWindow) {
            console.log('Brush trainer launched successfully');
            
            // Optional: Listen for messages from Brush window
            window.addEventListener('message', (event) => {
                if (event.source === brushWindow) {
                    this.handleBrushMessage(event.data);
                }
            });
        } else {
            this.authManager.showToast('Failed to open Brush trainer. Check popup blocker settings.', 'error');
        }
    }

    // Get the URL for Brush trainer
    getBrushUrl() {
        // Use relative path so it works with RIT hosting structure
        // This will resolve to people.rit.edu/~username/brush-trainer.html
        return 'brush-trainer.html';
    }

    // Handle messages from Brush trainer window
    handleBrushMessage(message) {
        console.log('Message from Brush:', message);
        
        if (message.type === 'training-complete') {
            this.authManager.showToast('Training completed successfully!', 'success');
            // Could update the scan record with the trained model URL
        } else if (message.type === 'training-error') {
            this.authManager.showToast('Training failed: ' + message.error, 'error');
        }
    }

    async showTrainingModal(scanId) {
        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            this.authManager.showToast('Scan not found.', 'error');
            return;
        }

        const modal = document.getElementById('training-modal');
        if (!modal) {
            console.error('Training modal not found');
            return;
        }

        // Store current scan for training
        this.currentTrainingScan = scan;

        // Update modal content
        document.getElementById('training-scan-name').textContent = scan.title || scan.name || 'Untitled Scan';
        document.getElementById('training-image-count').textContent = `${scan.photoUrls.length} images`;
        
        // Reset progress
        document.getElementById('training-progress').style.width = '0%';
        document.getElementById('training-progress-text').textContent = 'Checking for existing COLMAP data...';
        document.getElementById('training-step').textContent = 'Initializing...';
        document.getElementById('training-loss').textContent = 'Loss: N/A';
        document.getElementById('training-time').textContent = 'Time: 0s';

        // Clear logs
        const logContainer = document.getElementById('training-logs');
        if (logContainer) {
            logContainer.innerHTML = '';
        }

        // Show modal first
        modal.style.display = 'flex';
        setTimeout(() => modal.classList.add('active'), 10);

        // Refresh scan data from Firestore to get latest COLMAP results
        this.addTrainingLog(`Checking for existing COLMAP results for user ${this.currentUser?.uid}...`);
        await this.refreshScanData(scan);
        const colmapResults = await this.checkExistingCOLMAPResults(scan);

        const convertBtn = document.getElementById('convert-btn');
        if (!convertBtn) return;

        if (colmapResults.found) {
            // COLMAP results exist - skip to training
            const sourceText = colmapResults.source === 'firestore' ? 'from previous session' : 'in storage';
            this.addTrainingLog(`Found existing COLMAP results ${sourceText} with ${colmapResults.itemCount} files`);
            document.getElementById('training-progress-text').textContent = 'COLMAP data found! Ready to train.';
            document.getElementById('training-step').textContent = 'Step 2: Gaussian Splat Training';
            document.getElementById('training-progress').style.width = '100%';
            
            // Store the existing COLMAP results in the scan object if not already there
            if (!scan.colmapResults) {
                scan.colmapResults = {
                    resultsPath: colmapResults.path,
                    files: colmapResults.files
                };
            }

            // Set up training button
            convertBtn.innerHTML = '<i class="fas fa-magic"></i> Train with Brush';
            convertBtn.className = 'btn btn-success';
            convertBtn.disabled = false;
            convertBtn.onclick = () => this.startBrushTraining(scan);
            
            this.addTrainingLog('Click "Train with Brush" to begin Gaussian Splat training');
        } else {
            // No COLMAP results - need to convert first
            let errorMsg;
            if (colmapResults.source === 'localhost_skip') {
                errorMsg = 'Running on localhost - storage check disabled. Please run conversion.';
            } else if (colmapResults.source === 'storage_error') {
                errorMsg = 'Storage check failed. Please run conversion.';
            } else {
                errorMsg = 'No existing COLMAP results found. Conversion required.';
            }
            this.addTrainingLog(errorMsg);
            document.getElementById('training-progress-text').textContent = 'Ready to convert images...';
            document.getElementById('training-step').textContent = 'Step 1: COLMAP Conversion';
            document.getElementById('training-progress').style.width = '0%';

            // Set up convert button
            convertBtn.innerHTML = '<i class="fas fa-cogs"></i> Convert with COLMAP';
            convertBtn.className = 'btn btn-primary';
            convertBtn.disabled = false;
            convertBtn.onclick = () => this.startCOLMAPConversion(scan.id);
            
            this.addTrainingLog('Click "Convert with COLMAP" to process images first');
        }
    }

    closeTrainingModal() {
        const modal = document.getElementById('training-modal');
        if (modal) {
            modal.classList.remove('active');
            setTimeout(() => {
                modal.style.display = 'none';
            }, 300);
        }
        
        // Clean up training resources
        if (this.brushTrainer) {
            this.brushTrainer.stopTraining();
            // Don't dispose completely as we might use it again
        }

        // Clear training log
        const logContent = document.getElementById('training-log-content');
        if (logContent) {
            logContent.innerHTML = `
                <div class="log-entry">
                    <span class="log-time">[00:00]</span>
                    <span class="log-message">Initializing training session...</span>
                </div>
            `;
        }
    }

    async startBrushTraining(scan) {
        const startTime = Date.now();
        this.updateTrainingProgress(0, 'Initializing Brush engine...', 'Step 1: Initialization');

        try {
            // Initialize BrushTrainer if not already done
            if (!this.brushTrainer) {
                this.brushTrainer = new window.BrushTrainer();
            }

            // Set up progress callback
            this.brushTrainer.setProgressCallback((progress) => {
                this.updateTrainingProgress(
                    progress.step / progress.total_steps * 100,
                    `Training step ${progress.step}/${progress.total_steps}`,
                    'Step 2: Gaussian Splatting',
                    progress.loss,
                    Math.floor((Date.now() - startTime) / 1000)
                );
                
                // Add detailed log entry
                this.addTrainingLog(`Step ${progress.step}: Loss ${progress.loss.toFixed(4)}, PSNR ${progress.psnr.toFixed(1)}dB`);
            });

            // Step 1: Initialize Brush
            await this.brushTrainer.initialize();
            this.updateTrainingProgress(10, 'Brush engine initialized', 'Step 2: Loading COLMAP dataset');

            // Step 2: Verify COLMAP files exist
            this.addTrainingLog('Verifying COLMAP files are accessible...');
            const fileCheck = await this.verifyColmapFiles(scan);
            if (!fileCheck.success) {
                this.addTrainingLog(`File verification failed: ${fileCheck.error}`, 'error');
                console.error('COLMAP file verification details:', {
                    userId: this.currentUser.uid,
                    scanId: scan.id,
                    colmapResults: scan.colmapResults,
                    error: fileCheck.error
                });
                throw new Error(`COLMAP files not accessible: ${fileCheck.error}`);
            }
            this.addTrainingLog('COLMAP files verified successfully');

            // Step 3: Load COLMAP dataset
            const storage = window.firebaseServices.storage;
            const storageUrl = 'https://firebasestorage.googleapis.com/v0/b/' + 
                              window.firebaseServices.app.options.storageBucket + '/o';

            await this.brushTrainer.loadColmapDataset(
                this.currentUser.uid,
                scan.id,
                storageUrl
            );
            this.updateTrainingProgress(30, 'COLMAP dataset loaded', 'Step 4: Starting training');

            // Step 4: Start training
            await this.brushTrainer.startTraining();
            this.updateTrainingProgress(40, 'Training started successfully', 'Step 4: Training in progress');

            // Step 4: Monitor training progress
            await this.monitorTrainingProgress(startTime);

        } catch (error) {
            this.addTrainingLog(`Training failed: ${error.message}`, 'error');
            throw error;
        }
    }

    async downloadImages(photoUrls) {
        // This method is now handled by BrushTrainer.prepareDataset
        // Keeping for backward compatibility
        return this.brushTrainer.prepareDataset(photoUrls);
    }

    async generateCameraPoses(imageData, scan) {
        // This method is now handled by BrushTrainer.generateCameraPoses
        // Keeping for backward compatibility
        return [];
    }

    /**
     * Verify that COLMAP files are accessible in Firebase Storage
     */
    async verifyColmapFiles(scan) {
        try {
            if (!scan.colmapResults) {
                return { success: false, error: 'No COLMAP results found in scan' };
            }

            const { getDownloadURL, ref } = window.firebaseModules;
            const storage = window.firebaseServices.storage;
            const userId = this.currentUser.uid;
            
            // Check for essential files (binary format)
            const requiredFiles = ['cameras.bin', 'images.bin', 'points3D.bin'];
            const missingFiles = [];
            
            for (const fileName of requiredFiles) {
                try {
                    const fileRef = ref(storage, `colmap-results/${userId}/${scan.id}/sparse/${fileName}`);
                    await getDownloadURL(fileRef);
                    console.log(`✓ ${fileName} is accessible`);
                } catch (error) {
                    console.warn(`✗ ${fileName} not accessible:`, error.message);
                    missingFiles.push(fileName);
                }
            }
            
            if (missingFiles.length > 0) {
                return { 
                    success: false, 
                    error: `Missing binary files: ${missingFiles.join(', ')}. Please run COLMAP processing again.` 
                };
            }
            
            return { success: true };
            
        } catch (error) {
            return { 
                success: false, 
                error: `File verification failed: ${error.message}` 
            };
        }
    }

    async initializeBrushTraining(imageData, cameraData) {
        // This method is now handled by BrushTrainer.initialize
        // Keeping for backward compatibility
    }

    async monitorTrainingProgress(startTime) {
        return new Promise((resolve) => {
            // The progress is now handled via the callback we set up in startBrushTraining
            // We just need to monitor if training is still running
            const monitorInterval = setInterval(() => {
                if (!this.brushTrainer || !this.brushTrainer.isTraining()) {
                    clearInterval(monitorInterval);
                    this.completeTraining(startTime);
                    resolve();
                    return;
                }
                
                // Training is still running, the progress callback will handle updates
            }, 1000); // Check every second
        });
    }

    async runTrainingLoop(startTime) {
        // This method is now replaced by monitorTrainingProgress
        // Keeping for backward compatibility
        await this.monitorTrainingProgress(startTime);
    }

    completeTraining(startTime) {
        const totalTime = Math.floor((Date.now() - startTime) / 1000);
        
        this.updateTrainingProgress(
            100,
            'Training completed successfully!',
            'Step 5: Finalizing',
            '0.0234',
            totalTime
        );

        // Show completion message
        setTimeout(() => {
            this.authManager.showToast('Gaussian Splat training completed successfully!', 'success');
            
            // Update UI to show download button
            const downloadBtn = document.getElementById('training-download-btn');
            if (downloadBtn) {
                downloadBtn.style.display = 'block';
                downloadBtn.onclick = () => this.downloadTrainedSplat();
            }
        }, 1000);
    }

    updateTrainingProgress(progress, message, step, loss = null, time = null) {
        document.getElementById('training-progress-bar').style.width = `${progress}%`;
        document.getElementById('training-progress-text').textContent = message;
        document.getElementById('training-step').textContent = step;
        
        if (loss !== null) {
            document.getElementById('training-loss').textContent = `Loss: ${loss}`;
        }
        
        if (time !== null) {
            document.getElementById('training-time').textContent = `Time: ${time}s`;
        }

        // Add log entry
        this.addTrainingLogEntry(message);
    }

    addTrainingLogEntry(message) {
        const logContent = document.getElementById('training-log-content');
        if (!logContent) return;

        const timestamp = new Date().toLocaleTimeString();
        const logEntry = document.createElement('div');
        logEntry.className = 'log-entry';
        logEntry.innerHTML = `
            <span class="log-time">[${timestamp}]</span>
            <span class="log-message">${message}</span>
        `;
        
        logContent.appendChild(logEntry);
        logContent.scrollTop = logContent.scrollHeight;
    }

    async downloadTrainedSplat() {
        try {
            if (!this.brushTrainer) {
                throw new Error('No training session available');
            }

            this.addTrainingLog('Exporting trained Gaussian splat...');
            
            // Export the trained splat using BrushTrainer (now returns Uint8Array from WASM)
            const splatData = await this.brushTrainer.exportSplat();
            
            const blob = new Blob([splatData], { type: 'application/octet-stream' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${this.currentTrainingScan?.title || 'scan'}_trained.ply`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            
            this.addTrainingLog('Trained splat downloaded successfully!', 'success');
            this.authManager.showToast('Gaussian Splat downloaded successfully!', 'success');

            // Upload results to Firebase Storage
            if (this.currentUser && this.currentTrainingScan) {
                try {
                    this.addTrainingLog('Uploading results to Firebase Storage...');
                    const storage = window.firebaseServices.storage;
                    const storageUrl = 'https://firebasestorage.googleapis.com/v0/b/' + 
                                      window.firebaseServices.app.options.storageBucket + '/o';
                    
                    await this.brushTrainer.uploadResults(
                        this.currentUser.uid,
                        this.currentTrainingScan.id,
                        storageUrl
                    );
                    
                    this.addTrainingLog('Results uploaded to Firebase Storage successfully!', 'success');
                } catch (uploadError) {
                    console.warn('Failed to upload results to Firebase:', uploadError);
                    this.addTrainingLog('Warning: Failed to upload results to Firebase Storage', 'warning');
                }
            }
        } catch (error) {
            console.error('Failed to download splat:', error);
            this.addTrainingLog(`Failed to export trained splat: ${error.message}`, 'error');
            this.authManager.showToast('Failed to download splat: ' + error.message, 'error');
        }
    }

    async downloadScanImages(scanId) {
        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            this.authManager.showToast('Scan not found', 'error');
            return;
        }

        if (!scan.photoUrls || scan.photoUrls.length === 0) {
            this.authManager.showToast('No images found in this scan', 'error');
            return;
        }

        try {
            this.authManager.showLoading(true, 'Preparing download...');
            
            // Create a zip file
            const zip = new JSZip();
            const { storageRef, getDownloadURL } = window.firebaseModules;
            const storage = window.firebaseServices.storage;
            
            // Download each image and add to zip
            const downloadPromises = scan.photoUrls.map(async (photoUrl, index) => {
                try {
                    // If it's already a full URL, use it directly, otherwise get download URL from storage
                    let imageUrl = photoUrl;
                    if (!photoUrl.startsWith('http')) {
                        const imageRef = storageRef(storage, photoUrl);
                        imageUrl = await getDownloadURL(imageRef);
                    }
                    
                    const response = await fetch(imageUrl);
                    const blob = await response.blob();
                    const fileName = `image_${String(index + 1).padStart(3, '0')}.jpg`;
                    zip.file(fileName, blob);
                } catch (error) {
                    console.error(`Error downloading image ${index}:`, error);
                    // Continue with other images even if one fails
                }
            });

            await Promise.all(downloadPromises);
            
            // Generate and download the zip file
            const content = await zip.generateAsync({type: 'blob'});
            const downloadUrl = URL.createObjectURL(content);
            const link = document.createElement('a');
            link.href = downloadUrl;
            const scanName = scan.name || scan.title || 'scan';
            link.download = `${scanName.replace(/[^a-z0-9]/gi, '_')}_images.zip`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(downloadUrl);
            
            this.authManager.showToast('Download completed successfully', 'success');
        } catch (error) {
            console.error('Error downloading images:', error);
            this.authManager.showToast('Failed to download images', 'error');
        } finally {
            this.authManager.showLoading(false);
        }
    }

    async startCOLMAPConversion(scanId) {
        try {
            const scan = this.scans.find(s => s.id === scanId);
            if (!scan) {
                throw new Error('Scan not found');
            }

            // Update UI to show conversion in progress
            this.updateTrainingStep('Converting images with COLMAP...');
            this.updateTrainingProgress(0);
            this.addTrainingLog('Starting COLMAP conversion...');

            // Update button to show processing
            const convertBtn = document.getElementById('convert-btn');
            if (convertBtn) {
                convertBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Converting...';
                convertBtn.className = 'btn btn-warning';
                convertBtn.disabled = true;
            }

            // Validate user authentication
            if (!this.currentUser?.uid) {
                throw new Error('User not authenticated - cannot process COLMAP');
            }

            // Cloud Run COLMAP service URL (configured via getColmapServiceUrl)
            const COLMAP_SERVICE_URL = this.getColmapServiceUrl();
            
            this.updateTrainingProgress(10);
            this.addTrainingLog(`Sending images to COLMAP Cloud Run service for user ${this.currentUser.uid}...`);
            
            // Call the Cloud Run service (with 60-minute timeout capability)
            let serviceResult = null;
            const servicePayload = {
                scanId: scan.id,
                imageUrls: scan.photoUrls,
                userId: this.currentUser?.uid
            };
            
            console.log('Calling COLMAP Cloud Run service with payload:', servicePayload);
            
            try {
                // Get Firebase Auth token for authentication
                const idToken = await this.currentUser.getIdToken();
                
                // Call Cloud Run service with proper authentication
                const response = await fetch(`${COLMAP_SERVICE_URL}/process`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${idToken}`
                    },
                    body: JSON.stringify(servicePayload)
                });
                
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }
                
                serviceResult = await response.json();
                console.log('COLMAP Cloud Run service result:', serviceResult);
                this.updateTrainingProgress(50);
                this.addTrainingLog('COLMAP processing completed successfully');
            } catch (serviceError) {
                console.error('Cloud Run service error details:', {
                    message: serviceError.message,
                    stack: serviceError.stack
                });
                
                if (serviceError.name === 'TypeError' && serviceError.message.includes('fetch')) {
                    console.log('Cloud Run service request failed, checking for results...');
                    this.updateTrainingProgress(50);
                    this.addTrainingLog('COLMAP service request failed, checking for results...');
                } else {
                    this.addTrainingLog(`Cloud Run service error: ${serviceError.message}`);
                    throw serviceError; // Re-throw if it's not a network error
                }
            }

            // Always check Firebase Storage for results (even if function timed out)
            this.updateTrainingProgress(70);
            this.addTrainingLog(`Verifying COLMAP results in user storage (${this.currentUser.uid})...`);
            
            // Wait a bit longer for processing to complete if service request failed
            if (!serviceResult) {
                this.addTrainingLog('Waiting for COLMAP processing to complete...');
                await new Promise(resolve => setTimeout(resolve, 3000)); // Wait 3 seconds
            }
            
            // Check for results with retries (try new path first, then old path for backward compatibility)
            let colmapResults = await this.checkCOLMAPResults(scan.id);
            
            // If not found in new user-specific path, try old path structure for debugging
            if (!colmapResults.found) {
                console.log('Not found in new path, checking old path structure for debugging...');
                colmapResults = await this.checkCOLMAPResultsOldPath(scan.id);
                if (colmapResults.found) {
                    this.addTrainingLog('⚠️ Found results in old path structure - Cloud Function needs updating');
                }
            }
            
            // If not found immediately, try a few more times with delays
            if (!colmapResults.found) {
                this.addTrainingLog('Results not ready yet, retrying in 5 seconds...');
                this.updateTrainingProgress(75);
                await new Promise(resolve => setTimeout(resolve, 5000)); // Wait 5 seconds
                
                colmapResults = await this.checkCOLMAPResults(scan.id);
                
                if (!colmapResults.found) {
                    this.addTrainingLog('Still processing, checking again in 10 seconds...');
                    this.updateTrainingProgress(80);
                    await new Promise(resolve => setTimeout(resolve, 10000)); // Wait 10 more seconds
                    colmapResults = await this.checkCOLMAPResults(scan.id);
                }
            }

            if (colmapResults.found) {
                this.addTrainingLog('COLMAP results verified in Firebase Storage');
                this.updateTrainingStep('Conversion complete! Ready for training');
                this.updateTrainingProgress(100);
                
                // Store the COLMAP results in the scan object
                scan.colmapResults = {
                    resultsPath: colmapResults.path,
                    files: colmapResults.files,
                    sparseDir: functionResult?.data?.sparseDir,
                    imagesDir: functionResult?.data?.imagesDir,
                    databasePath: functionResult?.data?.databasePath
                };

                // Update the scan in Firebase
                await this.updateScan(scan);

                // Wait a moment for user to see completion, then update button
                setTimeout(() => {
                    if (convertBtn) {
                        convertBtn.innerHTML = '<i class="fas fa-magic"></i> Train with Brush';
                        convertBtn.className = 'btn btn-success';
                        convertBtn.disabled = false;
                        convertBtn.onclick = () => this.startBrushTraining(scan);
                    }
                    
                    this.updateTrainingStep('Ready to start Gaussian Splat training');
                    this.addTrainingLog('Click "Train with Brush" to begin Gaussian Splat training');
                }, 1500);

            } else {
                throw new Error('COLMAP results not found in Firebase Storage');
            }
        } catch (error) {
            console.error('COLMAP conversion error:', error);
            this.addTrainingLog(`Error: ${error.message}`, 'error');
            this.updateTrainingStep('Conversion failed');
            this.updateTrainingProgress(0);
            
            // Reset the convert button
            const convertBtn = document.getElementById('convert-btn');
            if (convertBtn) {
                convertBtn.innerHTML = '<i class="fas fa-cogs"></i> Convert with COLMAP';
                convertBtn.className = 'btn btn-primary';
                convertBtn.disabled = false;
                convertBtn.onclick = () => this.startCOLMAPConversion(scan.id);
            }
            
            this.authManager.showToast('Failed to convert scan with COLMAP. Please try again.', 'error');
        }
    }

    updateTrainingStep(step) {
        const stepElement = document.getElementById('training-step');
        if (stepElement) {
            stepElement.textContent = step;
        }
    }

    updateTrainingProgress(progress) {
        const progressBar = document.getElementById('training-progress');
        if (progressBar) {
            progressBar.style.width = `${progress}%`;
            progressBar.setAttribute('aria-valuenow', progress);
        }
    }

    addTrainingLog(message, type = 'info') {
        const logContainer = document.getElementById('training-logs');
        if (logContainer) {
            const logEntry = document.createElement('div');
            logEntry.className = `log-entry ${type}`;
            logEntry.textContent = message;
            logContainer.appendChild(logEntry);
            logContainer.scrollTop = logContainer.scrollHeight;
        }
    }

    updateLoadingState(isLoading) {
        const loadingOverlay = document.getElementById('loading-overlay');
        if (loadingOverlay) {
            loadingOverlay.style.display = isLoading ? 'flex' : 'none';
        }
    }

    showError(message) {
        const errorToast = document.getElementById('error-toast');
        if (errorToast) {
            errorToast.textContent = message;
            errorToast.style.display = 'block';
            setTimeout(() => {
                errorToast.style.display = 'none';
            }, 5000);
        }
    }

    // Check for existing COLMAP results (Firestore first, then Storage if not localhost)
    async checkExistingCOLMAPResults(scan) {
        try {
            // First, check if the scan document already has COLMAP results stored
            if (scan.colmapResults && scan.colmapResults.resultsPath) {
                console.log('Found COLMAP results in scan document:', scan.colmapResults);
                return {
                    found: true,
                    path: scan.colmapResults.resultsPath,
                    files: scan.colmapResults.files || [],
                    itemCount: scan.colmapResults.files ? scan.colmapResults.files.length : 0,
                    source: 'firestore'
                };
            }

            // Try to check Firebase Storage for COLMAP results
            try {
                const storageResult = await this.checkCOLMAPResults(scan.id);
                if (storageResult.found) {
                    console.log('Found COLMAP results in Firebase Storage:', storageResult);
                    return storageResult;
                }
                
                // If not found in new path, try old path for backward compatibility
                const oldPathResult = await this.checkCOLMAPResultsOldPath(scan.id);
                if (oldPathResult.found) {
                    console.log('Found COLMAP results in old path structure:', oldPathResult);
                    return oldPathResult;
                }
                
                // Not found in either location
                return storageResult; // Return the new path result (which will have found: false)
            } catch (storageError) {
                console.log('Storage check failed:', storageError);
                return {
                    found: false,
                    error: 'Storage check failed - please run conversion',
                    source: 'storage_error'
                };
            }
        } catch (error) {
            console.error('Error checking existing COLMAP results:', error);
            return {
                found: false,
                error: error.message
            };
        }
    }

    // Check Firebase Storage for COLMAP results (old path structure for debugging)
    async checkCOLMAPResultsOldPath(scanId) {
        try {
            const { storageRef, listAll } = window.firebaseModules;
            const storage = window.firebaseServices.storage;
            
            // Check old path structure: colmap-results/{scanId}/
            const colmapResultsRef = storageRef(storage, `colmap-results/${scanId}/`);
            
            try {
                const listResult = await listAll(colmapResultsRef);
                
                if (listResult.items.length > 0) {
                    console.log(`COLMAP results found in OLD path structure:`, listResult.items.length, 'files');
                    
                    const files = listResult.items.map(item => item.name);
                    
                    return {
                        found: true,
                        path: `colmap-results/${scanId}/`,
                        files: files,
                        itemCount: listResult.items.length,
                        source: 'storage_old_path'
                    };
                } else {
                    return {
                        found: false,
                        path: `colmap-results/${scanId}/`,
                        error: 'Results folder is empty'
                    };
                }
            } catch (listError) {
                return {
                    found: false,
                    path: `colmap-results/${scanId}/`,
                    error: 'Results folder not found'
                };
            }
        } catch (error) {
            console.error('Error checking COLMAP results in old path:', error);
            return {
                found: false,
                error: error.message
            };
        }
    }

    // Check Firebase Storage for COLMAP results
    async checkCOLMAPResults(scanId) {
        try {
            const { storageRef, listAll } = window.firebaseModules;
            const storage = window.firebaseServices.storage;
            
            // Get current user ID
            const userId = this.currentUser?.uid;
            if (!userId) {
                throw new Error('User not authenticated');
            }
            
            // Check for colmap-results/{userId}/{scanId}/ folder
            const colmapResultsRef = storageRef(storage, `colmap-results/${userId}/${scanId}/`);
            
            try {
                const listResult = await listAll(colmapResultsRef);
                
                if (listResult.items.length > 0) {
                    console.log(`COLMAP results found in Firebase Storage for user ${userId}:`, listResult.items.length, 'files');
                    
                    // Get the file names for reference
                    const files = listResult.items.map(item => item.name);
                    console.log('Files found in storage:', files);
                    
                    // Check file sizes for debugging
                    const fileSizes = await Promise.all(listResult.items.map(async (item) => {
                        try {
                            const metadata = await item.getMetadata();
                            return { name: item.name, size: metadata[0].size };
                        } catch (error) {
                            return { name: item.name, size: 'unknown' };
                        }
                    }));
                    console.log('File sizes:', fileSizes);
                    
                    return {
                        found: true,
                        path: `colmap-results/${userId}/${scanId}/`,
                        files: files,
                        itemCount: listResult.items.length,
                        source: 'storage'
                    };
                } else {
                    console.log('COLMAP results folder exists but is empty');
                    return {
                        found: false,
                        path: `colmap-results/${userId}/${scanId}/`,
                        error: 'Results folder is empty'
                    };
                }
            } catch (listError) {
                console.log('COLMAP results folder does not exist or is not accessible:', listError.message);
                return {
                    found: false,
                    path: `colmap-results/${userId}/${scanId}/`,
                    error: 'Results folder not found'
                };
            }
        } catch (error) {
            console.error('Error checking COLMAP results in Firebase Storage:', error);
            return {
                found: false,
                error: error.message
            };
        }
    }

    // Update scan document in Firestore
    async updateScan(scan) {
        if (!scan.id) {
            console.error('Cannot update scan: no ID provided');
            return;
        }

        try {
            const { doc, updateDoc } = window.firebaseModules;
            const scanRef = doc(this.firestore, 'ar_scans', scan.id);
            
            // Update with new COLMAP results
            await updateDoc(scanRef, {
                colmapResults: scan.colmapResults,
                updatedAt: new Date()
            });
            
            console.log('Scan updated successfully with COLMAP results');
        } catch (error) {
            console.error('Error updating scan:', error);
        }
    }

    // Refresh scan data from Firestore to get latest COLMAP results
    async refreshScanData(scan) {
        if (!scan.id) {
            return;
        }

        try {
            const { doc, getDoc } = window.firebaseModules;
            const scanRef = doc(this.firestore, 'ar_scans', scan.id);
            const scanDoc = await getDoc(scanRef);
            
            if (scanDoc.exists()) {
                const latestData = scanDoc.data();
                // Update the scan object with latest COLMAP results if they exist
                if (latestData.colmapResults) {
                    scan.colmapResults = latestData.colmapResults;
                    console.log('Refreshed scan with latest COLMAP results from Firestore');
                }
            }
        } catch (error) {
            console.error('Error refreshing scan data:', error);
        }
    }
}

// Global functions for HTML onclick handlers
window.toggleViewMode = function() {
    if (window.dashboardManager) {
        window.dashboardManager.toggleViewMode();
    }
};

window.closeImageViewer = function() {
    if (window.dashboardManager) {
        window.dashboardManager.closeImageViewer();
    }
};

window.closeEditModal = function() {
    if (window.dashboardManager) {
        window.dashboardManager.closeEditModal();
    }
};

window.closeDeleteModal = function() {
    if (window.dashboardManager) {
        window.dashboardManager.closeDeleteModal();
    }
};

window.saveEditedScan = async function() {
    if (window.dashboardManager) {
        await window.dashboardManager.saveEditedScan();
    }
};

window.confirmDelete = async function() {
    if (window.dashboardManager) {
        await window.dashboardManager.confirmDelete();
    }
};

window.closeTrainingModal = function() {
    if (window.dashboardManager) {
        window.dashboardManager.closeTrainingModal();
    }
};

// Wait for AuthManager to be ready before initializing the DashboardManager
window.addEventListener('authReady', () => {
    if (window.authManager && window.authManager.currentUser) {
        // Pass the authManager instance to the dashboardManager
        window.dashboardManager = new DashboardManager(window.authManager);
    }
});

// Add training-related functions to DashboardManager prototype
DashboardManager.prototype.updateTrainingStep = function(step) {
    const stepElement = document.getElementById('training-step');
    if (stepElement) {
        stepElement.textContent = step;
    }
};

DashboardManager.prototype.updateTrainingProgress = function(progress) {
    const progressBar = document.getElementById('training-progress');
    if (progressBar) {
        progressBar.style.width = `${progress}%`;
        progressBar.setAttribute('aria-valuenow', progress);
    }
};

DashboardManager.prototype.addTrainingLog = function(message, type = 'info') {
    const logContainer = document.getElementById('training-logs');
    if (logContainer) {
        const logEntry = document.createElement('div');
        logEntry.className = `log-entry ${type}`;
        logEntry.textContent = message;
        logContainer.appendChild(logEntry);
        logContainer.scrollTop = logContainer.scrollHeight;
    }
};

DashboardManager.prototype.startCOLMAPConversion = async function(scanId) {
    try {
        const scan = this.scans.find(s => s.id === scanId);
        if (!scan) {
            throw new Error('Scan not found');
        }

        // Update UI
        this.updateTrainingStep('Converting images with COLMAP...');
        this.updateTrainingProgress(0);
        this.addTrainingLog('Starting COLMAP conversion...');

        // Use Firebase Functions with streaming for real-time progress updates
        const { httpsCallable } = window.firebaseModules;
        // Cloud Run COLMAP service URL (configured via getColmapServiceUrl)
        const COLMAP_SERVICE_URL = this.getColmapServiceUrl();
        
        // Log the service call details
        console.log('Calling COLMAP Cloud Run service with:', {
            scanId: scan.id,
            imageUrls: scan.photoUrls
        });
        
        // Call the Cloud Run service
        this.addTrainingLog('Processing images in the cloud...');
        
        // Get Firebase Auth token for authentication
        const idToken = await this.currentUser.getIdToken();
        
        // Call Cloud Run service with proper authentication
        const response = await fetch(`${COLMAP_SERVICE_URL}/process`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${idToken}`
            },
            body: JSON.stringify({
                scanId: scan.id,
                imageUrls: scan.photoUrls,
                userId: this.currentUser.uid
            })
        });

        if (!response.ok) {
            throw new Error(`Cloud Run service error: ${response.status} ${response.statusText}`);
        }

        const result = await response.json();
        console.log('COLMAP Cloud Run service result:', result);

        if (result.success) {
            this.addTrainingLog('COLMAP conversion completed successfully');
            this.updateTrainingStep('Conversion complete!');
            this.updateTrainingProgress(100);
            
            // Store the COLMAP results in the scan object
            scan.colmapResults = {
                sparseDir: result.results.sparseDir,
                imagesDir: result.results.imagesDir,
                databasePath: result.results.databasePath,
                uploadedFiles: result.results.uploadedFiles || []
            };

            // Validate that critical files were uploaded
            if (!result.results.uploadedFiles || result.results.uploadedFiles.length === 0) {
                throw new Error('No files were uploaded from COLMAP processing');
            }
            
            // Check for essential sparse files (binary format)
            const hasCamera = result.results.uploadedFiles.some(f => f.includes('cameras.bin'));
            const hasImages = result.results.uploadedFiles.some(f => f.includes('images.bin'));
            const hasPoints = result.results.uploadedFiles.some(f => f.includes('points3D.bin'));
            
            if (!hasCamera || !hasImages || !hasPoints) {
                throw new Error('Missing critical COLMAP files (cameras.bin, images.bin, or points3D.bin)');
            }
            
            this.addTrainingLog(`Successfully uploaded ${result.results.uploadedFiles.length} files`);

            // Update the scan in Firebase
            await this.updateScan(scan);
        } else {
            throw new Error(result.error || 'COLMAP conversion failed');
        }
    } catch (error) {
        console.error('COLMAP conversion error:', error);
        this.addTrainingLog(`Error: ${error.message}`, 'error');
        this.updateTrainingStep('Conversion failed');
        this.authManager.showToast('Failed to convert scan with COLMAP. Please try again.', 'error');
    }
};

 