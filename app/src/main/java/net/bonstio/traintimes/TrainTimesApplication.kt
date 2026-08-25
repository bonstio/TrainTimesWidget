package net.bonstio.traintimes

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat

class TrainTimesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val config = BundledEmojiCompatConfig(this)
            EmojiCompat.init(config)
        } catch (_: Exception) {
            // Ignore
        }
    }
}
