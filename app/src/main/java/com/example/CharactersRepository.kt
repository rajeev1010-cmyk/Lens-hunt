package com.example

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class VisualAxes(
    val faceLength: Double = 0.5,
    val jawSharpness: Double = 0.5,
    val eyeNarrowness: Double = 0.5,
    val browWeight: Double = 0.5,
    val hairDarkness: Double = 0.5,
    val hairVolume: Double = 0.5,
    val expressionNeutrality: Double = 0.5,
    val symmetry: Double = 0.6,
    val contrast: Double = 0.5,
    val angularity: Double = 0.5,
    val glasses: Double = 0.0,
    val warmth: Double = 0.5
)

@JsonClass(generateAdapter = true)
data class CharacterEntry(
    val id: String,
    val name: String,
    val series: String,
    val profile: VisualAxes = VisualAxes()
)

class CharactersRepository(private val context: Context) {
    suspend fun getCharacters(): List<CharacterEntry> = withContext(Dispatchers.IO) {
        val json = context.assets.open("characters.json").bufferedReader().use { it.readText() }
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter<List<CharacterEntry>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, CharacterEntry::class.java)
        )
        adapter.fromJson(json) ?: emptyList()
    }
}
