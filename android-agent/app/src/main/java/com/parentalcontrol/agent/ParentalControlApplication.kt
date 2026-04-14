package com.parentalcontrol.agent

import android.app.Application

class ParentalControlApplication : Application() {
    lateinit var container: AgentContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AgentContainer(this)
    }
}

