# Google Cloud Shell CORS Fix Commands

## 🚀 **Run these commands in Google Cloud Shell:**

### **Step 1: Create CORS configuration file**
```bash
cat > cors.json << 'EOF'
[
  {
    "origin": ["https://people.rit.edu", "http://localhost:*", "https://localhost:*"],
    "method": ["GET", "HEAD", "PUT", "POST", "DELETE"],
    "maxAgeSeconds": 3600,
    "responseHeader": ["Content-Type", "Access-Control-Allow-Origin", "x-goog-resumable"]
  }
]
EOF
```

### **Step 2: Apply CORS configuration to your Firebase Storage bucket**
```bash
gsutil cors set cors.json gs://openarmap.firebasestorage.app
```

### **Step 3: Verify the CORS configuration was applied**
```bash
gsutil cors get gs://openarmap.firebasestorage.app
```

### **Step 4: Check current project (should be openarmap)**
```bash
gcloud config get-value project
```

If it's not `openarmap`, set it:
```bash
gcloud config set project openarmap
```

## ✅ **Expected Output**
After running the `gsutil cors set` command, you should see:
```
Setting CORS on gs://openarmap.firebasestorage.app/...
```

After running `gsutil cors get`, you should see your CORS configuration displayed.

## 🔧 **If you get permission errors:**
```bash
# Authenticate with your Google account
gcloud auth login

# Set the project
gcloud config set project openarmap

# Try the cors command again
gsutil cors set cors.json gs://openarmap.firebasestorage.app
```

## 🎯 **That's it!**
Once you run these commands, wait about 5-10 minutes for the changes to propagate, then test your app at `https://people.rit.edu/~jwk5651/` 