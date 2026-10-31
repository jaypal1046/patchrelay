package dev.patchrelay.screens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenUpdatesTest {
    private lateinit var context: Context
    private lateinit var keys: KeyPair
    private val host = "b".repeat(64)
    private val downloads = mutableMapOf<String, ByteArray>()
    private var requests = 0
    private val manifestUrl = "https://updates.example/patch.json"
    private val scriptUrl = "https://updates.example/screens.js"
    private val bundled = "bundled"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("patchrelay-screens", 0).edit().clear().commit()
        keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private fun key(pair: KeyPair = keys) = Base64.getEncoder().encodeToString(pair.public.encoded)
    private fun updates(trusted: Set<String> = setOf(key())) = ScreenUpdates(context, "base", trusted, setOf("checkout"), host) { url, limit ->
        requests++
        val bytes = downloads[url] ?: throw IOException("Interrupted/offline download")
        require(bytes.size <= limit)
        bytes
    }
    private fun offer(version: Any, pair: KeyPair = keys, script: ByteArray = "program-$version".toByteArray()) {
        val digest = MessageDigest.getInstance("SHA-256").digest(script).joinToString("") { "%02x".format(it.toInt() and 255) }
        val payload = JSONObject().put("schema", 3).put("screenApi", 2).put("runtime", "kotlin-screen-js")
            .put("appId", context.packageName).put("baseVersion", "base").put("hostSha256", host)
            .put("version", version).put("screenIds", JSONArray().put("checkout")).put("sha256", digest)
            .put("artifactUrl", scriptUrl).toString().toByteArray()
        val signer = Signature.getInstance("SHA256withRSA").apply { initSign(pair.private); update(payload) }
        downloads[manifestUrl] = JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signer.sign())).toString().toByteArray()
        downloads[scriptUrl] = script
    }
    private fun file(version: Int, suffix: String = "js") = File(context.noBackupFilesDir,
        "screen-patches/${UUID.nameUUIDFromBytes("base:$host".toByteArray())}/$version.$suffix")
    private fun <T> worker(block: () -> T): T {
        val task = FutureTask(block)
        Thread(task).start()
        try { return task.get(15, java.util.concurrent.TimeUnit.SECONDS) }
        catch (failure: ExecutionException) { throw failure.cause!! }
    }
    private fun rejected(block: () -> Unit) {
        try { worker(block); fail("Accepted invalid update") }
        catch (_: IOException) {} catch (_: IllegalArgumentException) {} catch (_: SecurityException) {}
    }

    @Test fun activatesOnlyNextSessionAndRejectsReplay() {
        val first = updates()
        assertEquals(0L, worker { first.start(bundled).version })
        offer(1)
        assertTrue(worker { first.stage(manifestUrl) })
        assertEquals(0L, first.activeVersion())
        val next = updates()
        assertEquals(1L, worker { next.start(bundled).version })
        worker { next.markHealthy() }
        assertFalse(worker { next.stage(manifestUrl) })
        offer(0)
        rejected { next.stage(manifestUrl) }
    }

    @Test fun repeatedUnhealthyLaunchesFallBackWithoutReactivatingBadVersion() {
        val updater = updates()
        offer(1); worker { updater.stage(manifestUrl) }
        assertEquals(1L, worker { updates().start(bundled).version })
        assertEquals(0L, worker { updates().start(bundled).version })
        assertEquals(0L, worker { updates().start(bundled).version })
        assertFalse(worker { updates().stage(manifestUrl) })
    }

    @Test fun eventFailureRestoresHealthyPreviousThenFallsBackIfItAlsoFails() {
        val first = updates()
        offer(1); worker { first.stage(manifestUrl); first.start(bundled); first.markHealthy() }
        offer(2); worker { first.stage(manifestUrl) }
        val next = updates()
        assertEquals(2L, worker { next.start(bundled).version })
        assertEquals(1L, worker { next.reject(bundled, IOException("Event failed")).version })
        assertEquals(0L, worker { next.reject(bundled, IOException("Fallback failed")).version })
        assertFalse(worker { next.stage(manifestUrl) })
    }

    @Test fun corruptSavedProgramCannotExecute() {
        val updater = updates()
        offer(1); worker { updater.stage(manifestUrl) }
        file(1).writeText("tampered")
        assertEquals(0L, worker { updates().start(bundled).version })
    }

    @Test fun interruptedAtomicWriteRestoresBackupBeforeVerification() {
        val updater = updates()
        offer(1); worker { updater.stage(manifestUrl) }
        val file = file(1)
        assertTrue(file.renameTo(File(file.path + ".bak")))
        file.writeText("partial")
        assertEquals("program-1", worker { updates().start(bundled).script })
    }

    @Test fun partialOfflineAndAlteredDownloadsDoNotReplacePendingPatch() {
        val updater = updates()
        offer(1); worker { updater.stage(manifestUrl) }
        offer(2); downloads.remove(scriptUrl)
        rejected { updater.stage(manifestUrl) }
        assertEquals(1L, updater.pendingVersion())
        downloads[scriptUrl] = "changed".toByteArray()
        rejected { updater.stage(manifestUrl) }
        downloads.remove(manifestUrl)
        rejected { updater.stage(manifestUrl) }
        assertEquals(1L, worker { updates().start(bundled).version })
    }

    @Test fun storageFailureDoesNotAdvanceReplayFloorOrPendingVersion() {
        val updater = updates()
        offer(1)
        // A file obstructs the patch directory without filling the developer's disk.
        val directory = file(1).parentFile!!
        assertTrue(directory.delete())
        directory.writeText("obstructed")
        rejected { updater.stage(manifestUrl) }
        assertEquals(0L, updater.pendingVersion())
        assertTrue(directory.delete())
        assertTrue(directory.mkdir())
        assertTrue(worker { updater.stage(manifestUrl) })
    }

    @Test fun hostDisablePersistsAndKeepsReplayProtection() {
        val updater = updates()
        offer(1); worker { updater.stage(manifestUrl); updater.start(bundled); updater.markHealthy() }
        offer(2); worker { updater.stage(manifestUrl) }
        assertEquals(0L, worker { updater.disable(bundled).version })
        val count = requests
        assertFalse(worker { updater.stage(manifestUrl) })
        assertEquals(count, requests)
        val next = updates()
        assertEquals(0L, worker { next.start(bundled).version })
        worker { next.enable() }
        assertFalse(worker { next.stage(manifestUrl) })
        offer(3)
        assertTrue(worker { next.stage(manifestUrl) })
    }

    @Test fun rotatesTrustAndReverifiesSavedPatchAfterKeyRevocation() {
        val newKeys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val updater = updates(setOf(key(), key(newKeys)))
        offer(1, newKeys)
        assertTrue(worker { updater.stage(manifestUrl) })
        assertEquals(1L, worker { updates(setOf(key(newKeys))).start(bundled).version })
        // A key which did not sign the saved program cannot authorize it on a later boot.
        worker { context.getSharedPreferences("patchrelay-screens", 0).edit().putBoolean("booting", false).commit() }
        assertEquals(0L, worker { updates(setOf(key())).start(bundled).version })
        offer(2, keys)
        rejected { updates(setOf(key(newKeys))).stage(manifestUrl) }
    }

    @Test fun rejectsFractionalStringAndOverflowVersionsBeforeDownload() {
        val updater = updates()
        for (version in listOf(1.5, "2", "9223372036854775808")) {
            offer(version)
            val count = requests
            rejected { updater.stage(manifestUrl) }
            assertEquals(count + 1, requests)
        }
    }
}
