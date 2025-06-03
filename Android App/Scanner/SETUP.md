# Open3DMap Scanner - Setup Instructions

## Required API Keys & Configuration

This project requires several API keys and configuration files that are **not included** in the repository for security reasons.

### 1. **Firebase Configuration**

1. **Create Firebase Project**:
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Create a new project or use existing `openarmap` project

2. **Download google-services.json**:
   - In Firebase Console → Project Settings → Your App
   - Download `google-services.json`
   - Place it in: `Android App/Scanner/app/google-services.json`
   - **Reference**: Use `google-services.json.template` as a guide

3. **Enable Firebase Services**:
   - **Authentication**: Enable Email/Password and Google Sign-In
   - **Firestore**: Create database
   - **Storage**: Enable Firebase Storage
   - **App Check**: Configure with Play Integrity API

### 2. **Google AR API Key**

1. **Get ARCore API Key**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Enable ARCore API
   - Create credentials → API Key
   - Restrict the key to ARCore API

2. **Add API Key**:
   - Open `Android App/Scanner/app/src/main/AndroidManifest.xml`
   - Replace `YOUR_GOOGLE_AR_API_KEY_HERE` with your actual API key

### 3. **Google Sign-In Configuration**

1. **Get SHA1 Fingerprint**:
   ```bash
   cd "Android App/Scanner"
   ./gradlew signingReport
   ```

2. **Add SHA1 to Firebase**:
   - Firebase Console → Project Settings → Your App
   - Add the SHA1 fingerprint from debug keystore

3. **Download Updated google-services.json**:
   - After adding SHA1, download updated `google-services.json`

## Build Instructions

### Prerequisites
- **Java 17** (LTS)
- **Android Studio** (latest)
- **Android SDK** with API level 34

### Build Steps

1. **Clone Repository**:
   ```bash
   git clone <repository-url>
   cd OpenARMap
   ```

2. **Configure API Keys** (see above sections)

3. **Open in Android Studio**:
   - Open `Android App/Scanner` folder
   - Let Gradle sync

4. **Build Project**:
   ```bash
   ./gradlew assembleDebug
   ```

## Required Permissions

The app requires these Android permissions:
- **Camera** (for AR scanning)
- **Internet** (for Firebase)
- **Location** (for GPS tagging)
- **Storage** (for saving scans)

## Security Notes

- **NEVER** commit `google-services.json` to version control
- **NEVER** commit API keys in plain text
- Use environment variables or secure key management in production
- Rotate API keys regularly

## Dependencies

All dependencies are managed via Gradle and will be downloaded automatically:
- Firebase BoM 32.7.4
- ARCore 1.41.0
- Material Design 3
- Kotlin 1.9.22
- See `app/build.gradle` for complete list

## Troubleshooting

### Common Issues:

1. **Firebase Authentication fails**:
   - Check `google-services.json` is correct
   - Verify SHA1 fingerprint in Firebase Console

2. **ARCore crashes**:
   - Verify AR API key is correct
   - Check device supports ARCore

3. **Build errors**:
   - Ensure Java 17 is installed
   - Clear Gradle cache: `./gradlew clean`

## Support

For setup issues, please check:
1. This SETUP.md file
2. [Firebase Documentation](https://firebase.google.com/docs)
3. [ARCore Documentation](https://developers.google.com/ar) 