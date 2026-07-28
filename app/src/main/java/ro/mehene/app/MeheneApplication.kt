package ro.mehene.app

import android.app.Application

class MeheneApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
