package com.agent.lumina

import android.app.Application
import com.google.firebase.FirebaseApp

class LuminaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
