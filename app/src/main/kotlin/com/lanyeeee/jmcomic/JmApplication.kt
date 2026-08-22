package com.lanyeeee.jmcomic

import android.app.Application

class JmApplication : Application() {
    lateinit var container: JmContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = JmContainer(this)
    }
}
