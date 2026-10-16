package com.powerk.app

import android.app.Application

class SmartStripApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Server.init(this)
        Diag.init(this)
    }
}
