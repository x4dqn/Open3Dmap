const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs').promises;

// Configure multer for memory storage
const upload = multer({ storage: multer.memoryStorage() });

router.post('/upload', upload.single('image'), async (req, res) => {
    try {
        console.log('Received upload request');
        console.log('Request body:', req.body);
        
        if (!req.file) {
            console.error('No file received');
            return res.status(400).json({
                success: false,
                error: 'No image file provided'
            });
        }

        const { targetPath } = req.body;
        if (!targetPath) {
            console.error('No target path provided');
            return res.status(400).json({
                success: false,
                error: 'No target path provided'
            });
        }

        console.log('File details:', {
            originalname: req.file.originalname,
            mimetype: req.file.mimetype,
            size: req.file.size
        });

        // Create the directory if it doesn't exist
        const fullPath = path.join(__dirname, '../../../temp', targetPath);
        console.log('Saving file to:', fullPath);
        
        await fs.mkdir(path.dirname(fullPath), { recursive: true });
        await fs.writeFile(fullPath, req.file.buffer);
        
        console.log('File saved successfully');

        res.json({
            success: true,
            message: 'Image uploaded successfully',
            path: targetPath,
            fullPath: fullPath
        });

    } catch (error) {
        console.error('Error uploading image:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

module.exports = router; 