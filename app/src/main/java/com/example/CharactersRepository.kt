package com.example

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class CharacterEntry(
    val id: String,
    val name: String,
    val series: String
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
