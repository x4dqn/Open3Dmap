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

### 2. **Google AR API Key (SECURE METHOD)**

1. **Get ARCore API Key**:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Enable ARCore API
   - Create credentials → API Key
   - Restrict the key to ARCore API

2. **Add API Key Securely**:
   - Open `Android App/Scanner/local.properties`
   - Add this line: `GOOGLE_AR_API_KEY=your_actual_api_key_here`
   - Replace `your_actual_api_key_here` with your real API key
   - **NEVER commit local.properties to git** (it's already in .gitignore)

   **Example local.properties:**
   ```properties
   sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
   GOOGLE_AR_API_KEY=AIzaSyD1234567890abcdefghijklmnopqrstuvwx
   ```

### 3. **Alternative: Environment Variables**

For CI/CD or server builds, you can also use environment variables:
```bash
export GOOGLE_AR_API_KEY=your_actual_api_key_here
```

The build system will automatically use environment variables if available, or fall back to local.properties.

### 4. **Google Sign-In Configuration**

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

2. **Configure API Keys** (see secure method above)

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

## Security Features

### API Key Security
- ✅ API keys are **never** stored in source code
- ✅ API keys are read from `local.properties` (not in git)
- ✅ Environment variable support for CI/CD
- ✅ BuildConfig integration for secure access
- ✅ Manifest placeholder replacement

### Files Never Committed:
- `local.properties` (contains API keys)
- `google-services.json` (Firebase config)
- `*.key` files
- `secrets.properties`

## Security Notes

- **NEVER** commit `google-services.json` to version control
- **NEVER** commit API keys in plain text
- Use environment variables or secure key management in production
- Rotate API keys regularly
- Always use `local.properties` for development API keys

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
   - Verify AR API key is correct in `local.properties`
   - Check device supports ARCore

3. **Build errors**:
   - Ensure Java 17 is installed
   - Clear Gradle cache: `./gradlew clean`
   - Check API key is set in `local.properties`

4. **API key not found**:
   - Ensure `GOOGLE_AR_API_KEY=your_key` is in `local.properties`
   - No spaces around the `=` sign
   - Check the file is in the correct location

## Support

For setup issues, please check:
1. This SETUP.md file
2. [Firebase Documentation](https://firebase.google.com/docs)
3. [ARCore Documentation](https://developers.google.com/ar) 