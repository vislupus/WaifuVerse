package com.vislupus.waifuverse.sprite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Loads character sprites and backgrounds from the assets folder.
 *
 * A sprite is ONE static body image plus small mouth patches that are drawn
 * on top of it at a fixed rectangle. Because the body never changes, the
 * character cannot jitter while talking. See tools/prepare_sprites.py.
 */
class SpriteLoader(context: Context) {

    class Sprite(
        val width: Int,
        val height: Int,
        val body: ImageBitmap,
        /** Index 0 = closed mouth, last = wide open. */
        val mouths: List<ImageBitmap>,
        /** Position of the mouth patches inside the body image (pixels). */
        val mouthRect: IntRect,
    )

    private val assets = context.applicationContext.assets
    private val spriteCache = HashMap<String, Sprite>()
    private val backgroundCache = HashMap<String, ImageBitmap>()

    suspend fun sprite(waifuId: String): Sprite? = withContext(Dispatchers.IO) {
        synchronized(spriteCache) { spriteCache[waifuId] }?.let { return@withContext it }
        val loaded = try {
            loadSprite(waifuId)
        } catch (_: Exception) {
            null
        }
        if (loaded != null) synchronized(spriteCache) { spriteCache[waifuId] = loaded }
        loaded
    }

    suspend fun background(file: String): ImageBitmap? = withContext(Dispatchers.IO) {
        synchronized(backgroundCache) { backgroundCache[file] }?.let { return@withContext it }
        val bmp = try {
            decode("backgrounds/$file")
        } catch (_: Exception) {
            null
        }
        if (bmp != null) synchronized(backgroundCache) { backgroundCache[file] = bmp }
        bmp
    }

    private fun loadSprite(waifuId: String): Sprite? {
        val dir = "characters/$waifuId"
        val meta = JSONObject(assets.open("$dir/sprite.json").bufferedReader().use { it.readText() })
        val mouth = meta.getJSONObject("mouth")
        val frames = meta.optInt("frames", 4)
        val body = decode("$dir/body.webp") ?: return null
        val mouths = (0 until frames).mapNotNull { decode("$dir/mouth_$it.webp") }
        if (mouths.isEmpty()) return null
        val x = mouth.getInt("x")
        val y = mouth.getInt("y")
        return Sprite(
            width = meta.optInt("width", body.width),
            height = meta.optInt("height", body.height),
            body = body,
            mouths = mouths,
            mouthRect = IntRect(x, y, x + mouth.getInt("width"), y + mouth.getInt("height")),
        )
    }

    private fun decode(path: String): ImageBitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        return bmp.asImageBitmap()
    }
}
