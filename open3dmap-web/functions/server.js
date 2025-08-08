import express from 'express';
import cors from 'cors';
import admin from 'firebase-admin';
import { Storage } from '@google-cloud/storage';
import path from 'path';
import fs from 'fs/promises';
import os from 'os';
import fetch from 'node-fetch';
import { exec } from 'child_process';
import util from 'util';

const execPromise = util.promisify(exec);

// Initialize Firebase Admin with error handling
let firebaseInitialized = false;
let storage;

console.log('Step 1: Starting Firebase Admin initialization...');
try {
  console.log('Initializing Firebase Admin...');
  console.log('GOOGLE_CLOUD_PROJECT:', process.env.GOOGLE_CLOUD_PROJECT);
  
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    storageBucket: process.env.GCS_BUCKET || 'YOUR_PROJECT_ID.firebasestorage.app'
  });
  
  storage = new Storage();
  firebaseInitialized = true;
  console.log('Firebase Admin initialized successfully');
} catch (error) {
  console.error('Firebase Admin initialization failed:', error);
  console.log('Continuing without Firebase Admin - some features may be limited');
}

console.log('Step 2: Initializing Express app...');
// Initialize Express app
const app = express();

console.log('Step 3: Configuring CORS and middleware...');
// Configure CORS to allow requests from localhost during development
const corsOptions = {
  origin: [
    'http://localhost:3000',
    'https://localhost:3000',
    'http://127.0.0.1:3000',
    'https://127.0.0.1:3000',
    'https://openarmap.web.app',
    'https://openarmap.firebaseapp.com'
  ],
  credentials: true,
  optionsSuccessStatus: 200
};

app.use(cors(corsOptions));
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

console.log('Step 4: Setting up routes...');

// Enhanced logging class (copied from index.js)
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

  error(stage, message, error = {}) {
    const timestamp = new Date().toISOString();
    const elapsed = Date.now() - this.startTime;
    console.error(`[${timestamp}] [${this.scanId}] [${this.userId}] [${stage}] ERROR: ${message}`, {
      error: error.message || error,
      elapsed: `${elapsed}ms`
    });
  }

  warn(stage, message, data = {}) {
    const timestamp = new Date().toISOString();
    const elapsed = Date.now() - this.startTime;
    console.warn(`[${timestamp}] [${this.scanId}] [${this.userId}] [${stage}] WARNING: ${message}`, {
      ...data,
      elapsed: `${elapsed}ms`
    });
  }
}

// Configuration
const BUCKET_NAME = process.env.GCS_BUCKET || 'YOUR_PROJECT_ID.firebasestorage.app';

// -------------------- GPU Orchestration Helpers (optional) --------------------
const PROJECT_ID = process.env.GOOGLE_CLOUD_PROJECT || process.env.PROJECT_ID;
const ENABLE_GPU_AUTOMATION = (process.env.ENABLE_GPU_AUTOMATION || '0') === '1';
const GPU_ZONE = process.env.GPU_ZONE || 'us-central1-a';
const GPU_INSTANCE_TEMPLATE = process.env.GPU_INSTANCE_TEMPLATE || 'colmap-gpu-template';

async function getGoogleAccessToken() {
  try {
    const res = await fetch('http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token', {
      headers: { 'Metadata-Flavor': 'Google' },
      timeout: 5000
    });
    if (!res.ok) throw new Error(`Metadata token error: ${res.status}`);
    const json = await res.json();
    return json.access_token;
  } catch (e) {
    return null;
  }
}

async function launchGpuDenseJob({ logger, userId, scanId, bucketName, basePath }) {
  if (!ENABLE_GPU_AUTOMATION) {
    logger.log('gpu-automation', 'GPU automation disabled by env');
    return;
  }
  if (!PROJECT_ID) {
    logger.warn('gpu-automation', 'PROJECT_ID missing, cannot launch GPU job');
    return;
  }
  const accessToken = await getGoogleAccessToken();
  if (!accessToken) {
    logger.warn('gpu-automation', 'Failed to obtain metadata access token, skipping GPU launch');
    return;
  }

  const instanceName = `colmap-gpu-${scanId.slice(0, 8)}-${Date.now()}`.toLowerCase();
  const url = `https://compute.googleapis.com/compute/v1/projects/${PROJECT_ID}/zones/${GPU_ZONE}/instances`;

  const metadataItems = [
    { key: 'SCAN_ID', value: scanId },
    { key: 'USER_ID', value: userId },
    { key: 'BUCKET', value: bucketName },
    { key: 'BASE_PATH', value: basePath }
  ];

  const body = {
    name: instanceName,
    sourceInstanceTemplate: `projects/${PROJECT_ID}/global/instanceTemplates/${GPU_INSTANCE_TEMPLATE}`,
    metadata: { items: metadataItems }
  };

  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body),
      timeout: 10000
    });
    const text = await res.text();
    if (!res.ok) {
      logger.warn('gpu-automation', 'Compute Engine insert failed', { status: res.status, text });
      return;
    }
    logger.log('gpu-automation', 'Launched GPU dense VM', { instanceName, response: text.substring(0, 500) });
  } catch (e) {
    logger.warn('gpu-automation', 'Error launching GPU dense VM', { error: e?.message || String(e) });
  }
}

async function hasBinary(name) {
  try {
    await execPromise(`which ${name}`, { timeout: 3000 });
    return true;
  } catch {
    return false;
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

// Enhanced COLMAP processing with dense reconstruction and optional GPU orchestration
async function runCOLMAPProcessing(tempDir, userId, scanId, logger, response) {
  try {
    logger.log('colmap-start', 'Starting COLMAP pipeline');

    // Check COLMAP installation - FAIL FAST if not available
    const healthChecks = await checkCOLMAPInstallation(logger);
    const colmapAvailable = healthChecks.find(c => c.check === 'COLMAP binary')?.status === 'FOUND';

    if (!colmapAvailable) {
      logger.error('colmap-health', 'COLMAP binary not available in Docker container', {
        healthChecks
      });
      throw new Error('COLMAP binary not available - service cannot process images without COLMAP');
    }

    logger.log('colmap-health', 'COLMAP binary verified and available', {
      healthChecks
    });

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

    const featureCommand = `colmap feature_extractor --database_path ${path.join(tempDir, 'distorted/database.db')} --image_path ${path.join(tempDir, 'input')} --ImageReader.camera_model OPENCV --ImageReader.single_camera 1 --SiftExtraction.use_gpu 0 --SiftExtraction.estimate_affine_shape 1 --SiftExtraction.domain_size_pooling 1 --SiftExtraction.max_num_features 16384 --SiftExtraction.peak_threshold 0.004 --SiftExtraction.edge_threshold 15`;

    const { stdout: featureStdout, stderr: featureStderr } = await execPromise(featureCommand, {
      cwd: tempDir,
      timeout: 1800000, // 30 minutes
      maxBuffer: 1024 * 1024 * 10 // 10MB buffer
    });

    logger.log('colmap-features', 'Feature extraction completed');

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

    const matchCommand = `colmap exhaustive_matcher --database_path ${path.join(tempDir, 'distorted/database.db')} --SiftMatching.use_gpu 0 --SiftMatching.guided_matching 1 --SiftMatching.max_ratio 0.9 --SiftMatching.max_num_matches 65536 --TwoViewGeometry.min_num_inliers 12 --TwoViewGeometry.max_error 6`;

    const { stdout: matchStdout, stderr: matchStderr } = await execPromise(matchCommand, {
      cwd: tempDir,
      timeout: 1800000, // 30 minutes
      maxBuffer: 1024 * 1024 * 10 // 10MB buffer
    });

    logger.log('colmap-matching', 'Feature matching completed');

    // Sparse reconstruction
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'sparse_reconstruction',
        progress: 80,
        message: 'Creating 3D point cloud...',
        details: 'Running sparse reconstruction'
      });
    }

    logger.log('colmap-reconstruction', 'Starting sparse reconstruction');

    const sparseDir = path.join(tempDir, 'sparse');
    await fs.mkdir(sparseDir, { recursive: true });

    const reconstructCommand = `colmap mapper --database_path ${path.join(tempDir, 'distorted/database.db')} --image_path ${path.join(tempDir, 'input')} --output_path ${sparseDir} --Mapper.num_threads 4 --Mapper.min_model_size 10 --Mapper.multiple_models 0 --Mapper.extract_colors 1`;

    const { stdout: reconstructStdout, stderr: reconstructStderr } = await execPromise(reconstructCommand, {
      cwd: tempDir,
      timeout: 1800000, // 30 minutes
      maxBuffer: 1024 * 1024 * 10 // 10MB buffer
    });

    logger.log('colmap-reconstruction', 'Sparse reconstruction completed');

    // -------------------- DENSE RECONSTRUCTION --------------------
    // Cloud Run typically has no GPU; attempt dense and gracefully skip if CUDA unavailable.
    let denseReconstructionPerformed = false;
    let denseSkipReason = '';
    let denseOutputPath = '';

    // Prepare dense workspace via image_undistorter
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'image_undistorter',
        progress: 82,
        message: 'Preparing dense workspace...',
        details: 'Running image_undistorter'
      });
    }

    const sparseDirChecked = path.join(tempDir, 'sparse', '0');
    const denseRoot = path.join(tempDir, 'dense');
    logger.log('colmap-dense', 'Running image_undistorter', { sparseDir: sparseDirChecked, denseRoot });

    const undistorterCmd = `colmap image_undistorter \
            --image_path ${path.join(tempDir, 'input')} \
            --input_path ${sparseDirChecked} \
            --output_path ${denseRoot} \
            --output_type COLMAP`;
    logger.log('colmap-dense', 'image_undistorter command', { command: undistorterCmd });
    await execPromise(undistorterCmd, { cwd: tempDir, timeout: 30 * 60 * 1000, maxBuffer: 1024 * 1024 * 20 });

    // PatchMatch stereo (CUDA required)
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'dense_reconstruction',
        progress: 88,
        message: 'Running PatchMatch stereo...',
        details: 'Generating depth maps'
      });
    }

    const patchMatchCmd = `colmap patch_match_stereo --workspace_path ${denseRoot} --workspace_format COLMAP --PatchMatchStereo.geom_consistency 1`;
    logger.log('colmap-dense', 'Running PatchMatch command', { command: patchMatchCmd });
    try {
      await execPromise(patchMatchCmd, { cwd: tempDir, timeout: 60 * 60 * 1000, maxBuffer: 1024 * 1024 * 10 });
    } catch (e) {
      const message = e?.message || String(e);
      if (/requires CUDA|CUDA/i.test(message)) {
        logger.warn('colmap-dense', 'Skipping PatchMatch stereo: CUDA not available', { reason: message });
        denseSkipReason = 'CUDA not available on Cloud Run instance';
      } else {
        throw e;
      }
    }

    // Stereo fusion
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'dense_fusion',
        progress: 92,
        message: 'Fusing depth maps...',
        details: 'Creating dense point cloud'
      });
    }

    try {
      const fusedPly = path.join(denseRoot, 'fused.ply');
      const stereoFusionCmd = `colmap stereo_fusion --workspace_path ${denseRoot} --workspace_format COLMAP --output_path ${fusedPly} --StereoFusion.min_num_pixels 3`;
      logger.log('colmap-dense', 'Running stereo_fusion command', { command: stereoFusionCmd });
      await execPromise(stereoFusionCmd, { cwd: tempDir, timeout: 30 * 60 * 1000, maxBuffer: 1024 * 1024 * 10 });
      denseReconstructionPerformed = true;
      denseOutputPath = fusedPly;
      logger.log('colmap-dense', 'Dense reconstruction completed', { fusedPly });
    } catch (e) {
      if (!denseSkipReason) denseSkipReason = e?.message || String(e);
      logger.warn('colmap-dense', 'Skipping stereo_fusion due to missing depth maps or CUDA unavailability', { reason: denseSkipReason });
    }

    // CPU DENSE FALLBACK VIA OPENMVS
    if (!denseReconstructionPerformed) {
      const enableCpuDense = (process.env.ENABLE_CPU_DENSE || '1') === '1';
      if (enableCpuDense) {
        const hasInterface = await hasBinary('InterfaceCOLMAP');
        const hasDensify = await hasBinary('DensifyPointCloud');
        if (hasInterface && hasDensify) {
          try {
            if (response.sendChunk) {
              response.sendChunk({
                stage: 'dense_reconstruction_cpu',
                progress: 90,
                message: 'Running OpenMVS CPU dense reconstruction...',
                details: 'InterfaceCOLMAP + DensifyPointCloud'
              });
            }
            logger.log('colmap-dense-cpu', 'Starting OpenMVS conversion and densification');
            const mvsScene = path.join(denseRoot, 'scene.mvs');
            const interfaceCmd = `InterfaceCOLMAP -i ${path.join(denseRoot, 'sparse')} -o ${mvsScene} -d ${path.join(denseRoot, 'images')}`;
            logger.log('colmap-dense-cpu', 'Running InterfaceCOLMAP', { command: interfaceCmd });
            await execPromise(interfaceCmd, { cwd: tempDir, timeout: 30 * 60 * 1000, maxBuffer: 1024 * 1024 * 20 });
            const densifyCmd = `DensifyPointCloud ${mvsScene} --resolution-level 1 --min-resolution 320 --num-threads 4`;
            logger.log('colmap-dense-cpu', 'Running DensifyPointCloud', { command: densifyCmd });
            await execPromise(densifyCmd, { cwd: tempDir, timeout: 60 * 60 * 1000, maxBuffer: 1024 * 1024 * 20 });
            const mvsDensePly = path.join(denseRoot, 'scene_dense.ply');
            denseReconstructionPerformed = true;
            denseOutputPath = mvsDensePly;
            logger.log('colmap-dense-cpu', 'CPU dense reconstruction completed', { mvsDensePly });
          } catch (e) {
            logger.warn('colmap-dense-cpu', 'OpenMVS dense failed, proceeding without dense', { error: e?.message || String(e) });
          }
        } else {
          logger.warn('colmap-dense-cpu', 'OpenMVS tools not found on PATH, skipping CPU dense', { hasInterface, hasDensify });
        }
      }
    }

    // Upload results (including optional dense output)
    if (response.sendChunk) {
      response.sendChunk({
        stage: 'uploading',
        progress: 95,
        message: 'Uploading reconstruction results...',
        details: 'Storing files to Firebase Storage'
      });
    }

    const results = await uploadCOLMAPResults(tempDir, userId, scanId, logger, { denseOutputPath });

    logger.log('colmap-complete', 'COLMAP processing completed successfully', {
      uploadedFiles: results.uploadedFiles.length,
      denseReconstructionPerformed,
      denseSkipReason: denseReconstructionPerformed ? undefined : denseSkipReason
    });

    // If dense not performed locally, optionally trigger GPU automation
    if (!denseReconstructionPerformed) {
      await launchGpuDenseJob({
        logger,
        userId,
        scanId,
        bucketName: BUCKET_NAME,
        basePath: `colmap-results/${userId}/${scanId}`
      });
    }

    return {
      success: true,
      message: 'Successfully processed COLMAP reconstruction',
      scanId: scanId,
      status: 'completed',
      sparseDir: results.sparseDir,
      imagesDir: results.imagesDir,
      databasePath: results.databasePath,
      uploadedFiles: results.uploadedFiles,
      denseReconstructionPerformed,
      denseSkipReason: denseReconstructionPerformed ? undefined : denseSkipReason
    };

  } catch (error) {
    logger.error('colmap-error', 'COLMAP processing failed', error);
    throw new Error(`COLMAP processing failed: ${error.message}`);
  }
}

// Note: uploadCOLMAPResults function is defined below

// Enhanced upload function with progress tracking
async function uploadCOLMAPResults(tempDir, userId, scanId, logger, options = {}) {
  const { denseOutputPath } = options;
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

    // Optionally upload dense output (fused or OpenMVS)
    if (denseOutputPath) {
      try {
        const denseFileName = path.basename(denseOutputPath);
        const remoteDensePath = `${basePath}/dense/${denseFileName}`;
        await bucket.upload(denseOutputPath, {
          destination: remoteDensePath,
          metadata: {
            contentType: 'application/octet-stream',
            metadata: { scanId, userId, uploadTime: new Date().toISOString() }
          }
        });
        uploadedFiles.push(remoteDensePath);
        logger.log('upload-file', `Uploaded dense file: ${denseFileName}`);
      } catch (e) {
        logger.warn('upload-dense', 'Failed to upload dense output (continuing)', { error: e?.message || String(e) });
      }
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

// Mock function removed - service now requires real COLMAP processing

// Root route
app.get('/', (req, res) => {
  console.log('Root route requested');
  res.json({
    message: 'COLMAP processor server is running',
    status: 'HEALTHY',
    timestamp: new Date().toISOString(),
    firebaseInitialized: firebaseInitialized
  });
});

// Handle preflight OPTIONS requests explicitly
app.options('*', (req, res) => {
  console.log('OPTIONS request received for:', req.path);
  res.status(200).end();
});

// Health check endpoint - simplified to avoid hanging
app.get('/health', (req, res) => {
  console.log('Health check requested');
  res.json({
    status: 'HEALTHY',
    timestamp: new Date().toISOString(),
    message: 'COLMAP processor server is running',
    firebaseInitialized: firebaseInitialized,
    nodeVersion: process.version,
    platform: process.platform
  });
});

// Process COLMAP endpoint
app.post('/process', async (req, res) => {
  const { scanId, imageUrls, userId } = req.body;
  
  if (!scanId || !imageUrls || !userId) {
    return res.status(400).json({
      error: 'Missing required fields: scanId, imageUrls, userId'
    });
  }
  
  const logger = new COLMAPLogger(scanId, userId);
  let tempDir;
  
  // Validate Firebase authentication token if Firebase is initialized
  if (firebaseInitialized) {
    try {
      const authHeader = req.headers.authorization;
      if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return res.status(401).json({
          error: 'Missing or invalid authorization header'
        });
      }
      
      const token = authHeader.substring(7); // Remove 'Bearer ' prefix
      const decodedToken = await admin.auth().verifyIdToken(token);
      
      // Verify that the token's uid matches the provided userId
      if (decodedToken.uid !== userId) {
        return res.status(403).json({
          error: 'Token uid does not match provided userId'
        });
      }
      
      logger.log('auth-success', 'Firebase authentication verified', {
        uid: decodedToken.uid,
        email: decodedToken.email
      });
      
    } catch (error) {
      logger.error('auth-error', 'Firebase authentication failed', error);
      return res.status(401).json({
        error: 'Invalid Firebase authentication token'
      });
    }
  } else {
    logger.warn('auth-warning', 'Firebase Admin not initialized - skipping authentication');
  }
  
  try {
    logger.log('process-start', 'Starting COLMAP processing', {
      imageCount: imageUrls.length,
      scanId,
      userId
    });
    
    // Create response object that mimics Firebase Functions streaming
    const response = {
      sendChunk: (data) => {
        // In a real implementation, you might want to use Server-Sent Events
        console.log('Progress update:', data);
      }
    };
    
    // Create temporary directory for processing
    tempDir = path.join(os.tmpdir(), `colmap-${scanId}-${Date.now()}`);
    const inputDir = path.join(tempDir, 'input');
    const distortedDir = path.join(tempDir, 'distorted');

    await fs.mkdir(inputDir, { recursive: true });
    await fs.mkdir(distortedDir, { recursive: true });

    logger.log('temp-dir', `Created temporary directory: ${tempDir}`);

    // Download images with parallel processing
    const imageResults = await downloadImagesParallel(imageUrls, inputDir, logger, response);

    if (imageResults.length === 0) {
      throw new Error('No valid images were downloaded');
    }

    logger.log('download-complete', `Downloaded ${imageResults.length} images successfully`);

    // Process with COLMAP
    const results = await runCOLMAPProcessing(tempDir, userId, scanId, logger, response);
    
    res.json({
      success: true,
      results
    });
    
  } catch (error) {
    logger.error('process-error', 'COLMAP processing failed', error);
    
    res.status(500).json({
      success: false,
      error: error.message
    });
  } finally {
    // Clean up temporary directory
    if (tempDir) {
      try {
        await fs.rm(tempDir, { recursive: true, force: true });
        logger.log('cleanup', 'Temporary directory cleaned up');
      } catch (cleanupError) {
        logger.warn('cleanup', 'Failed to clean up temporary directory', cleanupError);
      }
    }
  }
});

// Note: Root route (/) is defined earlier in the file

// Start server
// Initialize Firebase Storage bucket
  const bucket = storage.bucket(BUCKET_NAME);

// Note: downloadImagesParallel function is defined earlier in the file

// Note: runCOLMAPProcessing function is defined earlier in the file

// Note: uploadCOLMAPResults function is defined earlier in the file

const PORT = process.env.PORT || 8080;
console.log('Starting COLMAP processor server...');
console.log('Environment:', {
  NODE_ENV: process.env.NODE_ENV,
  PORT: PORT,
  GOOGLE_CLOUD_PROJECT: process.env.GOOGLE_CLOUD_PROJECT,
  firebaseInitialized: firebaseInitialized
});

console.log('Attempting to start server on port', PORT);

const server = app.listen(PORT, '0.0.0.0', () => {
  console.log(`✓ COLMAP processor server running on port ${PORT}`);
  console.log(`✓ Health check: http://localhost:${PORT}/health`);
  console.log(`✓ Process endpoint: http://localhost:${PORT}/process`);
  console.log(`✓ Firebase initialized: ${firebaseInitialized}`);
  console.log('Server is ready to handle requests');
});

server.on('error', (err) => {
  console.error('Server error:', err);
  process.exit(1);
});

// Handle uncaught exceptions
process.on('uncaughtException', (err) => {
  console.error('Uncaught exception:', err);
  process.exit(1);
});

// Handle unhandled promise rejections
process.on('unhandledRejection', (err) => {
  console.error('Unhandled promise rejection:', err);
  process.exit(1);
});

export default app; 