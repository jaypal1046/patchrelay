@file:OptIn(kotlin.js.ExperimentalJsExport::class)
import kotlin.js.JsExport

@JsExport
fun patchedFunctionIds(): String = "[\"checkout.total.v1\"]"

@JsExport
fun invokePatch(functionId: String, first: Int, second: Int): String {
    require(functionId == "checkout.total.v1") { "Unknown function" }
    require(first in 0..100 && second in 0..1000000) { "Unsupported calculation range" }
    return (first * second).toString()
}
