package ie.pantry

import android.app.Application
import ie.pantry.di.AppContainer

/** Application entry point. It owns the one [AppContainer] for the process. */
open class PantryApplication : Application() {

    /** Created on first read, thread-safely, so every caller sees the same container. */
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createContainer() }

    /** The container-creation hook. A test application overrides it to supply a container over test sources. */
    protected open fun createContainer(): AppContainer = AppContainer.production(this)
}
