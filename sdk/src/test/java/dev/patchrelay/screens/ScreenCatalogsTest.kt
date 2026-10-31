package dev.patchrelay.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenCatalogsTest {
    @Test fun acceptsCatalogProducedByNodeCli() {
        val fixture = javaClass.getResourceAsStream("/node-catalog.json")!!.bufferedReader().use { JSONObject(it.readText()) }
        val selected = ScreenCatalogs.select(fixture.getJSONObject("envelope").toString().toByteArray(),
            setOf(fixture.getString("publicKey")), "test.app", "base", "b".repeat(64), "production")
        assertEquals(CatalogPatch("https://updates.example/patch.json", 7), selected)
    }
}
