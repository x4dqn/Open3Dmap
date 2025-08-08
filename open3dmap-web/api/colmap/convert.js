const express = require('express');
const router = express.Router();
const { Storage } = require('@google-cloud/storage');
const path = require('path');
const fs = require('fs').promises;
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

// Initialize Google Cloud Storage with default credentials
const storage = new Storage();
// Use env-configured bucket for OSS safety; users must set GCS_BUCKET
const bucket = storage.bucket(process.env.GCS_BUCKET || 'YOUR_PROJECT_ID.appspot.com');

router.post('/convert', async (req, res) => {
    try {
        const { scanId, imageUrls } = req.body;
        
        if (!scanId || !imageUrls || !Array.isArray(imageUrls)) {
            throw new Error('Invalid request: scanId and imageUrls array are required');
        }
        
        // Create a local directory for processing
        const localDir = path.join(__dirname, '../../../temp', scanId);
        await fs.mkdir(localDir, { recursive: true });
        
        // Download images from Firebase Storage
        const imagePaths = [];
        for (const imageUrl of imageUrls) {
            try {
                // Extract the path from the URL
                const imagePath = imageUrl.split('/o/')[1]?.split('?')[0];
                if (!imagePath) {
                    console.warn(`Invalid image URL format: ${imageUrl}`);
                    continue;
                }

                const [file] = await bucket.file(decodeURIComponent(imagePath)).download();
                const localPath = path.join(localDir, path.basename(imagePath));
                await fs.writeFile(localPath, file);
                imagePaths.push(localPath);
            } catch (error) {
                console.error(`Error downloading image ${imageUrl}:`, error);
                throw error;
            }
        }
        
        // Run COLMAP processing
        const colmapResult = await runCOLMAPProcessing(localDir, imagePaths);
        
        // Upload results back to Firebase Storage
        const resultsPath = `colmap-results/${scanId}`;
        const results = await uploadResultsToStorage(localDir, resultsPath);
        
        // Clean up temporary directory
        await fs.rm(localDir, { recursive: true, force: true });
        
        res.json({
            success: true,
            sparseDir: results.sparseDir,
            imagesDir: results.imagesDir,
            databasePath: results.databasePath
        });
        
    } catch (error) {
        console.error('Error in COLMAP conversion:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

async function runCOLMAPProcessing(tempDir, imagePaths) {
    try {
        // Create input directory for COLMAP
        const inputDir = path.join(tempDir, 'input');
        await fs.mkdir(inputDir, { recursive: true });

        // Copy images to input directory
        for (const imagePath of imagePaths) {
            const fileName = path.basename(imagePath);
            await fs.copyFile(imagePath, path.join(inputDir, fileName));
        }

        // Run COLMAP conversion script
        const scriptPath = path.join(__dirname, 'colmap_converter.py');
        const { stdout, stderr } = await execPromise(`python ${scriptPath} --source_path ${tempDir} --camera OPENCV`, {
            cwd: tempDir
        });

        console.log('COLMAP conversion output:', stdout);
        if (stderr) {
            console.error('COLMAP conversion errors:', stderr);
        }

        // Check if conversion was successful by looking for output files
        const sparseDir = path.join(tempDir, 'sparse', '0');
        const imagesDir = path.join(tempDir, 'images');
        
        if (!await fs.access(sparseDir).then(() => true).catch(() => false)) {
            throw new Error('COLMAP conversion failed: sparse reconstruction not found');
        }

        return {
            sparseDir,
            imagesDir,
            databasePath: path.join(tempDir, 'distorted', 'database.db')
        };
    } catch (error) {
        throw new Error(`COLMAP processing failed: ${error.message}`);
    }
}

async function uploadResultsToStorage(tempDir, resultsPath) {
    const results = {};
    
    // Upload sparse reconstruction
    const sparseDir = path.join(tempDir, 'sparse');
    const sparseFiles = await fs.readdir(sparseDir);
    
    for (const file of sparseFiles) {
        const localPath = path.join(sparseDir, file);
        const remotePath = `${resultsPath}/sparse/${file}`;
        await bucket.upload(localPath, { destination: remotePath });
        results[file] = remotePath;
    }
    
    // Upload database
    const dbPath = path.join(tempDir, 'database.db');
    const remoteDbPath = `${resultsPath}/database.db`;
    await bucket.upload(dbPath, { destination: remoteDbPath });
    results.database = remoteDbPath;
    
    return results;
}

module.exports = router; 