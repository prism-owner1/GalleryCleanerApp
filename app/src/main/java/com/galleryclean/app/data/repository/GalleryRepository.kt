package com.galleryclean.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.galleryclean.app.data.model.PhotoItem
import com.galleryclean.app.utils.ImageSimilarityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryRepository(private val context: Context) {

    /** Load every image from MediaStore */
    suspend fun loadAllPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoItem>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection, projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id   = cursor.getLong(idCol)
                val uri  = ContentUris.withAppendedId(collection, id)
                val path = cursor.getString(pathCol) ?: ""
                val name = cursor.getString(nameCol) ?: ""
                val w    = cursor.getInt(widthCol)
                val h    = cursor.getInt(heightCol)

                val photo = PhotoItem(
                    id       = id,
                    uri      = uri,
                    path     = path,
                    name     = name,
                    size     = cursor.getLong(sizeCol),
                    dateAdded = cursor.getLong(dateCol),
                    width    = w,
                    height   = h,
                    mimeType = cursor.getString(mimeCol) ?: "image/jpeg"
                )

                // Tag promo photos
                val (isPromo, reason) = ImageSimilarityEngine.detectPromo(photo)
                photo.isPromo     = isPromo
                photo.promoReason = reason

                photos.add(photo)
            }
        }
        photos
    }

    /** Delete a list of photos via MediaStore (handles Android 10+ scoped storage) */
    suspend fun deletePhotos(photos: List<PhotoItem>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        photos.forEach { photo ->
            try {
                val rows = context.contentResolver.delete(photo.uri, null, null)
                if (rows > 0) deleted++
            } catch (e: Exception) {
                // SecurityException on Android 10+ — caller needs to catch and
                // launch the IntentSender from the exception (handled in ViewModel)
            }
        }
        deleted
    }

    /** Format bytes to human-readable string */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000         -> "%.0f KB".format(bytes / 1_000.0)
        else                   -> "$bytes B"
    }
}
