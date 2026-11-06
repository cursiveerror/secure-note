package org.cursiveerror.securenote

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object NoteRepository {
    private val storageDir = File(System.getProperty("user.home"), ".securenote").apply {
        if (!exists()) mkdirs()
    }
    private val storageFile = File(storageDir, "notes_pc.json")

    @Synchronized
    fun loadNotes(): List<Note> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val text = storageFile.readText(Charsets.UTF_8).trim()
            if (text.isEmpty()) return emptyList()
            val array = JSONArray(text)
            val result = mutableListOf<Note>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    Note(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isDeleted = obj.optBoolean("isDeleted", false)
                    )
                )
            }
            result
        } catch (e: Exception) {
            println("Error loading notes: ${e.message}")
            emptyList()
        }
    }

    @Synchronized
    fun saveNotes(notes: List<Note>) {
        try {
            val array = JSONArray()
            for (n in notes) {
                val obj = JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("timestamp", n.timestamp)
                    put("isDeleted", n.isDeleted)
                }
                array.put(obj)
            }
            storageFile.writeText(array.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            println("Error saving notes: ${e.message}")
        }
    }
}
