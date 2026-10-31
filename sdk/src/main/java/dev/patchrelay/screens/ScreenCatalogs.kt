package dev.patchrelay.screens

import java.net.URI

internal data class CatalogPatch(val url: String, val version: Long)

internal object ScreenCatalogs {
    fun select(bytes: ByteArray, keys: Set<String>, appId: String, base: String, host: String, channel: String): CatalogPatch? {
        require(channel.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Invalid update channel" }
        val catalog = ScreenManifests.verifiedPayload(bytes, keys)
        require(catalog.get("schema") == 1 && catalog.getString("kind") == "patchrelay-catalog" &&
            catalog.getString("appId") == appId && catalog.getString("channel") == channel) { "Incompatible catalog" }
        val entries = catalog.getJSONArray("entries")
        require(entries.length() <= 64) { "Catalog has too many base builds" }
        val seen = mutableSetOf<String>()
        var selected: CatalogPatch? = null
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val entryBase = entry.getString("baseVersion")
            val entryHost = entry.getString("hostSha256")
            require(entryBase.isNotBlank() && entryBase.length <= 128 && entryHost.matches(Regex("[a-f0-9]{64}")))
            require(seen.add("$entryBase:$entryHost")) { "Ambiguous catalog route" }
            val version = entry.get("version")
            require((version is Int || version is Long) && (version as Number).toLong() > 0) { "Invalid catalog patch version" }
            val address = URI(entry.getString("manifestUrl"))
            require(address.scheme == "https" && address.host != null && address.rawUserInfo == null && address.rawFragment == null) { "Invalid manifest URL" }
            if (entryBase == base && entryHost == host) selected = CatalogPatch(address.toString(), (version as Number).toLong())
        }
        return selected
    }
}
