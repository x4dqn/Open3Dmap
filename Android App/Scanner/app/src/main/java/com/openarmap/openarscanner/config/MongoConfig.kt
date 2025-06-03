package com.openarmap.openarscanner.config

object MongoConfig {
    const val APP_ID = "openarmap"
    const val DATABASE_NAME = "openarmap"
    const val CLUSTER_NAME = "openarmap"
    const val CLUSTER_ID = "tfcwauy"
    
    // Get the connection string from environment variable or use a default for development
    val CONNECTION_STRING: String
        get() = System.getenv("MONGODB_CONNECTION_STRING") ?: 
            "mongodb+srv://jwkirk15:${System.getenv("MONGODB_PASSWORD")}@${CLUSTER_NAME}.${CLUSTER_ID}.mongodb.net/?retryWrites=true&w=majority&appName=${APP_ID}"
    
    // Realm configuration
    const val REALM_NAME = "openarmap.realm"
    
    // Sync configuration
    const val SYNC_ENABLED = true
    const val SYNC_FREQUENCY = 300L // 5 minutes in seconds
} 