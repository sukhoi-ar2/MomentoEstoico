package com.momentoestoico

import android.app.Application
import android.content.Context
import com.momentoestoico.alarm.MomentScheduler
import com.momentoestoico.data.AppLanguage
import com.momentoestoico.data.Repository
import com.momentoestoico.data.StoicLibrary
import com.momentoestoico.notify.MomentNotifier

class AppContainer(context: Context) {
    val repository = Repository(context)
    val scheduler = MomentScheduler(context)
    val notifier = MomentNotifier(context)
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        StoicLibrary.load(assets.open("thoughts.json").bufferedReader().use { it.readText() })
        container = AppContainer(this)
        container.notifier.createChannel(AppLanguage.SYSTEM.resolve())
    }
}
