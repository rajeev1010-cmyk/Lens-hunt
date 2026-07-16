package com.example

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class GeminiService {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-pro",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun findMatchingCharacter(faceBitmap: Bitmap, characters: List<CharacterEntry>): String? {
        val prompt = buildString {
            append("Here is an image of a person's face. Analyze it visually and match it with the closest anime character from the following JSON list based on their visual traits. ")
            append("Respond ONLY with the 'id' of the best matching character.\n\n")
            append("[\n")
            characters.forEach { char ->
                append("  {\"id\": \"${char.id}\", \"name\": \"${char.name}\", \"series\": \"${char.series}\"},\n")
            }
            append("]")
        }

        val inputContent = content {
            image(faceBitmap)
            text(prompt)
        }

        return try {
            val response = generativeModel.generateContent(inputContent)
            val text = response.text?.trim() ?: ""
            // Extract id which might be enclosed in markdown or quotes
            characters.find { text.contains(it.id) }?.id ?: text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
