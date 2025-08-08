// Firebase configuration for the web app (public-ready)
// IMPORTANT: Replace the placeholder values with your own Firebase project settings.
// You can copy these from your Firebase console → Project settings → General → Your apps.
//
// For open source usage, we export a simple object with placeholders. Do NOT commit real client secrets here.
// Note: Firebase web apiKey is not a secret, but keep other credentials (service accounts) out of the repo.

const firebaseConfig = {
  apiKey: "YOUR_FIREBASE_API_KEY",
  authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT_ID.firebasestorage.app",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID",
  measurementId: "YOUR_MEASUREMENT_ID"
};

export { firebaseConfig };
