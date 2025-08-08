// Main App Controller
class App {
    constructor() {
        this.initPromise = this.deferredInit();
    }

    async deferredInit() {
        await this.waitForFirebase();
        this.init();
    }

    async waitForFirebase() {
        return new Promise((resolve) => {
            if (window.firebaseServices) {
                resolve();
            } else {
                window.addEventListener('firebaseReady', resolve, { once: true });
            }
        });
    }

    init() {
        this.setupEventListeners();
        this.checkFirebaseConnection();
    }

    setupEventListeners() {
        // Navigation menu toggle for mobile
        this.setupMobileMenu();
        
        // Upload button
        const uploadBtn = document.getElementById('upload-btn');
        if (uploadBtn) {
            uploadBtn.addEventListener('click', () => {
                this.showUploadModal();
            });
        }

        // Service worker registration for PWA support
        this.registerServiceWorker();
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

    checkFirebaseConnection() {
        // Check if Firebase is properly initialized
        if (!window.firebaseServices) {
            console.error('Firebase SDK not loaded properly');
            this.showErrorMessage('Firebase connection failed. Please refresh the page.');
            return;
        }

        // Test Firestore connection
        if (window.firebaseServices?.firestore) {
            console.log('Firebase services initialized successfully');
        } else {
            console.warn('Firebase services not properly initialized');
        }
    }

    showUploadModal() {
        // TODO: Implement upload modal
        console.log('Upload modal would open here');
        
        // For now, show a helpful message
        if (window.authManager) {
            window.authManager.showToast(
                'Upload functionality coming soon! For now, use the mobile app to capture scans.',
                'info'
            );
        }
    }

    showErrorMessage(message) {
        const errorBanner = document.createElement('div');
        errorBanner.className = 'error-banner';
        errorBanner.innerHTML = `
            <div class="error-content">
                <i class="fas fa-exclamation-triangle"></i>
                <span>${message}</span>
                <button onclick="this.parentElement.parentElement.remove()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        `;
        
        document.body.insertBefore(errorBanner, document.body.firstChild);
        
        // Auto-remove after 10 seconds
        setTimeout(() => {
            if (errorBanner.parentNode) {
                errorBanner.parentNode.removeChild(errorBanner);
            }
        }, 10000);
    }

    async registerServiceWorker() {
        // Disabled temporarily due to Python HTTP server MIME type issues
        // Enable this when deploying to production with proper server
        if ('serviceWorker' in navigator && location.hostname !== 'localhost') {
            try {
                const registration = await navigator.serviceWorker.register('/sw.js');
                console.log('ServiceWorker registration successful:', registration);
            } catch (error) {
                console.log('ServiceWorker registration failed:', error);
            }
        } else {
            console.log('ServiceWorker registration skipped (development mode)');
        }
    }

    // Utility functions
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    formatDistance(meters) {
        if (meters < 1000) {
            return `${Math.round(meters)}m`;
        } else {
            return `${(meters / 1000).toFixed(1)}km`;
        }
    }

    copyToClipboard(text) {
        if (navigator.clipboard) {
            return navigator.clipboard.writeText(text);
        } else {
            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);
            return Promise.resolve();
        }
    }

    async shareUrl(url, title = 'Check out this 3D scan') {
        if (navigator.share) {
            try {
                await navigator.share({
                    title: title,
                    url: url
                });
            } catch (error) {
                console.log('Error sharing:', error);
                // Fallback to clipboard
                this.copyToClipboard(url);
                if (window.authManager) {
                    window.authManager.showToast('Link copied to clipboard!', 'success');
                }
            }
        } else {
            // Fallback to clipboard
            this.copyToClipboard(url);
            if (window.authManager) {
                window.authManager.showToast('Link copied to clipboard!', 'success');
            }
        }
    }

    getLocationName(lat, lng) {
        // TODO: Implement reverse geocoding
        // For now, return coordinates
        return `${lat.toFixed(4)}, ${lng.toFixed(4)}`;
    }

    calculateDistance(lat1, lng1, lat2, lng2) {
        const R = 6371; // Earth's radius in kilometers
        const dLat = this.degToRad(lat2 - lat1);
        const dLng = this.degToRad(lng2 - lng1);
        
        const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(this.degToRad(lat1)) * Math.cos(this.degToRad(lat2)) *
                  Math.sin(dLng / 2) * Math.sin(dLng / 2);
        
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        const distance = R * c * 1000; // Convert to meters
        
        return distance;
    }

    degToRad(deg) {
        return deg * (Math.PI / 180);
    }

    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    throttle(func, limit) {
        let inThrottle;
        return function() {
            const args = arguments;
            const context = this;
            if (!inThrottle) {
                func.apply(context, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    }
}

// Initialize the app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM Content Loaded');
    window.app = new App();
    
    // This is handled by auth.js now
    // if (window.firebaseServices) {
    //     console.log('Firebase services already available, initializing AuthManager');
    //     window.authManager = new AuthManager();
    // } else {
    //     console.log('Waiting for Firebase services to be ready...');
    //     // Wait for Firebase to be ready
    //     window.addEventListener('firebaseReady', () => {
    //         console.log('Firebase services ready, initializing AuthManager');
    //         window.authManager = new AuthManager();
    //     });
    // }
});

// Global error handler
window.addEventListener('error', (event) => {
    console.error('Global error:', event.error);
    
    // Don't show error messages for known issues
    const ignoredErrors = [
        'Script error',
        'Network request failed',
        'Loading chunk'
    ];
    
    if (ignoredErrors.some(ignored => event.message.includes(ignored))) {
        return;
    }
    
    if (window.authManager) {
        window.authManager.showToast('An unexpected error occurred', 'error');
    }
});

// Handle unhandled promise rejections
window.addEventListener('unhandledrejection', (event) => {
    console.error('Unhandled promise rejection:', event.reason);
    
    // Prevent the default behavior (logging to console)
    event.preventDefault();
    
    if (window.authManager) {
        window.authManager.showToast('An error occurred. Please try again.', 'error');
    }
}); 