package org.cursiveerror.securenote

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.test.*

class FullSyncAuditTest {

    @Test
    fun testCryptoRoundTrip() {
        val key = CryptoManager.getKeyBase64()
        val plainText = "Hello SecureNote 1.0.3! Secret cryptographic payload."

        val (iv, cipherText) = CryptoManager.encrypt(plainText)
        val decrypted = CryptoManager.decrypt(cipherText, iv)

        assertEquals(plainText, decrypted, "Decrypted text must match original plaintext")
    }

    @Test
    fun testMergeTwoNotesScenarios() {
        val now = System.currentTimeMillis()

        // 1. Normal edit: newer timestamp wins
        val note1Old = Note("id1", "Title", "Old Content", now - 1000, false)
        val note1New = Note("id1", "Title", "New Content", now, false)
        assertEquals(note1New, mergeTwoNotes(note1Old, note1New))
        assertEquals(note1New, mergeTwoNotes(note1New, note1Old))

        // 2. Client deletion (title & content unchanged): deletion wins
        val noteActive = Note("id2", "Delete Me", "Some body", now - 5000, false)
        val noteDeleted = Note("id2", "Delete Me", "Some body", now, true)
        val merged1 = mergeTwoNotes(noteActive, noteDeleted)
        assertTrue(merged1.isDeleted, "Deletion from incoming must win")

        // 3. Server deletion (title & content unchanged): deletion wins
        val merged2 = mergeTwoNotes(noteDeleted, noteActive)
        assertTrue(merged2.isDeleted, "Deletion on existing must win")

        // 4. Clock skew on deletion (deletion happened even if remote timestamp is slightly higher)
        val noteActiveSkew = Note("id3", "Same Title", "Same text", now + 2000, false)
        val noteDeletedLocal = Note("id3", "Same Title", "Same text", now, true)
        val mergedSkew = mergeTwoNotes(noteActiveSkew, noteDeletedLocal)
        assertTrue(mergedSkew.isDeleted, "Deletion must win when content matches regardless of skew")

        // 5. Explicit content edit after deletion: edit wins
        val noteDeletedOlder = Note("id4", "Old Title", "Old Text", now - 2000, true)
        val noteEditedNewer = Note("id4", "Resurrected Title", "Edited Text", now, false)
        val mergedResurrect = mergeTwoNotes(noteDeletedOlder, noteEditedNewer)
        assertFalse(mergedResurrect.isDeleted, "Intentional edit with new content after deletion must win")
        assertEquals("Resurrected Title", mergedResurrect.title)
    }

    @Test
    fun testLiveHttpSyncServerEndToEnd() {
        val testPort = 8099
        val serverNotes = mutableListOf<Note>()
        var isPairedResult = false

        val server = SyncServer(
            httpPort = testPort,
            udpPort = 8899,
            onSync = { incomingNotes ->
                val noteMap = LinkedHashMap<String, Note>()
                for (n in serverNotes) noteMap[n.id] = n
                for (inc in incomingNotes) {
                    val existing = noteMap[inc.id]
                    if (existing == null) {
                        noteMap[inc.id] = inc
                    } else {
                        noteMap[inc.id] = mergeTwoNotes(existing, inc)
                    }
                }
                val merged = noteMap.values.sortedByDescending { it.timestamp }
                serverNotes.clear()
                serverNotes.addAll(merged)
                merged
            },
            getCurrentNotes = { serverNotes.toList() },
            onPairStatusChanged = { paired ->
                isPairedResult = paired
            }
        )

        server.start()
        try {
            val key = CryptoManager.getKeyBase64()

            // Step 1: Test /pair endpoint with encrypted payload
            val pairPayload = """{"device":"Android Test","timestamp":${System.currentTimeMillis()}}"""
            val (pairIv, pairData) = CryptoManager.encrypt(pairPayload)
            val pairBody = """{"iv":"$pairIv","data":"$pairData"}"""

            val pairConn = (URL("http://127.0.0.1:$testPort/pair").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            pairConn.outputStream.use { it.write(pairBody.toByteArray()) }
            assertEquals(200, pairConn.responseCode, "Pair endpoint must return 200 OK")
            assertTrue(isPairedResult, "Server should notify paired = true")

            // Step 2: Test /sync - Client pushes Note A and Note B
            val noteA = Note("A", "Title A", "Content A", System.currentTimeMillis() - 100, false)
            val noteB = Note("B", "Title B", "Content B", System.currentTimeMillis() - 50, false)

            val clientSyncJson = JSONObject().apply {
                val arr = JSONArray()
                arr.put(JSONObject().apply {
                    put("id", noteA.id)
                    put("title", noteA.title)
                    put("content", noteA.content)
                    put("timestamp", noteA.timestamp)
                    put("isDeleted", noteA.isDeleted)
                })
                arr.put(JSONObject().apply {
                    put("id", noteB.id)
                    put("title", noteB.title)
                    put("content", noteB.content)
                    put("timestamp", noteB.timestamp)
                    put("isDeleted", noteB.isDeleted)
                })
                put("clientNotes", arr)
            }.toString()

            val (syncIv, syncData) = CryptoManager.encrypt(clientSyncJson)
            val syncBody = """{"iv":"$syncIv","data":"$syncData"}"""

            val syncConn = (URL("http://127.0.0.1:$testPort/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            syncConn.outputStream.use { it.write(syncBody.toByteArray()) }
            assertEquals(200, syncConn.responseCode, "Sync endpoint must return 200 OK")

            val syncRespText = syncConn.inputStream.bufferedReader().readText()
            val syncRespEnvelope = JSONObject(syncRespText)
            val decryptedResp = CryptoManager.decrypt(
                syncRespEnvelope.getString("data"),
                syncRespEnvelope.getString("iv")
            )
            val respJson = JSONObject(decryptedResp)
            val notesArr = respJson.getJSONArray("notes")
            assertEquals(2, notesArr.length(), "Server must return 2 notes")

            // Step 3: Server creates Note C and marks Note A as deleted on Server
            val noteC = Note("C", "Title C", "Content C", System.currentTimeMillis(), false)
            val noteADeleted = noteA.copy(isDeleted = true, timestamp = System.currentTimeMillis())
            serverNotes.add(noteC)
            val idxA = serverNotes.indexOfFirst { it.id == "A" }
            serverNotes[idxA] = noteADeleted

            // Step 4: Client syncs again (Client has not deleted Note A locally yet)
            val syncConn2 = (URL("http://127.0.0.1:$testPort/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            syncConn2.outputStream.use { it.write(syncBody.toByteArray()) }
            assertEquals(200, syncConn2.responseCode)

            val syncRespText2 = syncConn2.inputStream.bufferedReader().readText()
            val env2 = JSONObject(syncRespText2)
            val dec2 = CryptoManager.decrypt(env2.getString("data"), env2.getString("iv"))
            val respJson2 = JSONObject(dec2)
            val notesArr2 = respJson2.getJSONArray("notes")

            val returnedNoteMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until notesArr2.length()) {
                val o = notesArr2.getJSONObject(i)
                returnedNoteMap[o.getString("id")] = o
            }

            assertTrue(returnedNoteMap.containsKey("A"), "Note A must be returned")
            assertTrue(returnedNoteMap["A"]!!.getBoolean("isDeleted"), "Note A must be isDeleted = true")
            assertTrue(returnedNoteMap.containsKey("C"), "Note C from server must be present")
            assertFalse(returnedNoteMap["C"]!!.getBoolean("isDeleted"), "Note C must not be deleted")

            // Step 5: Test that UNENCRYPTED /sync request is strictly rejected with 401
            val unencryptedSyncConn = (URL("http://127.0.0.1:$testPort/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            unencryptedSyncConn.outputStream.use { it.write("""{"clientNotes":[]}""".toByteArray()) }
            assertEquals(401, unencryptedSyncConn.responseCode, "Server MUST reject unencrypted plaintext sync requests with 401")

            // Step 6: Test that UNENCRYPTED /pair request is strictly rejected with 401
            val unencryptedPairConn = (URL("http://127.0.0.1:$testPort/pair").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            unencryptedPairConn.outputStream.use { it.write("""{"device":"phone"}""".toByteArray()) }
            assertEquals(401, unencryptedPairConn.responseCode, "Server MUST reject unencrypted plaintext pair requests with 401")

            // Step 7: Test /unpair endpoint - should unpair AND regenerate key
            val unpairConn = (URL("http://127.0.0.1:$testPort/unpair").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
            }
            assertEquals(200, unpairConn.responseCode)
            assertFalse(isPairedResult, "Server should notify paired = false after unpair")

            // Step 8: Client attempts to sync using the OLD key after unpair -> MUST FAIL WITH 401
            val oldKeySyncConn = (URL("http://127.0.0.1:$testPort/sync").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            oldKeySyncConn.outputStream.use { it.write(syncBody.toByteArray()) }
            assertEquals(401, oldKeySyncConn.responseCode, "Server MUST reject requests using old key with 401 after unpair")

        } finally {
            server.stop()
        }
    }
}
