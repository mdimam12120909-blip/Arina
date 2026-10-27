package com.arina.assistant.device

import android.content.ContentUris
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

/** Finds the user's own photos and files so they can be shown on screen. */
object MediaFinder {

    /** Newest photos first. [album] filters by album (folder) name when not blank. Returns content:// addresses. */
    fun recentPhotos(ctx: Context, album: String, count: Int): List<String> {
        val out = ArrayList<String>()
        val sel = if (album.isBlank()) null else "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?"
        val args = if (album.isBlank()) null else arrayOf("%${album.trim()}%")
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            sel, args, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < count) {
                out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)).toString()
            }
        }
        return out
    }

    private fun roots(): List<File> = listOf(
        Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_MUSIC
    ).map { Environment.getExternalStoragePublicDirectory(it) }.filter { it.isDirectory }

    private fun search(dir: File, needle: String, depth: Int): File? {
        val list = dir.listFiles() ?: return null
        list.filter { it.isFile && it.name.contains(needle, ignoreCase = true) }
            .maxByOrNull { it.lastModified() }?.let { return it }
        if (depth > 0) for (d in list.filter { it.isDirectory && !it.name.startsWith(".") }) {
            search(d, needle, depth - 1)?.let { return it }
        }
        return null
    }

    fun findFile(name: String): File? {
        val n = name.trim()
        if (n.isEmpty()) return null
        for (r in roots()) search(r, n, 3)?.let { return it }
        return null
    }

    fun pdfPages(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { PdfRenderer(it).use { r -> r.pageCount } }
    } catch (e: Exception) {
        0
    }
}
