package com.galleryclean.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.LruCache
import com.galleryclean.app.data.model.PhotoItem
import com.galleryclean.app.data.model.SimilarGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Perceptual hash (pHash) based similarity engine.
 *
 * Algorithm:
 *  1. Resize to 32×32 greyscale
 *  2. Apply DCT on each row then each column (2D DCT)
 *  3. Take top-left 8×8 = 64 bits of DCT coefficients
 *  4. Threshold bits by mean  →  64-bit fingerprint
 *  5. Hamming distance between two hashes → similarity %
 */
object ImageSimilarityEngine {

    private const val HASH_SIZE = 8          // 8×8 = 64 bit hash
    private const val RESIZE = 32
    private val hashCache = LruCache<Long, LongArray>(500)

    // ── Public API ──────────────────────────────────────────────────────────

    suspend fun groupSimilarPhotos(
        context: Context,
        photos: List<PhotoItem>,
        thresholdPercent: Int = 80,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<SimilarGroup> = withContext(Dispatchers.Default) {

        val hashes = mutableMapOf<Long, LongArray>()

        // 1. Compute hashes
        photos.forEachIndexed { index, photo ->
            withContext(Dispatchers.IO) {
                computeHash(context, photo)
            }?.let { hashes[photo.id] = it }
            onProgress(index + 1, photos.size)
        }

        // 2. Build similarity groups using union-find
        val photoList = photos.filter { hashes.containsKey(it.id) }
        val parent = IntArray(photoList.size) { it }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var cur = x
            while (parent[cur] != cur) { val next = parent[cur]; parent[cur] = r; cur = next }
            return r
        }

        val pairSimilarity = mutableMapOf<Pair<Int, Int>, Float>()

        for (i in photoList.indices) {
            for (j in i + 1 until photoList.size) {
                val hi = hashes[photoList[i].id] ?: continue
                val hj = hashes[photoList[j].id] ?: continue
                val sim = hammingSimilarity(hi, hj)
                if (sim >= thresholdPercent / 100f) {
                    val ri = find(i); val rj = find(j)
                    if (ri != rj) parent[ri] = rj
                    pairSimilarity[Pair(minOf(i, j), maxOf(i, j))] = sim
                }
            }
        }

        // 3. Collect groups
        val groups = mutableMapOf<Int, MutableList<Int>>()
        photoList.indices.forEach { i ->
            val root = find(i)
            groups.getOrPut(root) { mutableListOf() }.add(i)
        }

        var groupId = 0
        groups.values
            .filter { it.size >= 2 }
            .map { indices ->
                val groupPhotos = indices.map { photoList[it] }.toMutableList()
                val maxSim = indices.flatMap { i ->
                    indices.mapNotNull { j ->
                        if (i < j) pairSimilarity[Pair(i, j)] else null
                    }
                }.maxOrNull() ?: thresholdPercent / 100f
                SimilarGroup(groupId++, groupPhotos, maxSim * 100f)
            }
            .sortedByDescending { it.similarity }
    }

    /** Returns similarity 0.0–1.0 between two photos */
    suspend fun computeSimilarity(context: Context, a: PhotoItem, b: PhotoItem): Float =
        withContext(Dispatchers.IO) {
            val ha = computeHash(context, a) ?: return@withContext 0f
            val hb = computeHash(context, b) ?: return@withContext 0f
            hammingSimilarity(ha, hb)
        }

    // ── Promotional / Unwanted Photo Detection ──────────────────────────────

    fun detectPromo(photo: PhotoItem): Pair<Boolean, String> {
        val name = photo.name.lowercase()
        val path = photo.path.lowercase()

        // Filename patterns common in promo screenshots
        val promoKeywords = listOf(
            "screenshot", "whatsapp", "forward", "promo", "offer",
            "discount", "sale", "deal", "ad_", "_ad", "banner",
            "coupon", "voucher", "congratulation", "winner", "prize",
            "forward", "share", "viral", "broadcast", "img-", "received"
        )

        // Folder patterns
        val promoFolders = listOf(
            "whatsapp", "telegram", "received", "downloads",
            "screenshots", "bluetooth"
        )

        val matchedKeyword = promoKeywords.firstOrNull { name.contains(it) }
        val matchedFolder = promoFolders.firstOrNull { path.contains(it) }

        // Very tall images are often promotional banners
        val isLongBanner = photo.height > 0 && photo.width > 0 &&
                (photo.height.toFloat() / photo.width) > 2.5f

        return when {
            matchedKeyword != null -> Pair(true, "Filename: '$matchedKeyword'")
            matchedFolder != null  -> Pair(true, "From: $matchedFolder folder")
            isLongBanner           -> Pair(true, "Long banner image")
            else                   -> Pair(false, "")
        }
    }

    // ── pHash internals ─────────────────────────────────────────────────────

    private fun computeHash(context: Context, photo: PhotoItem): LongArray? {
        hashCache.get(photo.id)?.let { return it }

        return try {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(photo.width, photo.height, RESIZE * 4)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = context.contentResolver.openInputStream(photo.uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val grey = toGreyscale(bmp, RESIZE)
            bmp.recycle()

            val dct = applyDCT(grey)
            val hash = buildHash(dct)
            hashCache.put(photo.id, hash)
            hash
        } catch (e: Exception) {
            null
        }
    }

    private fun toGreyscale(src: Bitmap, size: Int): DoubleArray {
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        scaled.recycle()
        return DoubleArray(size * size) { i ->
            val p = pixels[i]
            (Color.red(p) * 0.299 + Color.green(p) * 0.587 + Color.blue(p) * 0.114)
        }
    }

    private fun applyDCT(values: DoubleArray): DoubleArray {
        val n = RESIZE
        val result = DoubleArray(n * n)
        // Row DCT
        val rowDCT = DoubleArray(n * n)
        for (y in 0 until n) {
            for (u in 0 until n) {
                var sum = 0.0
                for (x in 0 until n) sum += values[y * n + x] * Math.cos((2 * x + 1) * u * Math.PI / (2 * n))
                rowDCT[y * n + u] = sum * (if (u == 0) 1.0 / Math.sqrt(2.0) else 1.0)
            }
        }
        // Col DCT
        for (v in 0 until n) {
            for (u in 0 until n) {
                var sum = 0.0
                for (y in 0 until n) sum += rowDCT[y * n + u] * Math.cos((2 * y + 1) * v * Math.PI / (2 * n))
                result[v * n + u] = sum * (if (v == 0) 1.0 / Math.sqrt(2.0) else 1.0)
            }
        }
        return result
    }

    private fun buildHash(dct: DoubleArray): LongArray {
        val top = DoubleArray(HASH_SIZE * HASH_SIZE) { i ->
            val row = i / HASH_SIZE; val col = i % HASH_SIZE
            dct[row * RESIZE + col]
        }
        val mean = top.average()
        // Pack 64 bits into a single Long
        var hash = 0L
        top.forEachIndexed { i, v -> if (v > mean) hash = hash or (1L shl i) }
        return longArrayOf(hash)
    }

    private fun hammingSimilarity(a: LongArray, b: LongArray): Float {
        val diff = java.lang.Long.bitCount(a[0] xor b[0])
        return 1f - diff / 64f
    }

    private fun calculateInSampleSize(w: Int, h: Int, reqSize: Int): Int {
        var size = 1
        if (h > reqSize || w > reqSize) {
            val half = maxOf(h, w) / 2
            while (half / size > reqSize) size *= 2
        }
        return size
    }
}
