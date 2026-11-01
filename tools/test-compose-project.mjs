import assert from 'node:assert/strict';
import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
import {gradle, checkFunctions} from '../cli/src/project.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const folder = join(root, '.patchrelay', 'compose-only-' + randomUUID());
for (const path of ['src/commonMain/kotlin', 'src/commonTest/kotlin']) mkdirSync(join(folder, path), {recursive: true});
const plugin = join(root, '.patchrelay-tools').replaceAll('\\', '/');
writeFileSync(join(folder, 'settings.gradle'), `pluginManagement { includeBuild('${plugin}'); repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = 'IndependentCompose'
`);
writeFileSync(join(folder, 'gradle.properties'), 'kotlin.js.ir.output.granularity=whole-program\npatchrelay.compose=true\n');
writeFileSync(join(folder, 'build.gradle'), `plugins { id 'org.jetbrains.kotlin.multiplatform' version '1.9.23'; id 'dev.patchrelay.functions' }
kotlin {
    jvm { compilations.all { kotlinOptions.jvmTarget = '17' } }
    js(IR) { moduleName = 'ComposeOnly'; nodejs(); binaries.executable(); compilations.all { kotlinOptions.moduleKind = 'plain' } }
    sourceSets { commonTest { dependencies { implementation kotlin('test') } }; jvmTest { dependencies { implementation kotlin('test-junit') } } }
}
`);
writeFileSync(join(folder, 'src/commonMain/kotlin/App.kt'), `import androidx.compose.runtime.*
import dev.patchrelay.compose.*
private val screen by lazy { RelayComposition("inventory") { Counter() } }
@Composable private fun Counter() {
    var count by remember { mutableStateOf(0) }
    Column("panel") { Text("count", "Inventory $count"); Button("add", "Add") { count++ } }
}
fun renderAppScreen(id: String): String = screen.render()
fun handleAppEvent(id: String, action: String, value: String?): String = screen.event(action, value)
`);
writeFileSync(join(folder, 'src/commonTest/kotlin/AppTest.kt'), `import kotlin.test.*
class AppTest {
    @Test fun composeWithoutDummyPatchFunctions() {
        assertTrue(PatchRelayFunctions.contracts.isEmpty())
        assertTrue(renderAppScreen("inventory").contains("Inventory 0"))
        assertTrue(handleAppEvent("inventory", "c_add", null).contains("Inventory 1"))
    }
}
`);
gradle(root, ['-p', folder, 'jvmTest', 'compileProductionExecutableKotlinJs']);
const context = vm.createContext({}, {codeGeneration: {strings: false, wasm: false}});
new vm.Script(readFileSync(join(folder, 'build/compileSync/js/main/productionExecutable/kotlin/ComposeOnly.js'), 'utf8')).runInContext(context, {timeout: 5000});
const contract = JSON.parse(readFileSync(join(folder, 'build/generated/patchrelay/functions.json')));
assert.equal(contract.functions.length, 0);
checkFunctions(contract, contract);
assert.match(context.__patchrelay.renderScreen('inventory'), /Inventory 0/);
assert.match(context.__patchrelay.handleEvent('inventory', 'c_add', null), /Inventory 1/);
assert.match(context.__patchrelay.handleEvent('inventory', 'c_add', null), /Inventory 2/);
console.log('PASS: independent Compose-only JVM/JS project, generated empty contracts and callbacks; no dummy function or handwritten JS exports.');
console.log('Fixture retained:', folder);
