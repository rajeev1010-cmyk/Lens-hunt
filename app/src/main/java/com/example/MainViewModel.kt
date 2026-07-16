package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CharactersRepository(application)
    private val geminiService = GeminiService()

    private val _characters = MutableStateFlow<List<CharacterEntry>>(emptyList())
    val characters = _characters.asStateFlow()

    private val _matchedCharacter = MutableStateFlow<CharacterEntry?>(null)
    val matchedCharacter = _matchedCharacter.asStateFlow()

    private val _faceBounds = MutableStateFlow<Rect?>(null)
    val faceBounds = _faceBounds.asStateFlow()

    private val _isMatching = MutableStateFlow(false)
    val isMatching = _isMatching.asStateFlow()

    init {
        viewModelScope.launch {
            _characters.value = repository.getCharacters()
        }
    }

    fun updateFaceBounds(bounds: Rect?) {
        _faceBounds.value = bounds
        if (bounds == null) {
            _matchedCharacter.value = null
        }
    }

    fun matchFace(faceBitmap: Bitmap) {
        if (_isMatching.value) return
        if (_characters.value.isEmpty()) return

        _isMatching.value = true
        viewModelScope.launch {
            val matchedId = geminiService.findMatchingCharacter(faceBitmap, _characters.value)
            if (matchedId != null) {
                val match = _characters.value.find { it.id == matchedId }
                if (match != null) {
                    _matchedCharacter.value = match
                }
            }
            // Add a small delay before next matching to avoid spamming
            kotlinx.coroutines.delay(2000)
            _isMatching.value = false
        }
    }
}
