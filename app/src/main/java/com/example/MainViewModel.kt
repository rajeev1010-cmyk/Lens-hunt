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

enum class UiStage { SCANNING, TOP_MATCHES, SHARE_CARD }

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

    private val _uiStage = MutableStateFlow(UiStage.SCANNING)
    val uiStage = _uiStage.asStateFlow()

    private val _topMatches = MutableStateFlow<List<MatchResult>>(emptyList())
    val topMatches = _topMatches.asStateFlow()

    private val _capturedSelfie = MutableStateFlow<Bitmap?>(null)
    val capturedSelfie = _capturedSelfie.asStateFlow()

    private val _selectedMatch = MutableStateFlow<MatchResult?>(null)
    val selectedMatch = _selectedMatch.asStateFlow()

    // Freshest axes from the live analyzer, kept even while the per-frame
    // debounce is active, so the shutter button always has something to work
    // with the instant it's pressed.
    private var lastMeasuredAxes: MeasuredAxes? = null

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
        lastMeasuredAxes = axes
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

    /** Shutter pressed: freezes the captured selfie and shows the top-5 candidates. */
    fun onSelfieCaptured(bitmap: Bitmap) {
        _capturedSelfie.value = bitmap
        val axes = lastMeasuredAxes
        if (axes == null) {
            _matchError.value = "No face detected yet -- hold steady and try again."
            return
        }
        val matches = CharacterMatcher.findTopMatches(axes, _characters.value, limit = 5)
        if (matches.isEmpty()) {
            _matchError.value = "No matches found."
            return
        }
        _topMatches.value = matches
        _uiStage.value = UiStage.TOP_MATCHES
    }

    fun selectMatch(result: MatchResult) {
        _selectedMatch.value = result
        _uiStage.value = UiStage.SHARE_CARD
    }

    fun dismissOverlay() {
        _uiStage.value = UiStage.SCANNING
        _topMatches.value = emptyList()
        _selectedMatch.value = null
    }
}
