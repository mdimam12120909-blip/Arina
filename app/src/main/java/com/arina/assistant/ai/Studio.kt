package com.arina.assistant.ai

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size

/** Generate Studio: everything Entrion makes lives in the phone's own Entrion folders, so it survives deleting the app. */
object Studio {
    enum class Kind(val title: String) { Photos("Photos"), Videos("Videos"), Music("Music") }
    class Item(val uri: Uri, val name: String, val mime: String, val kind: Kind)

    /** Creates the Entrion folders in the phone's file manager (best effort; Android may only allow it on first save). */
    fun ensureFolders(ctx: Context) {
        runCatching {
            listOf(Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_DOCUMENTS).forEach {
                java.io.File(Environment.getExternalStoragePublicDirectory(it), "Entrion").mkdirs()
            }
            java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Entrion/Generate Studio").mkdirs()
        }
    }

    private fun collection(kind: Kind): Uri = when (kind) {
        Kind.Photos -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        Kind.Videos -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Kind.Music -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    fun list(ctx: Context, kind: Kind, max: Int = 120): List<Item> {
        val out = ArrayList<Item>()
        runCatching {
            val col = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
            val like = if (Build.VERSION.SDK_INT >= 29) "%Entrion/%" else "%/Entrion/%"
            ctx.contentResolver.query(
                collection(kind), arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE),
                "$col LIKE ?", arrayOf(like), "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < max) out += Item(ContentUris.withAppendedId(collection(kind), c.getLong(0)), c.getString(1).orEmpty(), c.getString(2).orEmpty(), kind)
            }
        }
        return out
    }

    fun thumb(ctx: Context, item: Item): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.loadThumbnail(item.uri, Size(320, 320), null)
        else if (item.kind == Kind.Photos) ctx.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) }
        else null
    }.getOrNull()

    fun open(ctx: Context, item: Item) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
