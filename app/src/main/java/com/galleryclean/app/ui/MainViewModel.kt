package com.galleryclean.app.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.galleryclean.app.data.model.PhotoItem
import com.galleryclean.app.data.model.SimilarGroup
import com.galleryclean.app.data.repository.GalleryRepository
import com.galleryclean.app.utils.ImageSimilarityEngine
import kotlinx.coroutines.launch

enum class ScanState { IDLE, SCANNING, DONE, ERROR }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GalleryRepository(app)

    // ── Scan state ──────────────────────────────────────────────────────────
    private val _scanState   = MutableLiveData(ScanState.IDLE)
    val scanState: LiveData<ScanState> = _scanState

    private val _scanProgress = MutableLiveData(0 to 0)   // scanned to total
    val scanProgress: LiveData<Pair<Int, Int>> = _scanProgress

    // ── All photos ──────────────────────────────────────────────────────────
    private val _allPhotos = MutableLiveData<List<PhotoItem>>(emptyList())
    val allPhotos: LiveData<List<PhotoItem>> = _allPhotos

    // ── Similar groups ──────────────────────────────────────────────────────
    private val _similarGroups = MutableLiveData<List<SimilarGroup>>(emptyList())
    val similarGroups: LiveData<List<SimilarGroup>> = _similarGroups

    // ── Promo / unwanted photos ─────────────────────────────────────────────
    private val _promoPhotos = MutableLiveData<List<PhotoItem>>(emptyList())
    val promoPhotos: LiveData<List<PhotoItem>> = _promoPhotos

    // ── Stats ───────────────────────────────────────────────────────────────
    private val _totalSize  = MutableLiveData(0L)
    val totalSize: LiveData<Long> = _totalSize

    private val _savedSize  = MutableLiveData(0L)
    val savedSize: LiveData<Long> = _savedSize

    // ── Threshold (user-adjustable) ─────────────────────────────────────────
    var similarityThreshold = 80   // percent

    // ── Scan ────────────────────────────────────────────────────────────────
    fun startScan() {
        if (_scanState.value == ScanState.SCANNING) return
        viewModelScope.launch {
            _scanState.value = ScanState.SCANNING
            _scanProgress.value = 0 to 0
            try {
                val photos = repo.loadAllPhotos()
                _allPhotos.value = photos
                _totalSize.value = photos.sumOf { it.size }

                val promos = photos.filter { it.isPromo }
                _promoPhotos.value = promos

                val groups = ImageSimilarityEngine.groupSimilarPhotos(
                    getApplication(),
                    photos,
                    similarityThreshold
                ) { done, total -> _scanProgress.postValue(done to total) }

                _similarGroups.value = groups
                _scanState.value = ScanState.DONE
            } catch (e: Exception) {
                _scanState.value = ScanState.ERROR
            }
        }
    }

    // ── Delete ──────────────────────────────────────────────────────────────
    fun deleteSelected(photos: List<PhotoItem>, onRequiresIntent: (Any) -> Unit) {
        viewModelScope.launch {
            try {
                val deleted = repo.deletePhotos(photos)
                _savedSize.value = (_savedSize.value ?: 0L) + photos.take(deleted).sumOf { it.size }
                refreshAfterDelete(photos.take(deleted))
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // The exception may carry a RecoverableSecurityException with an IntentSender
                    val cause = e.cause
                    if (cause is android.app.RecoverableSecurityException) {
                        onRequiresIntent(cause.userAction.actionIntent.intentSender)
                    }
                }
            }
        }
    }

    private fun refreshAfterDelete(deleted: List<PhotoItem>) {
        val deletedIds = deleted.map { it.id }.toSet()
        _allPhotos.value = _allPhotos.value?.filterNot { it.id in deletedIds }
        _promoPhotos.value = _promoPhotos.value?.filterNot { it.id in deletedIds }
        _similarGroups.value = _similarGroups.value?.mapNotNull { group ->
            val remaining = group.photos.filterNot { it.id in deletedIds }.toMutableList()
            if (remaining.size >= 2) group.copy(photos = remaining) else null
        }
    }

    fun formatSize(bytes: Long) = repo.formatSize(bytes)
}
