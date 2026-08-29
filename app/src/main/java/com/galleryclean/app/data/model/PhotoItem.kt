package com.galleryclean.app.data.model

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,           // bytes
    val dateAdded: Long,      // epoch seconds
    val width: Int,
    val height: Int,
    val mimeType: String,
    var isSelected: Boolean = false,
    var similarityScore: Float = 0f,   // 0.0 – 1.0
    var isPromo: Boolean = false,
    var promoReason: String = ""
)

data class SimilarGroup(
    val id: Int,
    val photos: MutableList<PhotoItem>,
    val similarity: Float        // highest similarity in group (0-100 %)
)
