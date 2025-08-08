/**
 * Brush Integration Module
 * Handles the integration with the Brush WebAssembly module for training Gaussian Splats
 * Based on: https://github.com/ArthurBrussee/brush
 */

class BrushTrainer {
    constructor() {
        this.isInitialized = false;
        this.wasmModule = null;
        this.wasmTrainer = null;
        this.canvas = null;
        this.progressCallback = null;
        this.supportedFormats = ['jpg', 'jpeg', 'png'];
    }

    /**
     * Initialize the Brush WebAssembly module
     */
    async initialize() {
        if (this.isInitialized) {
            return true;
        }

        try {
            // Check WebGPU support
            if (!navigator.gpu) {
                throw new Error('WebGPU is not supported in this browser. Please use Chrome 113+ or update your browser.');
            }

            const adapter = await navigator.gpu.requestAdapter();
            if (!adapter) {
                throw new Error('Failed to get WebGPU adapter. Make sure hardware acceleration is enabled.');
            }

            const device = await adapter.requestDevice();
            if (!device) {
                throw new Error('Failed to get WebGPU device.');
            }

            console.log('WebGPU initialized successfully');
            
            // Import and initialize the WebAssembly module
            const { default: init, BrushTrainer: WasmBrushTrainer, get_version, test_brush_integration } = await import('../brush-wasm-pkg/brush_wasm.js');
            
            // Initialize the WASM module
            await init();
            
            console.log('✅ Brush WASM module loaded successfully');
            console.log('Version:', get_version());
            console.log('Test:', test_brush_integration());
            
            // Create a hidden canvas for the WASM trainer
            this.canvas = document.createElement('canvas');
            this.canvas.width = 800;
            this.canvas.height = 600;
            this.canvas.style.display = 'none';
            document.body.appendChild(this.canvas);
            
            // Initialize the WASM trainer
            this.wasmTrainer = new WasmBrushTrainer(this.canvas);
            
            // Set up progress callback
            this.wasmTrainer.set_progress_callback((progress) => {
                if (this.progressCallback) {
                    this.progressCallback(progress);
                }
            });
            
            this.isInitialized = true;
            return true;
        } catch (error) {
            console.error('Failed to initialize Brush:', error);
            throw error;
        }
    }

    /**
     * Set progress callback for training updates
     */
    setProgressCallback(callback) {
        this.progressCallback = callback;
    }

    /**
     * Check if the browser supports all required features
     */
    static checkCompatibility() {
        const requirements = {
            webgpu: 'gpu' in navigator,
            webgl2: !!document.createElement('canvas').getContext('webgl2'),
            wasm: 'WebAssembly' in window,
            worker: 'Worker' in window,
            imagebitmap: 'createImageBitmap' in window
        };

        const missing = Object.entries(requirements)
            .filter(([key, supported]) => !supported)
            .map(([key]) => key);

        return {
            supported: missing.length === 0,
            missing: missing,
            requirements: requirements
        };
    }

    /**
     * Load COLMAP dataset for training
     */
    async loadColmapDataset(userId, scanId, firebaseStorageUrl) {
        if (!this.isInitialized) {
            await this.initialize();
        }

        if (!this.wasmTrainer) {
            throw new Error('WASM trainer not initialized');
        }

        console.log('Loading COLMAP dataset for:', { userId, scanId });
        
        try {
            // Get Firebase Auth token
            const authToken = await this.getFirebaseAuthToken();
            
            // Try loading with retry logic
            let lastError = null;
            for (let attempt = 1; attempt <= 3; attempt++) {
                try {
                    console.log(`Loading COLMAP dataset attempt ${attempt}/3`);
                    await this.wasmTrainer.load_colmap_dataset(userId, scanId, firebaseStorageUrl, authToken);
                    console.log('COLMAP dataset loaded successfully');
                    return;
                } catch (error) {
                    lastError = error;
                    console.warn(`Attempt ${attempt} failed:`, error.message);
                    
                    if (attempt < 3) {
                        // Wait before retry (exponential backoff)
                        const delay = Math.pow(2, attempt) * 1000;
                        console.log(`Retrying in ${delay}ms...`);
                        await new Promise(resolve => setTimeout(resolve, delay));
                    }
                }
            }
            
            // If all attempts failed, throw detailed error
            throw new Error(`Failed to load COLMAP dataset after 3 attempts. Last error: ${lastError.message}`);
        } catch (error) {
            console.error('Failed to load COLMAP dataset:', error);
            throw error;
        }
    }

    /**
     * Start training session
     */
    async startTraining() {
        if (!this.isInitialized) {
            await this.initialize();
        }

        if (!this.wasmTrainer) {
            throw new Error('WASM trainer not initialized');
        }

        console.log('Starting Gaussian Splat training...');
        
        try {
            await this.wasmTrainer.start_training();
            console.log('Training started successfully');
        } catch (error) {
            console.error('Failed to start training:', error);
            throw error;
        }
    }

    /**
     * Stop training session
     */
    stopTraining() {
        if (this.wasmTrainer) {
            this.wasmTrainer.stop_training();
            console.log('Training stopped');
        }
    }

    /**
     * Check if training is currently running
     */
    isTraining() {
        if (!this.wasmTrainer) {
            return false;
        }
        return this.wasmTrainer.is_training();
    }

    /**
     * Export trained Gaussian splat as PLY file
     */
    async exportSplat() {
        if (!this.wasmTrainer) {
            throw new Error('WASM trainer not initialized');
        }

        try {
            const splatData = await this.wasmTrainer.export_splats();
            console.log('Splat exported successfully');
            return splatData;
        } catch (error) {
            console.error('Failed to export splat:', error);
            throw error;
        }
    }

    /**
     * Upload training results to Firebase Storage
     */
    async uploadResults(userId, scanId, firebaseStorageUrl) {
        if (!this.wasmTrainer) {
            throw new Error('WASM trainer not initialized');
        }

        try {
            console.log('Exporting splat data for Firebase upload...');
            
            // Export the PLY data from WASM
            const plyData = await this.wasmTrainer.export_splats();
            
            // Upload using Firebase SDK to avoid CORS issues
            const { getStorage, ref, uploadBytes } = window.firebaseModules;
            const storage = getStorage(window.firebaseServices.app);
            const filename = `${scanId}_trained.ply`;
            const storageRef = ref(storage, `trained-splats/${userId}/${filename}`);
            
            // Convert Uint8Array to Blob
            const blob = new Blob([plyData], { type: 'application/octet-stream' });
            
            console.log(`Uploading ${blob.size} bytes to Firebase Storage...`);
            const uploadResult = await uploadBytes(storageRef, blob);
            
            console.log('Results uploaded successfully to:', uploadResult.ref.fullPath);
            return uploadResult;
        } catch (error) {
            console.error('Failed to upload results:', error);
            throw error;
        }
    }

    /**
     * Get training progress and statistics
     */
    getTrainingProgress() {
        if (!this.wasmTrainer || !this.isTraining()) {
            return null;
        }

        // The progress callback should handle this
        // This method is kept for compatibility
        return {
            isRunning: this.isTraining(),
            progress: 0 // Will be updated via callback
        };
    }

    /**
     * Get Firebase authentication token
     */
    async getFirebaseAuthToken() {
        try {
            const { getAuth } = window.firebaseModules;
            const auth = getAuth();
            const user = auth.currentUser;
            
            if (!user) {
                throw new Error('User not authenticated');
            }
            
            const token = await user.getIdToken();
            return token;
        } catch (error) {
            console.error('Failed to get Firebase auth token:', error);
            throw error;
        }
    }

    /**
     * Clean up resources
     */
    dispose() {
        if (this.wasmTrainer) {
            this.wasmTrainer.free();
            this.wasmTrainer = null;
        }
        
        if (this.canvas && this.canvas.parentNode) {
            this.canvas.parentNode.removeChild(this.canvas);
            this.canvas = null;
        }
        
        this.isInitialized = false;
        this.progressCallback = null;
    }
}

// Export for use in other modules
window.BrushTrainer = BrushTrainer; 