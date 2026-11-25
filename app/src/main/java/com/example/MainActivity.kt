package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebase.configureAppCheck
import com.example.ui.HisabApp
import com.example.ui.theme.HisabProTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity.onCreate started (package=$packageName)")

        val googleAppIdRes = resources.getIdentifier("google_app_id", "string", packageName)
        Log.i(TAG, "google_app_id resource present=${googleAppIdRes != 0}")

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)
        Log.i(TAG, "FirebaseApp count after AppCheck init=${FirebaseApp.getApps(this).size}")

        // STEP 2: Enable edge-to-edge display and render Compose UI
        enableEdgeToEdge()
        setContent {
            HisabProTheme {
                HisabApp()
            }
        }
        Log.i(TAG, "MainActivity.setContent completed")
    }

    companion object {
        private const val TAG = "HisabStartup"
    }
}
