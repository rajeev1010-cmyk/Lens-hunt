package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CharactersRepository(application)

    private val _characters = MutableStateFlow<List<CharacterEntry>>(emptyList())
    val characters = _characters.asStateFlow()

    private val _matchedCharacter = MutableStateFlow<CharacterEntry?>(null)
    val matchedCharacter = _matchedCharacter.asStateFlow()

    private val _matchSimilarity = MutableStateFlow<Int?>(null)
    val matchSimilarity = _matchSimilarity.asStateFlow()

    private val _faceBounds = MutableStateFlow<Rect?>(null)
    val faceBounds = _faceBounds.asStateFlow()

    private val _isMatching = MutableStateFlow(false)
    val isMatching = _isMatching.asStateFlow()

    private val _matchError = MutableStateFlow<String?>(null)
    val matchError = _matchError.asStateFlow()

    init {
        viewModelScope.launch {
            _characters.value = repository.getCharacters()
        }
    }

    fun updateFaceBounds(bounds: Rect?) {
        _faceBounds.value = bounds
        if (bounds == null) {
            _matchedCharacter.value = null
            _matchSimilarity.value = null
        }
    }

    /**
     * Matches entirely on-device against the 5 axes FaceAxesExtractor can
     * currently measure reliably (see FaceAxesExtractor.RELIABLE_AXES) --
     * no network call, no face image ever leaves the device.
     */
    fun matchFace(axes: MeasuredAxes) {
        if (_isMatching.value) return
        if (_characters.value.isEmpty()) return

        _isMatching.value = true
        val result = CharacterMatcher.findBestMatch(axes, _characters.value)
        if (result != null) {
            _matchedCharacter.value = result.character
            _matchSimilarity.value = result.similarityPercent
        }
        viewModelScope.launch {
            // Short debounce so the overlay doesn't flicker between
            // characters on every single frame.
            delay(400)
            _isMatching.value = false
        }
    }

    /** Matches a single gallery-picked photo instead of a live camera frame. */
    fun matchPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _matchError.value = null
            val face = try {
                PhotoFaceDetector.detectFirstFace(bitmap)
            } catch (e: Exception) {
                null
            }
            if (face == null) {
                _matchError.value = "No face found in that photo."
                return@launch
            }
            val axes = FaceAxesExtractor.extract(face)
            if (axes == null) {
                _matchError.value = "Couldn't read enough detail from that face."
                return@launch
            }
            val result = CharacterMatcher.findBestMatch(axes, _characters.value)
            if (result != null) {
                _matchedCharacter.value = result.character
                _matchSimilarity.value = result.similarityPercent
            }
        }
    }

    fun clearMatchError() {
        _matchError.value = null
    }
}
