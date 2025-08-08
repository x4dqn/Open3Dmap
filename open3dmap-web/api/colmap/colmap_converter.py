#!/usr/bin/env python3
import os
import sys
import argparse
import subprocess
import logging
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def run_command(command, cwd=None):
    """Run a shell command and log its output."""
    try:
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            shell=True,
            cwd=cwd,
            universal_newlines=True
        )
        stdout, stderr = process.communicate()
        
        if process.returncode != 0:
            logger.error(f"Command failed with return code {process.returncode}")
            logger.error(f"Error: {stderr}")
            raise RuntimeError(f"Command failed: {command}")
        
        return stdout, stderr
    except Exception as e:
        logger.error(f"Error running command: {e}")
        raise

def create_directories(base_path):
    """Create necessary directories for COLMAP processing."""
    dirs = ['images', 'sparse', 'distorted']
    for dir_name in dirs:
        dir_path = os.path.join(base_path, dir_name)
        os.makedirs(dir_path, exist_ok=True)
        logger.info(f"Created directory: {dir_path}")

def process_images(source_path, camera_model="OPENCV"):
    """Process images using COLMAP pipeline."""
    try:
        # Create necessary directories
        create_directories(source_path)
        
        # Get the input directory (where images are stored)
        input_dir = os.path.join(source_path, 'input')
        if not os.path.exists(input_dir):
            raise RuntimeError(f"Input directory not found: {input_dir}")
        
        # Get list of images
        image_files = [f for f in os.listdir(input_dir) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if not image_files:
            raise RuntimeError("No images found in input directory")
        
        logger.info(f"Found {len(image_files)} images to process")
        
        # Feature extraction
        logger.info("Extracting features...")
        feature_extractor_cmd = f"colmap feature_extractor \
            --database_path {os.path.join(source_path, 'distorted/database.db')} \
            --image_path {input_dir} \
            --ImageReader.camera_model {camera_model} \
            --ImageReader.single_camera 1 \
            --SiftExtraction.use_gpu 1 \
            --SiftExtraction.estimate_affine_shape 1 \
            --SiftExtraction.domain_size_pooling 1 \
            --SiftExtraction.max_num_features 16384 \
            --SiftExtraction.peak_threshold 0.004 \
            --SiftExtraction.edge_threshold 15"
        run_command(feature_extractor_cmd, source_path)
        
        # Feature matching
        logger.info("Matching features...")
        feature_matcher_cmd = f"colmap exhaustive_matcher \
            --database_path {os.path.join(source_path, 'distorted/database.db')} \
            --SiftMatching.use_gpu 1 \
            --SiftMatching.guided_matching 1 \
            --SiftMatching.max_ratio 0.9 \
            --SiftMatching.max_num_matches 65536 \
            --TwoViewGeometry.min_num_inliers 12 \
            --TwoViewGeometry.max_error 6"
        run_command(feature_matcher_cmd, source_path)
        
        # Sparse reconstruction
        logger.info("Performing sparse reconstruction...")
        mapper_cmd = f"colmap mapper \
            --database_path {os.path.join(source_path, 'distorted/database.db')} \
            --image_path {input_dir} \
            --output_path {os.path.join(source_path, 'sparse')} \
            --Mapper.min_model_size 3 \
            --Mapper.min_num_matches 10 \
            --Mapper.init_min_num_inliers 50 \
            --Mapper.abs_pose_min_num_inliers 15 \
            --Mapper.abs_pose_min_inlier_ratio 0.15 \
            --Mapper.filter_max_reproj_error 6 \
            --Mapper.tri_min_angle 1.0 \
            --Mapper.tri_ignore_two_view_tracks 0 \
            --Mapper.multiple_models 1 \
            --Mapper.max_num_models 10"
        run_command(mapper_cmd, source_path)
        
        # Check if reconstruction was successful
        sparse_dir = os.path.join(source_path, 'sparse', '0')
        if not os.path.exists(sparse_dir):
            raise RuntimeError("Sparse reconstruction failed: no output directory created")
        
        logger.info("COLMAP processing completed successfully")
        return True
        
    except Exception as e:
        logger.error(f"Error in COLMAP processing: {e}")
        raise

def main():
    parser = argparse.ArgumentParser(description='Process images using COLMAP')
    parser.add_argument('--source_path', required=True, help='Path to the source directory containing images')
    parser.add_argument('--camera', default='OPENCV', help='Camera model to use (default: OPENCV)')
    
    args = parser.parse_args()
    
    try:
        # Validate source path
        source_path = os.path.abspath(args.source_path)
        if not os.path.exists(source_path):
            raise RuntimeError(f"Source path does not exist: {source_path}")
        
        # Process images
        success = process_images(source_path, args.camera)
        
        if success:
            logger.info("COLMAP processing completed successfully")
            sys.exit(0)
        else:
            logger.error("COLMAP processing failed")
            sys.exit(1)
            
    except Exception as e:
        logger.error(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 