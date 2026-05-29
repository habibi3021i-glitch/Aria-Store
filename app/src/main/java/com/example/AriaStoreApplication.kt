package com.example

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class AriaStoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                // Programmatically initialize Firebase using project settings from AI Studio
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:943392449649:android:9a5082da039f9486b5a7d0")
                    .setApiKey("AIzaSyA3aE9OyQ8OsIUGT_5ja89MHPodVqgIVUQ")
                    .setDatabaseUrl("https://wajidtechtube-default-rtdb.firebaseio.com")
                    .setProjectId("wajidtechtube")
                    .setStorageBucket("wajidtechtube.firebasestorage.app")
                    .build()

                FirebaseApp.initializeApp(this, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
