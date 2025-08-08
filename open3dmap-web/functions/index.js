const { onCall } = require('firebase-functions/v2/https');
const admin = require('firebase-admin');
const { Storage } = require('@google-cloud/storage');
const fetch = require('node-fetch');
const path = require('path');
const os = require('os');
const fs = require('fs').promises;
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

admin.initializeApp();
const storage = new Storage();
const BUCKET_NAME = process.env.GCS_BUCKET || 'YOUR_PROJECT_ID.firebasestorage.app';

// Install COLMAP dependencies on startup
async function installCOLMAP() {
  try {
    console.log('Checking if COLMAP is already installed...');
    
    // Check if COLMAP already exists
    try {
      await execPromise('which colmap');
      console.log('COLMAP already installed, skipping installation');
      return;
    } catch (error) {
      console.log('COLMAP not found, installing...');
    }

    // Run the installation script
    const installScript = path.join(__dirname, 'install-colmap.sh');
    
    // Make script executable
    await execPromise(`chmod +x ${installScript}`);
    
    // Run installation
    console.log('Running COLMAP installation script...');
    const { stdout, stderr } = await execPromise(`bash ${installScript}`, {
      timeout: 600000, // 10 minutes timeout
      maxBuffer: 1024 * 1024 * 10 // 10MB buffer
    });
    
    console.log('COLMAP installation completed:', stdout);
    if (stderr) console.log('Installation warnings:', stderr);
    
  } catch (error) {
    console.error('Failed to install COLMAP:', error);
    // Don't throw - let functions continue with mock data
  }
}

// Install COLMAP on module load
let colmapInstallPromise = null;
function ensureCOLMAP() {
  if (!colmapInstallPromise) {
    colmapInstallPromise = installCOLMAP();
  }
  return colmapInstallPromise;
}

// Enhanced logging class without emojis
class COLMAPLogger {
  constructor(scanId, userId) {
    this.scanId = scanId;
    this.userId = userId;
    this.startTime = Date.now();
  }

  log(stage, message, data = {}) {
    const timestamp = new Date().toISOString();
    const elapsed = Date.now() - this.startTime;

    console.log(`[${timestamp}] [${this.scanId}] [${this.userId}] [${stage}] ${message}`, {
      ...data,
      elapsed: `${elapsed}ms`
    });
  }

  error(stage, message, error) {
    const timestamp = new Date().toISOString();
    const elapsed = Date.now() - this.startTime;

    console.error(`[ERROR] [${timestamp}] [${this.scanId}] [${this.userId}] [${stage}] ${message}`, {
      error: error.message,
      stack: error.stack,
      elapsed: `${elapsed}ms`
    });
  }

  warn(stage, message, data = {}) {
    const timestamp = new Date().toISOString();
    const elapsed = Date.now() - this.startTime;

    console.warn(`[WARN] [${timestamp}] [${this.scanId}] [${this.userId}] [${stage}] ${message}`, {
      ...data,
      elapsed: `${elapsed}ms`
    });
  }
}

// Enhanced COLMAP installation checker
async function checkCOLMAPInstallation(logger) {
  const checks = [];

  // Check if COLMAP binary exists
  try {
    const { stdout } = await execPromise('which colmap', { timeout: 10000 });
    checks.push({ check: 'COLMAP binary', status: 'FOUND', path: stdout.trim() });
    logger.log('health-check', 'COLMAP binary found', { path: stdout.trim() });
  } catch (error) {
    checks.push({ check: 'COLMAP binary', status: 'NOT_FOUND', error: error.message });
    logger.error('health-check', 'COLMAP binary not found', error);
  }

  // Check COLMAP version using help command (official Docker image doesn't support --version)
  try {
    const { stdout } = await execPromise('colmap help', { timeout: 10000 });
    // If help command works, COLMAP is functional
    checks.push({ check: 'COLMAP version', status: 'OK', version: 'Official Docker Image' });
    logger.log('health-check', 'COLMAP help command works - installation is functional');
  } catch (error) {
    checks.push({ check: 'COLMAP version', status: 'ERROR', error: error.message });
    logger.error('health-check', 'COLMAP help command failed', error);
  }

  // Check required dependencies
  const dependencies = ['cmake', 'python3', 'gcc'];
  for (const dep of dependencies) {
    try {
      const { stdout } = await execPromise(`which ${dep}`, { timeout: 5000 });
      checks.push({ check: dep, status: 'FOUND', path: stdout.trim() });
      logger.log('health-check', `${dep} found`, { path: stdout.trim() });
    } catch (error) {
      checks.push({ check: dep, status: 'NOT_FOUND', error: error.message });
      logger.warn('health-check', `${dep} not found`, { error: error.message });
    }
  }

  return checks;
}

// Enhanced image download with retry logic
async function downloadImageWithRetry(imageUrl, outputPath, logger, retries = 3) {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      logger.log('download', `Downloading image attempt ${attempt}/${retries}`, { url: imageUrl });

      const response = await fetch(imageUrl, {
        timeout: 30000,
        headers: {
          'User-Agent': 'Open3DMap-CloudFunction/2.0'
        }
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const fileData = await response.buffer();
      await fs.writeFile(outputPath, fileData);

      logger.log('download', 'Image downloaded successfully', {
        url: imageUrl,
        size: fileData.length,
        attempt
      });

      return fileData.length;

    } catch (error) {
      logger.error('download', `Download attempt ${attempt} failed`, error);

      if (attempt === retries) {
        throw new Error(`Failed to download image after ${retries} attempts: ${error.message}`);
      }

      // Wait before retry (exponential backoff)
      await new Promise(resolve => setTimeout(resolve, 1000 * Math.pow(2, attempt - 1)));
    }
  }
}

// Parallel image downloads with progress streaming
async function downloadImagesParallel(imageUrls, inputDir, logger, response) {
  const batchSize = 10; // Process 10 images at a time
  const results = [];
  let totalDownloaded = 0;

  logger.log('download', `Starting parallel download of ${imageUrls.length} images`, { batchSize });

  for (let i = 0; i < imageUrls.length; i += batchSize) {
    const batch = imageUrls.slice(i, i + batchSize);

    const batchPromises = batch.map(async(url, index) => {
      const globalIndex = i + index;
      const fileName = url.split('/').pop().split('?')[0];

      // Ensure filename has proper extension
      let finalFileName = fileName;
      if (!finalFileName.match(/\.(jpg|jpeg|png|webp)$/i)) {
        finalFileName += '.jpg';
      }

      const outputPath = path.join(inputDir, finalFileName);
      const fileSize = await downloadImageWithRetry(url, outputPath, logger);

      return {
        index: globalIndex,
        fileName: finalFileName,
        path: outputPath,
        size: fileSize
      };
    });

    const batchResults = await Promise.all(batchPromises);
    results.push(...batchResults);
    totalDownloaded += batchResults.length;

    // Send progress update via streaming
    if (response.sendChunk) {
      const progress = Math.round((totalDownloaded / imageUrls.length) * 30); // 30% for downloads
      response.sendChunk({
        stage: 'downloading',
        progress,
        message: `Downloaded ${totalDownloaded}/${imageUrls.length} images`,
        completed: totalDownloaded,
        total: imageUrls.length,
        currentBatch: Math.floor(i / batchSize) + 1,
        totalBatches: Math.ceil(imageUrls.length / batchSize)
      });
    }

    logger.log('download', `Completed batch ${Math.floor(i / batchSize) + 1}`, {
      batchSize: batchResults.length,
      totalDownloaded,
      totalImages: imageUrls.length
    });
  }

  logger.log('download', 'All images downloaded successfully', {
    totalImages: results.length,
    totalSize: results.reduce((sum, r) => sum + r.size, 0)
  });

  return results;
}

// Enhanced COLMAP processing with streaming updates
async function runCOLMAPProcessing(tempDir, userId, scanId, logger, response) {
  try {
    logger.log('colmap-start', 'Starting COLMAP pipeline');

    // Enhanced COLMAP parameters for better image registration rate
    logger.log('colmap-enhancement', 'Using enhanced COLMAP parameters to register more images', {
      siftFeatures: 'DSP-SIFT with affine shape estimation',
      matching: 'Guided matching with relaxed thresholds',
      reconstruction: 'Reduced model size requirements and more inclusive registration'
    });

    // Check COLMAP installation
    const healthChecks = await checkCOLMAPInstallation(logger);
    const colmapAvailable = healthChecks.find(c => c.check === 'COLMAP binary')?.status === 'FOUND';

    if (!colmapAvailable) {
      logger.warn('colmap-health', 'COLMAP not available, creating mock results');
      return await createMockCOLMAPResults(tempDir, userId, scanId, logger, response);
    }

    // Feature extraction
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'feature_extraction',
        progress: 40,
        message: 'Extracting features from images...',
        details: 'Using SIFT feature detector'
      });
    }

    logger.log('colmap-features', 'Starting feature extraction');

    // Enhanced COLMAP parameters for better image registration:
    // - estimate_affine_shape: More robust feature detection
    // - domain_size_pooling: More discriminative DSP-SIFT features
    // - Higher max_num_features: More features per image
    // - Lower peak_threshold: More sensitive feature detection
    const featureCommand = `colmap feature_extractor \
            --database_path ${path.join(tempDir, 'distorted/database.db')} \
            --image_path ${path.join(tempDir, 'input')} \
            --ImageReader.camera_model OPENCV \
            --ImageReader.single_camera 1 \
            --SiftExtraction.use_gpu 0 \
            --SiftExtraction.estimate_affine_shape 1 \
            --SiftExtraction.domain_size_pooling 1 \
            --SiftExtraction.max_num_features 16384 \
            --SiftExtraction.peak_threshold 0.004 \
            --SiftExtraction.edge_threshold 15`;

    const { stdout: featureStdout, stderr: featureStderr } = await execPromise(featureCommand, {
      cwd: tempDir,
      timeout: 600000, // 10 minutes timeout
      maxBuffer: 1024 * 1024 * 10 // 10MB buffer
    });

    logger.log('colmap-features', 'Feature extraction completed', {
      stdout: featureStdout ? featureStdout.substring(0, 500) : 'No output',
      stderr: featureStderr ? featureStderr.substring(0, 500) : 'No errors'
    });

    // Feature matching
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'feature_matching',
        progress: 60,
        message: 'Matching features between images...',
        details: 'Using exhaustive matcher'
      });
    }

    logger.log('colmap-matching', 'Starting feature matching');

    // Enhanced matching parameters for more image pairs:
    // - guided_matching: Better matching using geometric constraints
    // - Higher max_ratio: More lenient distance ratio
    // - Higher max_num_matches: More matches per image pair
    // - Lower min_num_inliers: More lenient geometric verification
    const matchCommand = `colmap exhaustive_matcher \
            --database_path ${path.join(tempDir, 'distorted/database.db')} \
            --SiftMatching.use_gpu 0 \
            --SiftMatching.guided_matching 1 \
            --SiftMatching.max_ratio 0.9 \
            --SiftMatching.max_num_matches 65536 \
            --TwoViewGeometry.min_num_inliers 12 \
            --TwoViewGeometry.max_error 6`;

    const { stdout: matchStdout, stderr: matchStderr } = await execPromise(matchCommand, {
      cwd: tempDir,
      timeout: 1200000, // 20 minutes timeout
      maxBuffer: 1024 * 1024 * 10 // 10MB buffer
    });

    logger.log('colmap-matching', 'Feature matching completed', {
      stdout: matchStdout ? matchStdout.substring(0, 500) : 'No output',
      stderr: matchStderr ? matchStderr.substring(0, 500) : 'No errors'
    });

    // Sparse reconstruction
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'sparse_reconstruction',
        progress: 80,
        message: 'Building 3D reconstruction...',
        details: 'Creating sparse point cloud'
      });
    }

    logger.log('colmap-reconstruction', 'Starting sparse reconstruction');

    // Enhanced mapper parameters for more inclusive image registration:
    // - min_model_size: Reduced from 10 to 3 (smaller models allowed)
    // - min_num_matches: Reduced from 15 to 10 (fewer matches needed)
    // - init_min_num_inliers: Reduced from 100 to 50 (easier initialization)
    // - abs_pose_min_num_inliers: Reduced from 30 to 15 (easier pose estimation)
    // - tri_ignore_two_view_tracks: Disabled (use all possible tracks)
    // - multiple_models: Enabled (allows disconnected reconstructions)
    const mapperCommand = `colmap mapper \
            --database_path ${path.join(tempDir, 'distorted/database.db')} \
            --image_path ${path.join(tempDir, 'input')} \
            --output_path ${path.join(tempDir, 'sparse')} \
            --Mapper.min_model_size 3 \
            --Mapper.min_num_matches 10 \
            --Mapper.init_min_num_inliers 50 \
            --Mapper.abs_pose_min_num_inliers 15 \
            --Mapper.abs_pose_min_inlier_ratio 0.15 \
            --Mapper.filter_max_reproj_error 6 \
            --Mapper.tri_min_angle 1.0 \
            --Mapper.tri_ignore_two_view_tracks 0 \
            --Mapper.multiple_models 1 \
            --Mapper.max_num_models 10`;

    const { stdout: mapperStdout, stderr: mapperStderr } = await execPromise(mapperCommand, {
      cwd: tempDir,
      timeout: 1800000, // 30 minutes timeout
      maxBuffer: 1024 * 1024 * 10 // 10MB buffer
    });

    logger.log('colmap-reconstruction', 'Sparse reconstruction completed', {
      stdout: mapperStdout ? mapperStdout.substring(0, 500) : 'No output',
      stderr: mapperStderr ? mapperStderr.substring(0, 500) : 'No errors'
    });

    // Verify reconstruction results
    const sparseDir = path.join(tempDir, 'sparse', '0');
    const databasePath = path.join(tempDir, 'distorted', 'database.db');

    const sparseExists = await fs.access(sparseDir).then(() => true).catch(() => false);
    const databaseExists = await fs.access(databasePath).then(() => true).catch(() => false);

    if (!sparseExists) {
      // List sparse directory contents for debugging
      const sparseParent = path.join(tempDir, 'sparse');
      try {
        const contents = await fs.readdir(sparseParent);
        logger.error('colmap-reconstruction', 'Sparse reconstruction failed', {
          sparseDir,
          contents
        });
      } catch (error) {
        logger.error('colmap-reconstruction', 'Sparse directory does not exist', { sparseDir, error: error.message });
      }
      throw new Error('COLMAP sparse reconstruction failed: no sparse output directory found');
    }

    if (!databaseExists) {
      throw new Error('COLMAP database not found');
    }

    // Upload results
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'uploading',
        progress: 95,
        message: 'Uploading reconstruction results...',
        details: 'Storing files to Firebase Storage'
      });
    }

    logger.log('colmap-upload', 'Starting upload of results');
    const results = await uploadCOLMAPResults(tempDir, userId, scanId, logger);

    logger.log('colmap-complete', 'COLMAP processing completed successfully', {
      uploadedFiles: results.uploadedFiles.length
    });

    return {
      success: true,
      message: 'Successfully processed COLMAP reconstruction',
      scanId: scanId,
      status: 'completed',
      sparseDir: results.sparseDir,
      imagesDir: results.imagesDir,
      databasePath: results.databasePath,
      uploadedFiles: results.uploadedFiles,
      healthChecks
    };

  } catch (error) {
    logger.error('colmap-processing', 'COLMAP processing failed', error);
    throw new Error(`COLMAP processing failed: ${error.message}`);
  }
}

// Enhanced mock COLMAP results with streaming
async function createMockCOLMAPResults(tempDir, userId, scanId, logger, response) {
  try {
    logger.log('mock-start', 'Creating mock COLMAP reconstruction results');

    // Create mock directory structure
    const sparseDir = path.join(tempDir, 'sparse', '0');
    const distortedDir = path.join(tempDir, 'distorted');

    await fs.mkdir(sparseDir, { recursive: true });
    await fs.mkdir(distortedDir, { recursive: true });

    // Create mock COLMAP output files with realistic but simple content
    const camerasContent = `# Camera list with one line of data per camera:
#   CAMERA_ID, MODEL, WIDTH, HEIGHT, PARAMS[]
# Number of cameras: 1
1 OPENCV 800 600 400 400 400 300 0.1 0.05`;
    await fs.writeFile(path.join(sparseDir, 'cameras.txt'), camerasContent);

    const imagesContent = `# Image list with two lines of data per image:
#   IMAGE_ID, QW, QX, QY, QZ, TX, TY, TZ, CAMERA_ID, NAME
#   POINTS2D[] as (X, Y, POINT3D_ID)
# Number of images: 3, mean observations per image: 100
1 0.7071 0.0000 0.0000 0.7071 0.0000 0.0000 0.0000 1 image1.jpg

2 0.7071 0.0000 0.0000 0.7071 1.0000 0.0000 0.0000 1 image2.jpg

3 0.7071 0.0000 0.0000 0.7071 2.0000 0.0000 0.0000 1 image3.jpg
`;
    await fs.writeFile(path.join(sparseDir, 'images.txt'), imagesContent);

    const points3DContent = `# 3D point list with one line of data per point:
#   POINT3D_ID, X, Y, Z, R, G, B, ERROR, TRACK[] as (IMAGE_ID, POINT2D_IDX)
# Number of points: 10, mean track length: 2.5
1 0.0 0.0 0.0 255 0 0 0.5 1 100 2 150
2 1.0 0.0 0.0 0 255 0 0.5 1 101 2 151
3 0.0 1.0 0.0 0 0 255 0.5 1 102 2 152
4 0.0 0.0 1.0 255 255 0 0.5 1 103 2 153
5 1.0 1.0 0.0 255 0 255 0.5 2 104 3 154
6 1.0 0.0 1.0 0 255 255 0.5 2 105 3 155
7 0.0 1.0 1.0 128 128 128 0.5 2 106 3 156
8 1.0 1.0 1.0 255 255 255 0.5 3 107 1 157
9 0.5 0.5 0.5 64 64 64 0.5 3 108 1 158
10 -0.5 -0.5 -0.5 192 192 192 0.5 3 109 1 159`;
    await fs.writeFile(path.join(sparseDir, 'points3D.txt'), points3DContent);

    // Create a mock database file
    const databasePath = path.join(distortedDir, 'database.db');
    await fs.writeFile(databasePath, Buffer.alloc(1024)); // Create a 1KB mock database file

    logger.log('mock-files', 'Mock COLMAP files created successfully');

    // Upload the mock results
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'uploading',
        progress: 95,
        message: 'Uploading mock reconstruction results...',
        details: 'COLMAP not available, using mock data'
      });
    }

    const results = await uploadCOLMAPResults(tempDir, userId, scanId, logger);

    logger.log('mock-complete', 'Mock COLMAP processing completed');

    return {
      success: true,
      message: 'Mock COLMAP reconstruction completed (COLMAP not available in runtime)',
      scanId: scanId,
      status: 'completed',
      isMockData: true,
      sparseDir: results.sparseDir,
      imagesDir: results.imagesDir,
      databasePath: results.databasePath,
      uploadedFiles: results.uploadedFiles
    };

  } catch (error) {
    logger.error('mock-processing', 'Mock COLMAP processing failed', error);
    throw new Error(`Mock COLMAP processing failed: ${error.message}`);
  }
}

// Enhanced upload function with progress tracking
async function uploadCOLMAPResults(tempDir, userId, scanId, logger) {
  try {
    logger.log('upload-start', 'Uploading COLMAP results to Firebase Storage');

    const bucket = storage.bucket(BUCKET_NAME);
    const basePath = `colmap-results/${userId}/${scanId}`;
    const uploadedFiles = [];

    // Upload sparse reconstruction files
    const sparseDir = path.join(tempDir, 'sparse', '0');
    const sparseFiles = await fs.readdir(sparseDir);

    logger.log('upload-sparse', `Uploading ${sparseFiles.length} sparse files`);

    for (const file of sparseFiles) {
      const localPath = path.join(sparseDir, file);
      const remotePath = `${basePath}/sparse/${file}`;

      await bucket.upload(localPath, {
        destination: remotePath,
        metadata: {
          contentType: 'application/octet-stream',
          metadata: {
            scanId,
            userId,
            uploadTime: new Date().toISOString()
          }
        }
      });

      uploadedFiles.push(remotePath);
      logger.log('upload-file', `Uploaded sparse file: ${file}`);
    }

    // Upload database file
    const databasePath = path.join(tempDir, 'distorted', 'database.db');
    const remoteDatabasePath = `${basePath}/database.db`;

    await bucket.upload(databasePath, {
      destination: remoteDatabasePath,
      metadata: {
        contentType: 'application/octet-stream',
        metadata: {
          scanId,
          userId,
          uploadTime: new Date().toISOString()
        }
      }
    });

    uploadedFiles.push(remoteDatabasePath);
    logger.log('upload-file', 'Uploaded database file');

    // Upload input images for reference
    const inputDir = path.join(tempDir, 'input');
    const inputFiles = await fs.readdir(inputDir);

    logger.log('upload-images', `Uploading ${inputFiles.length} input images`);

    for (const file of inputFiles) {
      const localPath = path.join(inputDir, file);
      const remotePath = `${basePath}/images/${file}`;

      await bucket.upload(localPath, {
        destination: remotePath,
        metadata: {
          contentType: 'image/jpeg',
          metadata: {
            scanId,
            userId,
            uploadTime: new Date().toISOString()
          }
        }
      });

      uploadedFiles.push(remotePath);
    }

    logger.log('upload-complete', `Successfully uploaded ${uploadedFiles.length} files`, {
      totalFiles: uploadedFiles.length,
      basePath
    });

    return {
      sparseDir: `${basePath}/sparse/`,
      imagesDir: `${basePath}/images/`,
      databasePath: `${basePath}/database.db`,
      uploadedFiles: uploadedFiles
    };

  } catch (error) {
    logger.error('upload-error', 'Failed to upload COLMAP results', error);
    throw new Error(`Failed to upload COLMAP results: ${error.message}`);
  }
}

// Main Firebase Function with streaming support
exports.processCOLMAP = onCall({
  timeoutSeconds: 3600,    // 60 minutes for large datasets
  memory: '16GiB',         // 16GB memory for processing 2000+ images
  cpu: 4,                  // 4 CPU cores for parallel processing
  concurrency: 1,          // One request per instance for resource-intensive tasks
  invoker: 'private'       // Require authentication
}, async(request, response) => {
  const logger = new COLMAPLogger(request.data?.scanId || 'unknown', request.auth?.uid || 'anonymous');

  logger.log('function-start', 'COLMAP processing function started', {
    hasAuth: !!request.auth,
    supportsStreaming: !!request.acceptsStreaming,
    requestData: request.data
  });

  // Ensure user is authenticated
  if (!request.auth) {
    logger.error('auth-error', 'No authentication context provided');
    throw new Error('Authentication required');
  }

  const userId = request.auth.uid;
  let tempDir;

  try {
    // Ensure COLMAP is installed before processing
    logger.log('colmap-install', 'Ensuring COLMAP is installed...');
    await ensureCOLMAP();
    logger.log('colmap-install', 'COLMAP installation check completed');

    const { scanId, imageUrls } = request.data;

    // Validate required fields
    if (!scanId) {
      logger.error('validation-error', 'scanId is missing from request');
      throw new Error('scanId is required');
    }

    if (!imageUrls || !Array.isArray(imageUrls)) {
      logger.error('validation-error', 'imageUrls validation failed', {
        provided: typeof imageUrls,
        isArray: Array.isArray(imageUrls)
      });
      throw new Error('imageUrls must be an array');
    }

    if (imageUrls.length === 0) {
      logger.error('validation-error', 'imageUrls array is empty');
      throw new Error('imageUrls array cannot be empty');
    }

    logger.log('validation-success', `All validations passed. Processing ${imageUrls.length} images`);

    // Send initial streaming update
    if (request.acceptsStreaming) {
      response.sendChunk({
        stage: 'initializing',
        progress: 0,
        message: 'Starting COLMAP processing...',
        totalImages: imageUrls.length,
        scanId: scanId
      });
    }

    // Create temporary directory for processing
    tempDir = path.join(os.tmpdir(), `colmap-${scanId}-${Date.now()}`);
    const inputDir = path.join(tempDir, 'input');
    const distortedDir = path.join(tempDir, 'distorted');

    await fs.mkdir(inputDir, { recursive: true });
    await fs.mkdir(distortedDir, { recursive: true });

    logger.log('temp-dir', `Created temporary directory: ${tempDir}`);

    // Download images with parallel processing and streaming updates
    const imageResults = await downloadImagesParallel(imageUrls, inputDir, logger, response);

    if (imageResults.length === 0) {
      throw new Error('No valid images were downloaded');
    }

    logger.log('download-complete', `Downloaded ${imageResults.length} images successfully`, {
      totalSize: imageResults.reduce((sum, r) => sum + r.size, 0)
    });

    // Run COLMAP processing with streaming updates
    const colmapResults = await runCOLMAPProcessing(tempDir, userId, scanId, logger, response);

    // Send final completion update
    if (request.acceptsStreaming) {
      response.sendChunk({
        stage: 'completed',
        progress: 100,
        message: 'COLMAP processing completed successfully',
        scanId: scanId,
        uploadedFiles: colmapResults.uploadedFiles?.length || 0
      });
    }

    logger.log('function-complete', 'COLMAP processing completed successfully', {
      processingTime: Date.now() - logger.startTime,
      totalFiles: colmapResults.uploadedFiles?.length || 0
    });

    return colmapResults;

  } catch (error) {
    logger.error('function-error', 'COLMAP processing failed', error);

    // Send error update via streaming
    if (request.acceptsStreaming) {
      response.sendChunk({
        stage: 'error',
        progress: 0,
        message: `Error: ${error.message}`,
        error: true
      });
    }

    throw new Error(`COLMAP processing failed: ${error.message}`);
  } finally {
    // Clean up temporary directory
    if (tempDir) {
      try {
        await fs.rm(tempDir, { recursive: true, force: true });
        logger.log('cleanup', `Cleaned up temporary directory: ${tempDir}`);
      } catch (error) {
        logger.error('cleanup', 'Error cleaning up temporary directory', error);
      }
    }
  }
});

// Health check endpoint for monitoring
exports.checkCOLMAPHealth = onCall({
  timeoutSeconds: 60,
  memory: '1GiB',
  cpu: 1,
  invoker: 'public'  // Allow unauthenticated access for health monitoring
}, async(request) => {
  const logger = new COLMAPLogger('health-check', request.auth?.uid || 'anonymous');

  try {
    logger.log('health-start', 'Starting COLMAP health check');

    // Ensure COLMAP is installed before health check
    logger.log('colmap-install', 'Ensuring COLMAP is installed for health check...');
    await ensureCOLMAP();
    logger.log('colmap-install', 'COLMAP installation check completed');

    const healthChecks = await checkCOLMAPInstallation(logger);

    const overallStatus = healthChecks.some(check => check.status === 'NOT_FOUND' || check.status === 'ERROR')
      ? 'UNHEALTHY' : 'HEALTHY';

    logger.log('health-complete', `Health check completed: ${overallStatus}`);

    return {
      status: overallStatus,
      timestamp: new Date().toISOString(),
      checks: healthChecks
    };

  } catch (error) {
    logger.error('health-error', 'Health check failed', error);
    return {
      status: 'ERROR',
      timestamp: new Date().toISOString(),
      error: error.message
    };
  }
});

