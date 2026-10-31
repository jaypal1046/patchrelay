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
    private val requestedUrls = mutableListOf<String>()
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
        requestedUrls.add(url)
        val bytes = downloads[url] ?: throw IOException("Interrupted/offline download")
        require(bytes.size <= limit)
        bytes
    }
    private fun offer(version: Any, pair: KeyPair = keys, script: ByteArray = "program-$version".toByteArray(), base: String = "base", hash: String = host) {
        val digest = MessageDigest.getInstance("SHA-256").digest(script).joinToString("") { "%02x".format(it.toInt() and 255) }
        val payload = JSONObject().put("schema", 3).put("screenApi", 2).put("runtime", "kotlin-screen-js")
            .put("appId", context.packageName).put("baseVersion", base).put("hostSha256", hash)
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

    private fun signedCatalog(entries: JSONArray, appId: String = context.packageName, channel: String = "production"): ByteArray {
        val payload = JSONObject().put("schema", 1).put("kind", "patchrelay-catalog").put("appId", appId)
            .put("channel", channel).put("entries", entries).toString().toByteArray()
        val signer = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(payload) }
        return JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signer.sign())).toString().toByteArray()
    }

    private fun route(version: Long, base: String = "base", hash: String = host) = JSONObject()
        .put("baseVersion", base).put("hostSha256", hash).put("version", version).put("manifestUrl", manifestUrl)

    @Test fun catalogSelectsOnlyExactBaseAndSkipsIntermediatePatches() {
        val url = "https://updates.example/catalog.json"
        offer(7)
        downloads[url] = signedCatalog(JSONArray().put(route(99, hash = "c".repeat(64))).put(route(7)))
        val updater = updates()
        worker { updater.start(bundled) }
        assertTrue(worker { updater.stageCatalog(url) })
        assertEquals(listOf(url, manifestUrl, scriptUrl), requestedUrls)
        assertEquals(7L, worker { updater.pendingVersion() })
        requestedUrls.clear()
        assertFalse(worker { updater.stageCatalog(url) })
        assertEquals(listOf(url), requestedUrls)
        downloads[url] = signedCatalog(JSONArray().put(route(99, base = "other-base")))
        assertFalse(worker { updater.stageCatalog(url) })
    }

    @Test fun catalogRejectsWrongAppChannelTamperingDuplicateRoutesAndVersionMismatch() {
        val url = "https://updates.example/catalog.json"
        offer(7)
        val updater = updates()
        worker { updater.start(bundled) }
        downloads[url] = signedCatalog(JSONArray().put(route(7)), appId = "other.app")
        rejected { updater.stageCatalog(url) }
        downloads[url] = signedCatalog(JSONArray().put(route(7)), channel = "beta")
        rejected { updater.stageCatalog(url) }
        downloads[url] = signedCatalog(JSONArray().put(route(7)).put(route(8)))
        rejected { updater.stageCatalog(url) }
        val tampered = JSONObject(String(signedCatalog(JSONArray().put(route(7)))))
            .put("payload", Base64.getEncoder().encodeToString("{}".toByteArray())).toString().toByteArray()
        downloads[url] = tampered
        rejected { updater.stageCatalog(url) }
        for (entry in listOf(route(0), route(7).put("version", 1.5), route(7).put("manifestUrl", "http://updates.example/patch.json"))) {
            downloads[url] = signedCatalog(JSONArray().put(entry))
            rejected { updater.stageCatalog(url) }
        }
        downloads[url] = signedCatalog(JSONArray().put(route(8)))
        requestedUrls.clear()
        rejected { updater.stageCatalog(url) }
        assertEquals(listOf(url, manifestUrl), requestedUrls)
        assertEquals(0L, worker { updater.pendingVersion() })
    }

    @Test fun nativeUpgradeResetsPatchSelectionWithoutClearingBusinessData() {
        for (newBase in listOf("base", "new-base")) {
            context.getSharedPreferences("business-data", 0).edit().putString("login", "preserved").commit()
            val old = updates()
            worker { old.start(bundled) }
            offer(7)
            worker { old.stage(manifestUrl) }
            val healthyOld = updates()
            assertEquals(7L, worker { healthyOld.start(bundled).version })
            worker { healthyOld.markHealthy(7) }
            val newHost = "c".repeat(64)
            fun upgraded() = ScreenUpdates(context, newBase, setOf(key()), setOf("checkout"), newHost) { url, limit ->
                val bytes = downloads[url] ?: throw IOException("No network")
                require(bytes.size <= limit)
                bytes
            }
            val next = upgraded()
            assertEquals(ScreenProgram(0, "new-bundled"), worker { next.start("new-bundled") })
            rejected { next.stage(manifestUrl) } // Old base's signed patch cannot enter the new APK.
            offer(1, base = newBase, hash = newHost)
            assertTrue(worker { next.stage(manifestUrl) }) // Old base's version 7 must not block new base's version 1.
            assertEquals(1L, worker { upgraded().start("new-bundled").version })
            assertEquals(ScreenProgram(0, "new-bundled"), worker { upgraded().start("new-bundled") })
            assertEquals("preserved", context.getSharedPreferences("business-data", 0).getString("login", null))
        }
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

    @Test fun staleHealthCallbackCannotConfirmAFallbackThatHasNotStarted() {
        val first = updates()
        offer(1)
        worker { first.stage(manifestUrl); first.start(bundled) }
        assertTrue(worker { first.markHealthy(1) })
        offer(2)
        worker { first.stage(manifestUrl) }
        val next = updates()
        assertEquals(2L, worker { next.start(bundled).version })
        assertEquals(1L, worker { next.reject(bundled, IOException("Bad patch")).version })
        assertFalse(worker { next.markHealthy(2) })
        // The fallback never completed startup, so the next boot must still reject it.
        assertEquals(0L, worker { updates().start(bundled).version })
    }

    @Test fun failedCommitRestoresPendingStateAndAllowsTheSameVersionToRetry() {
        for ((failures, throwAfterWrite) in listOf(1 to false, 2 to false, 1 to true)) {
            var remaining = 0
            val wrapped = object : android.content.ContextWrapper(context) {
                override fun getApplicationContext(): Context = this
                override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                    val preferences = super.getSharedPreferences(name, mode)
                    return object : android.content.SharedPreferences by preferences {
                        override fun edit(): android.content.SharedPreferences.Editor {
                            val edit = preferences.edit()
                            return object : android.content.SharedPreferences.Editor by edit {
                                override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor { edit.putString(key, value); return this }
                                override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor { edit.putLong(key, value); return this }
                                override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor { edit.putInt(key, value); return this }
                                override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor { edit.putBoolean(key, value); return this }
                                override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor { edit.putFloat(key, value); return this }
                                override fun putStringSet(key: String, value: Set<String>?): android.content.SharedPreferences.Editor { edit.putStringSet(key, value); return this }
                                override fun remove(key: String): android.content.SharedPreferences.Editor { edit.remove(key); return this }
                                override fun clear(): android.content.SharedPreferences.Editor { edit.clear(); return this }
                                override fun commit(): Boolean {
                                    val result = edit.commit()
                                    if (remaining > 0) {
                                        remaining--
                                        if (throwAfterWrite) throw IllegalStateException("Injected commit exception")
                                        return false
                                    }
                                    return result
                                }
                            }
                        }
                    }
                }
            }
            context.getSharedPreferences("patchrelay-screens", 0).edit().clear().commit()
            val updater = ScreenUpdates(wrapped, "base", setOf(key()), setOf("checkout"), host) { url, _ -> downloads.getValue(url) }
            context.getSharedPreferences("patchrelay-screens", 0).edit().putInt("future_int", 42)
                .putFloat("future_float", 1.5f).putStringSet("future_set", setOf("a", "b")).putBoolean("enabled", true).commit()
            offer(1); worker { updater.stage(manifestUrl) }
            val before = context.getSharedPreferences("patchrelay-screens", 0).all.toMap()
            offer(2); remaining = failures
            rejected { updater.stage(manifestUrl) }
            assertEquals(before, context.getSharedPreferences("patchrelay-screens", 0).all)
            assertEquals(1L, updater.pendingVersion())
            assertFalse(file(2).exists())
            assertTrue(worker { updater.stage(manifestUrl) })
            assertEquals(2L, updater.pendingVersion())
        }
    }
}
