package com.tgx.react

import android.app.Application

class TGXReactApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReactionRepository.init(this)
    }
}
