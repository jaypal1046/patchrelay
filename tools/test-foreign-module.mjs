import assert from 'node:assert/strict';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import { gradle, checkFunctions } from '../cli/src/project.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const folder = join(root, '.patchrelay', 'foreign-module-' + randomUUID());
const plugin = join(root, 'gradle-plugin').replaceAll('\\', '/');
for (const dir of ['src/commonMain/kotlin', 'src/commonTest/kotlin']) mkdirSync(join(folder, dir), { recursive: true });
writeFileSync(join(folder, 'settings.gradle'), `pluginManagement { includeBuild('${plugin}'); repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = 'VendorInventory'
`);
writeFileSync(join(folder, 'gradle.properties'), 'kotlin.js.ir.output.granularity=whole-program\n');
writeFileSync(join(folder, 'build.gradle'), `plugins { id 'org.jetbrains.kotlin.multiplatform' version '1.9.23'; id 'dev.patchrelay.functions' }
kotlin {
    jvm { compilations.all { kotlinOptions.jvmTarget = '17' } }
    js(IR) { moduleName = 'VendorProgram'; nodejs(); binaries.executable(); compilations.all { kotlinOptions.moduleKind = 'plain' } }
    sourceSets { commonTest { dependencies { implementation kotlin('test') } }; jvmTest { dependencies { implementation kotlin('test-junit') } } }
}
`);
writeFileSync(join(folder, 'src/commonMain/kotlin/Business.kt'), `package vendor
import PatchFunction
import PatchState
import kotlin.coroutines.*
@PatchState("quantity")
internal var quantity: Int = 1
private var continuation: Continuation<Int>? = null
@PatchFunction
fun greet(name: String, vip: Boolean): String = (if (vip) "VIP " else "Guest ") + name
@PatchFunction
suspend fun answer(n: Int?): Int? = n?.plus(1)
@PatchFunction
suspend fun later(): Int = suspendCoroutine { continuation = it }
@PatchFunction
suspend fun recursive(n: Int): Int = if (n == 0) 0 else recursive(n - 1) + 1
@PatchFunction
fun resume(n: Int): Unit { continuation!!.resume(n) }
@PatchFunction
fun clear(): Unit { quantity = 0 }
`);
writeFileSync(join(folder, 'src/commonMain/kotlin/App.kt'), `import vendor.quantity
import kotlinx.serialization.json.*
fun renderAppScreen(id: String): String = "Inventory: $quantity"
fun handleAppEvent(id: String, action: String, value: String?): String { quantity++; return renderAppScreen(id) }
fun migrateAppState(schema: String, values: JsonObject): JsonObject {
    require(schema == "a".repeat(64)) { "Unsupported state schema" }
    return JsonObject(mapOf("quantity" to values.getValue("old_quantity")))
}
`);
writeFileSync(join(folder, 'src/commonTest/kotlin/HooksTest.kt'), `import kotlin.test.*
class HooksTest {
    @Test fun nativeSuspendHookWaitsForContinuation() {
        var result: Int? = null
        PatchRelayFunctions.later { result = it }
        assertNull(result)
        vendor.resume(9)
        assertEquals(9, result)
        PatchRelayFunctions.answer(null) { assertNull(it) }
    }
}
`);
mkdirSync(join(folder, 'src/jvmTest/kotlin'), { recursive: true });
writeFileSync(join(folder, 'src/jvmTest/kotlin/InterceptTest.kt'), `import kotlin.test.*
import kotlin.coroutines.*
class InterceptTest {
    @Test fun directPackagedSuspendCallsAreInstrumented() {
        var result: Result<Int?>? = null
        fun launch(body: suspend () -> Int?) { body.startCoroutine(Continuation(EmptyCoroutineContext) { result = it }) }
        try {
            launch { vendor.answer(8) }; assertEquals(9, result!!.getOrThrow())
            PatchRelayFunctions.installDispatcher { id, _, original, done ->
                if (id == "vendor.answer.v1") done(Result.success("41")) else original(done)
            }
            launch { vendor.answer(8) }; assertEquals(41, result!!.getOrThrow())
            launch { vendor.recursive(5) }; assertEquals(5, result!!.getOrThrow())
            result = null
            launch { vendor.later() }; assertNull(result)
            vendor.resume(12); assertEquals(12, result!!.getOrThrow())
        } finally { PatchRelayFunctions.installDispatcher(null) }
    }
}
`);
// Reuse the repo wrapper without installing Gradle or creating global configuration.
gradle(root, ['-p', folder, 'jvmTest', 'compileProductionExecutableKotlinJs']);
const bytes = readFileSync(join(folder, 'build/compileSync/js/main/productionExecutable/kotlin/VendorProgram.js'), 'utf8');
const context = vm.createContext({}, { codeGeneration: { strings: false, wasm: false } });
new vm.Script(bytes).runInContext(context, { timeout: 3000 });
assert.equal(context['PatchRelay:screens'], undefined, 'Fixture must not depend on the old project/module export');
const api = context.__patchrelay;
assert.ok(api);
assert.equal(JSON.parse(await api.invokeFunction('vendor.greet.v1', '["Ada",true]')), 'VIP Ada');
assert.equal(JSON.parse(await api.invokeFunction('vendor.answer.v1', '[null]')), null);
assert.equal(JSON.parse(await api.invokeFunction('vendor.answer.v1', '[8]')), 9);
const later = api.invokeFunction('vendor.later.v1', '[]');
let completed = false;
later.then(() => { completed = true; });
await Promise.resolve();
assert.equal(completed, false);
await api.invokeFunction('vendor.resume.v1', '[17]');
assert.equal(JSON.parse(await later), 17);
const contract = JSON.parse(readFileSync(join(folder, 'build/generated/patchrelay/functions.json')));
checkFunctions(contract, contract);
const snapshot = api.exportState();
api.handleEvent('inventory', 'increment', null);
assert.equal(api.renderScreen('inventory'), 'Inventory: 2');
assert.equal(api.restoreState(snapshot), 'restored');
assert.equal(api.renderScreen('inventory'), 'Inventory: 1');
assert.equal(api.restoreState(JSON.stringify({ schema: 'a'.repeat(64), values: { old_quantity: 7 } })), 'restored');
assert.equal(api.renderScreen('inventory'), 'Inventory: 7');
assert.equal(JSON.parse(await api.invokeFunction('vendor.clear.v1', '[]')), null);
assert.equal(api.renderScreen('inventory'), 'Inventory: 0');
await assert.rejects(api.invokeFunction('vendor.greet.v1', '[42,true]'));
console.log('PASS: foreign project/module, packaged Kotlin declarations, scalar/null/Unit transport, real suspend/resume, native fallback, snapshot restore and explicit schema migration.');
console.log('Fixture retained:', folder);
