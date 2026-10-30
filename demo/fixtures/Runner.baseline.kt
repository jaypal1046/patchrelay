@file:OptIn(kotlin.js.ExperimentalJsExport::class)
import kotlin.js.JsExport

@JsExport
fun patchedFunctionIds(): String = "[]"

@JsExport
fun invokePatch(functionId: String, first: Int, second: Int): String =
    error("No function patch bundled")
