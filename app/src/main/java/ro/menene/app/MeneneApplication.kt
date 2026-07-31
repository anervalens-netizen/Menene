package ro.menene.app

import android.app.Application

class MeneneApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.warmUp()
    }
}
