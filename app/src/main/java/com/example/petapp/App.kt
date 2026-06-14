package com.example.petapp

import android.app.Application
import com.example.petapp.di.AppComponent
import com.example.petapp.di.DaggerAppComponent

/**
 * Application entry point that owns the Dagger [AppComponent].
 *
 * [appComponent] is initialized once in [onCreate] and exposed so that
 * [MainActivity] can retrieve the [com.example.petapp.di.ViewModelFactory]
 * without going through a service locator pattern elsewhere in the codebase.
 */
class App : Application() {

    lateinit var appComponent: AppComponent
        private set

    override fun onCreate() {
        super.onCreate()
        appComponent = DaggerAppComponent.factory().create(this)
    }
}
