package com.vislupus.waifuverse

import android.app.Application
import com.vislupus.waifuverse.data.AppRepository
import com.vislupus.waifuverse.sprite.SpriteLoader

/** Application: owns the long-lived singletons (database, sprite cache). */
class WaifuVerseApp : Application() {
    lateinit var repository: AppRepository
        private set
    lateinit var sprites: SpriteLoader
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        sprites = SpriteLoader(this)
    }
}
