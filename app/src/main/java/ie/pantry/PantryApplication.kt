package ie.pantry

import android.app.Application
import ie.pantry.di.AppContainer

/** Application entry point. It owns the one [AppContainer] for the process. */
class PantryApplication : Application() {

    /** Created on first read, thread-safely, so every caller sees the same container. */
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer.production(this) }
}
