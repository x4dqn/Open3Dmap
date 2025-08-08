# Open3DMapWeb Production Deployment Guide (Public/Open Source)

This directory contains everything needed to deploy Open3DMapWeb to a production server.

## Prerequisites

- Node.js 20+ 
- Python 3.8+
- COLMAP (for 3D reconstruction)
- Firebase CLI
- Git
- Nginx (recommended)
- PM2 (for process management)

## Deployment Options

Choose one of the following deployment methods:

### Option 1: Firebase Hosting + Functions (Recommended)
### Option 2: Self-hosted with Nginx
### Option 3: Docker Deployment

---

## Option 1: Firebase Hosting + Functions Deployment

### 1. Firebase Setup

1. Install Firebase CLI:
```bash
npm install -g firebase-tools
```

2. Login to Firebase:
```bash
firebase login
```

3. Set your Firebase project:
```bash
firebase use --add
# Select your project ID (replace with your own)
```

### 2. Deploy to Firebase

Run the deployment script:
```bash
./deploy-firebase.sh
```

Or manually:
```bash
# Deploy functions
cd ../functions && npm install && firebase deploy --only functions

# Deploy hosting
firebase deploy --only hosting
```

---

## Option 2: Self-hosted Server Deployment

### 1. Server Setup

Copy files to your server:
```bash
scp -r deploy/ user@your-server:/var/www/open3dmap/
```

### 2. Install Dependencies

On your server:
```bash
cd /var/www/open3dmap
chmod +x setup-server.sh
./setup-server.sh
```

### 3. Configure Environment

Edit the environment file:
```bash
cp .env.example .env
nano .env
```

### 4. Start Services

```bash
# Start with PM2
pm2 start ecosystem.config.js

# Or with systemd
sudo systemctl enable open3dmap
sudo systemctl start open3dmap
```

---

## Option 3: Docker Deployment

### 1. Build and Run

```bash
# Build the image
docker build -t open3dmap .

# Run with docker-compose
docker-compose up -d
```

---

## Environment Variables

Required environment variables (set in `.env`):

```
# Firebase Configuration
FIREBASE_PROJECT_ID=YOUR_PROJECT_ID
FIREBASE_API_KEY=YOUR_API_KEY
FIREBASE_AUTH_DOMAIN=YOUR_PROJECT_ID.firebaseapp.com
FIREBASE_STORAGE_BUCKET=YOUR_PROJECT_ID.firebasestorage.app

# Server Configuration
NODE_ENV=production
PORT=3000
HOST=0.0.0.0

# COLMAP Configuration
COLMAP_PATH=/usr/local/bin/colmap
PYTHON_PATH=/usr/bin/python3

# Optional: Custom Domain
DOMAIN=yourdomain.com
SSL_CERT_PATH=/etc/ssl/certs/cert.pem
SSL_KEY_PATH=/etc/ssl/private/key.pem
```

---

## SSL/HTTPS Setup

### Let's Encrypt (Recommended)

```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d yourdomain.com

# Auto-renewal
sudo crontab -e
# Add: 0 12 * * * /usr/bin/certbot renew --quiet
```

---

## Monitoring and Logs

### PM2 Monitoring
```bash
pm2 monit
pm2 logs
pm2 restart all
```

### System Logs
```bash
sudo journalctl -u open3dmap -f
sudo tail -f /var/log/nginx/access.log
sudo tail -f /var/log/nginx/error.log
```

---

## Troubleshooting

### Common Issues

1. **Firebase Functions timeout**: Increase memory and timeout in functions
2. **COLMAP not found**: Install COLMAP and update PATH
3. **Permission errors**: Check file permissions and user groups
4. **Port conflicts**: Change PORT in .env file

### Health Checks

```bash
# Check service status
curl http://localhost:3000/health

# Check Firebase connectivity
curl https://your-app.web.app/

# Check COLMAP installation
colmap --help
```

---

## Backup and Recovery

### Database Backup
```bash
# Firestore backup (automated in Firebase Console)
# Manual export:
gcloud firestore export gs://your-backup-bucket
```

### File Backup
```bash
# Application files
tar -czf backup-$(date +%Y%m%d).tar.gz /var/www/open3dmap/

# Storage bucket backup
gsutil -m cp -r gs://YOUR_PROJECT_ID.firebasestorage.app gs://your-backup-bucket
```

---

## Performance Optimization

### Nginx Caching
- Static assets cached for 1 year
- HTML files not cached
- Gzip compression enabled

### Firebase Optimization
- Functions: 2GB memory, 540s timeout
- Hosting: CDN enabled globally
- Storage: Multi-region setup

### COLMAP Optimization
- GPU acceleration enabled
- Parallel processing configured
- Temp file cleanup automated

---

## Security Considerations

1. **Firewall**: Only allow necessary ports (80, 443, 22)
2. **Authentication**: Firebase Auth with proper rules
3. **CORS**: Configured for your domain only
4. **API Keys**: Environment variables, not hardcoded
5. **File uploads**: Size limits and type validation
6. **Rate limiting**: Implemented in functions

---

## Support

For deployment issues:
1. Check logs first
2. Verify environment variables
3. Test individual components
4. Check Firebase Console for errors
5. Review server resources (CPU, memory, disk)

## Updates

To update the application:
```bash
git pull origin main
./deploy-update.sh
``` 