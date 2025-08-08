#!/usr/bin/env bash
set -euxo pipefail

SCAN_ID="$(curl -sfH 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/attributes/SCAN_ID || echo '')"
USER_ID="$(curl -sfH 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/attributes/USER_ID || echo '')"
BUCKET="$(curl -sfH 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/attributes/BUCKET || echo '')"
BASE_PATH="$(curl -sfH 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/attributes/BASE_PATH || echo '')"

mkdir -p /workspace && cd /workspace
source /opt/conda/etc/profile.d/conda.sh || true
conda init bash || true
source ~/.bashrc || true
conda create -y -n colmap python=3.10
conda activate colmap
conda install -y -c conda-forge colmap cudatoolkit

mkdir -p input sparse dense
gsutil -m cp -r gs://${BUCKET}/${BASE_PATH}/images/* ./input/ || true
gsutil -m cp -r gs://${BUCKET}/${BASE_PATH}/sparse ./sparse || true

colmap image_undistorter --image_path input --input_path sparse --output_path dense --output_type COLMAP
colmap patch_match_stereo --workspace_path dense --workspace_format COLMAP --PatchMatchStereo.geom_consistency 1
colmap stereo_fusion --workspace_path dense --workspace_format COLMAP --output_path dense/fused.ply --StereoFusion.min_num_pixels 3

gsutil cp dense/fused.ply gs://${BUCKET}/${BASE_PATH}/dense/fused.ply || true

sudo shutdown -h now

