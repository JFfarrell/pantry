package com.pantry.app.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.pantry.app.AppContainer
import com.pantry.app.PantryApp

/** Reaches the app's dependency container from inside a ViewModel factory. */
val CreationExtras.container: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PantryApp).container

val Application.container: AppContainer
    get() = (this as PantryApp).container
