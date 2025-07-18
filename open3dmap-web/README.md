# Open3DMapWeb

A web-based 3D reconstruction and Gaussian Splatting application using COLMAP and Firebase.

## Features

- 📸 **Image Upload & Management** - Upload and organize scan images
- 🔄 **COLMAP Integration** - Automated 3D reconstruction using COLMAP
- 🎨 **Gaussian Splatting** - Advanced 3D rendering with Gaussian Splatting
- 🔐 **Firebase Authentication** - Secure user authentication with Google Sign-in
- ☁️ **Cloud Storage** - Firebase Storage for image and model storage
- 🚀 **Cloud Processing** - Google Cloud Run for heavy COLMAP processing
- 📱 **Responsive Design** - Works on desktop and mobile devices

## Technology Stack

- **Frontend**: HTML5, CSS3, JavaScript (ES6+)
- **Backend**: Node.js, Firebase Functions
- **Database**: Firebase Firestore
- **Storage**: Firebase Storage
- **Authentication**: Firebase Auth
- **3D Processing**: COLMAP, Gaussian Splatting
- **Cloud Services**: Google Cloud Run, Firebase Hosting

## Getting Started

### Prerequisites

- Node.js (v16 or higher)
- Firebase CLI
- Google Cloud SDK (for Cloud Run deployment)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/open3dmap-web.git
cd open3dmap-web
```

2. Install dependencies:
```bash
npm install
```

3. Set up Firebase configuration:
```bash
# Copy the example environment file
cp env.example .env

# Edit .env with your Firebase configuration
# Get your config from Firebase Console -> Project Settings -> Web App
```

4. Run the development server:
```bash
npm run dev
```

5. Open your browser and navigate to `http://localhost:3000`

### Firebase Setup

1. Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Enable the following services:
   - Authentication (Google Sign-in)
   - Firestore Database
   - Storage
   - Hosting
3. Set up Firebase Storage rules (see `storage.rules`)
4. Set up Firestore rules (see `firestore.rules`)

### Google Cloud Setup

1. Enable Google Cloud Run API
2. Deploy the COLMAP processor service
3. Update the Cloud Run service URL in `js/dashboard.js`

## Project Structure

```
open3dmap-web/
├── index.html              # Main landing page
├── dashboard.html          # User dashboard
├── brush-trainer.html      # Gaussian Splatting trainer
├── js/                     # JavaScript modules
│   ├── auth.js            # Authentication handling
│   ├── dashboard.js       # Dashboard functionality
│   ├── main.js            # Main application logic
│   └── firebase-config.js # Firebase configuration
├── styles/                 # CSS stylesheets
├── assets/                 # Static assets
├── functions/             # Firebase Cloud Functions
├── api/                   # API endpoints
├── deployment/            # Deployment scripts
└── docs/                  # Documentation

```

## Development

### Running Locally

```bash
# Start development server
npm run dev

# Build production version
npm run build

# Deploy to Firebase
firebase deploy
```

### Testing

The project includes diagnostic tools for testing:
- Firebase Authentication
- Storage access
- COLMAP processing
- Cloud Run services

## Configuration

### Environment Variables

Create a `.env` file with your Firebase configuration:

```env
FIREBASE_API_KEY=your_api_key
FIREBASE_AUTH_DOMAIN=your_project.firebaseapp.com
FIREBASE_PROJECT_ID=your_project_id
FIREBASE_STORAGE_BUCKET=your_project.appspot.com
FIREBASE_MESSAGING_SENDER_ID=your_sender_id
FIREBASE_APP_ID=your_app_id
```

### OAuth Configuration

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to APIs & Services > Credentials
3. Create or edit your OAuth 2.0 Client ID
4. Add authorized JavaScript origins:
   - `http://localhost:3000` (development)
   - `https://your-domain.com` (production)
5. Add authorized redirect URIs:
   - `http://localhost:3000`
   - `https://your-project.firebaseapp.com/__/auth/handler`

## Deployment

### Firebase Hosting

```bash
# Build and deploy
npm run build
firebase deploy --only hosting
```

### Cloud Run (COLMAP Service)

```bash
# Deploy COLMAP processor
./deploy-colmap.sh
```

### Docker Deployment

```bash
# Build and deploy with Docker
./deploy-docker.sh
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

If you encounter any issues:
1. Check the [documentation](docs/)
2. Review the [troubleshooting guide](docs/troubleshooting.md)
3. Open an issue on GitHub

## Acknowledgments

- [COLMAP](https://colmap.github.io/) for 3D reconstruction
- [Three.js](https://threejs.org/) for 3D rendering
- [Firebase](https://firebase.google.com/) for backend services
- [Google Cloud](https://cloud.google.com/) for cloud processing 