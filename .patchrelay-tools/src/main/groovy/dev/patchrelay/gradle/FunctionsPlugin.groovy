package dev.patchrelay.gradle

import groovy.json.JsonOutput
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.file.FileCollection
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.lexer.KtTokens

/** Kotlin PSI reads declarations; Kotlin/JVM and Kotlin/JS still type-check all bodies. */
class FunctionBindings {
    static List<Map> inspect(Map<String, String> sources) { discover(sources).functions }

    static Map discover(Map<String, String> sources, boolean compose = false) {
        def disposable = Disposer.newDisposable()
        try {
            def env = KotlinCoreEnvironment.createForProduction(disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
            def factory = new KtPsiFactory(env.project, false)
            def bindings = []
            def states = []
            def entrypoints = new HashSet<String>()
            sources.toSorted().each { path, text ->
                def file = factory.createFile(path, text.replace('\r\n', '\n').replace('\r', '\n'))
                if (compose && file.importDirectives.any { it.importedFqName?.asString()?.startsWith('androidx.compose.') && !it.importedFqName.asString().startsWith('androidx.compose.runtime') })
                    throw new GradleException("PatchRelay $path: only Compose runtime and dev.patchrelay.compose widgets are supported; Android Material/UI requires a different backend")
                if (compose && file.importDirectives.any { it.importedFqName?.asString() in ['androidx.compose.runtime.LaunchedEffect', 'androidx.compose.runtime.produceState', 'androidx.compose.runtime.rememberCoroutineScope', 'androidx.compose.runtime.snapshotFlow'] })
                    throw new GradleException("PatchRelay $path: asynchronous Compose effects are unsupported, including aliased imports; use installed host operations")
                if (file.importDirectives.any { it.aliasName && it.importedFqName?.shortName()?.asString() == 'PatchFunction' })
                    throw new GradleException("PatchRelay $path: annotation aliases are unsupported; use @PatchFunction")
                def visit
                visit = { element ->
                    if (!compose && element instanceof KtNamedFunction && element.annotationEntries.any { it.shortName?.asString() == 'Composable' })
                        throw new GradleException("PatchRelay $path: ${element.name}: Compose runtime/backend is unsupported; use the shared screen-tree DSL or keep Compose in the installed native host")
                    if (compose && element instanceof KtCallExpression && element.calleeExpression?.text in ['LaunchedEffect', 'produceState', 'rememberCoroutineScope', 'snapshotFlow'])
                        throw new GradleException("PatchRelay $path: ${element.calleeExpression.text}: asynchronous Compose effects are unsupported; use installed host operations and explicit render/event transactions")
                    if (element instanceof KtNamedFunction && element.parent instanceof KtFile) {
                        if (element.name?.startsWith('patchRelayIntercept_') || element.name?.startsWith('__patchrelay_original_'))
                            throw new GradleException("PatchRelay $path: reserved compiler-generated function name: ${element.name}")
                        entrypoints.add(element.name)
                    }
                    if (element instanceof KtProperty && element.annotationEntries.any { it.shortName?.asString() == 'PatchState' }) {
                        def property = element
                        def annotation = property.annotationEntries.find { it.shortName?.asString() == 'PatchState' }
                        String target = (file.packageFqName.asString() ? file.packageFqName.asString() + '.' : '') + property.name
                        String id = target.toLowerCase(Locale.ROOT)
                        if (annotation.valueArguments) {
                            def expression = annotation.valueArguments.size() == 1 ? annotation.valueArguments[0].getArgumentExpression() : null
                            if (!(expression instanceof KtStringTemplateExpression) || expression.entries.any { !(it instanceof KtLiteralStringTemplateEntry) })
                                throw new GradleException("PatchRelay $path: state ID must be a literal string")
                            id = expression.entries.collect { it.text }.join('')
                        }
                        if (!(property.parent instanceof KtFile) || !property.isVar() || !property.initializer || property.accessors || property.hasDelegate() ||
                            property.hasModifier(KtTokens.PRIVATE_KEYWORD) || !supportedType(property.typeReference?.text, false) ||
                            !property.name?.matches('[a-zA-Z][a-zA-Z0-9_]*') || !id.matches('[a-z][a-z0-9_.-]{0,95}'))
                            throw new GradleException("PatchRelay $path: @PatchState requires a top-level mutable scalar with explicit type, initializer and no custom accessors")
                        states << [id:id, target:target, type:property.typeReference.text]
                    }
                    if (element instanceof KtNamedFunction && element.annotationEntries.any { it.shortName?.asString() == 'PatchFunction' }) {
                        def fn = element
                        def fail = { reason -> throw new GradleException("PatchRelay $path: ${fn.name}: $reason. Supported: public top-level function, 0..8 scalar arguments, explicit scalar/Unit return type") }
                        if (!(fn.parent instanceof KtFile) || fn.receiverTypeReference || fn.typeParameters ||
                            fn.hasModifier(KtTokens.PRIVATE_KEYWORD) || fn.hasModifier(KtTokens.INTERNAL_KEYWORD) || fn.hasModifier(KtTokens.EXPECT_KEYWORD) || fn.hasModifier(KtTokens.EXTERNAL_KEYWORD) || fn.hasModifier(KtTokens.INLINE_KEYWORD)) fail('unsupported function declaration (inline interception is unsupported)')
                        def parameters = fn.valueParameters.collect { it.typeReference?.text }
                        String result = fn.typeReference?.text
                        if (!fn.name?.matches('[a-zA-Z][a-zA-Z0-9_]*') || !fn.hasBody() || fn.valueParameters.size() > 8 ||
                            !supportedType(result, true) || parameters.any { !supportedType(it, false) } ||
                            fn.valueParameters.any { it.defaultValue || it.isVarArg() }) fail('use Int, finite Double, Boolean, String, nullable scalars or Unit result; no defaults or varargs')
                        def annotation = fn.annotationEntries.find { it.shortName?.asString() == 'PatchFunction' }
                        def args = annotation.valueArguments
                        String qualified = (file.packageFqName.asString() ? file.packageFqName.asString() + '.' : '') + fn.name
                        String id = qualified.toLowerCase(Locale.ROOT) + '.v1'
                        if (args) {
                            def expression = args.size() == 1 ? args[0].getArgumentExpression() : null
                            if (!(expression instanceof KtStringTemplateExpression) || expression.entries.any { !(it instanceof KtLiteralStringTemplateEntry) }) fail('ID must be a literal string')
                            id = expression.entries.collect { it.text }.join('')
                        }
                        if (!id.matches('[a-z][a-z0-9_.-]{0,95}')) fail('ID must match [a-z][a-z0-9_.-]{0,95}')
                        boolean suspending = fn.hasModifier(KtTokens.SUSPEND_KEYWORD)
                        bindings << [id:id, signature:(suspending ? 'suspend ' : '') + '(' + parameters.join(',') + ')->' + result,
                            target:qualified, name:fn.name, parameters:parameters, result:result, suspending:suspending]
                    }
                    element.children.each { visit(it) }
                }
                visit(file)
            }
            if (bindings.size() > 64) throw new GradleException('PatchRelay supports at most 64 @PatchFunction declarations')
            if (bindings*.id.toSet().size() != bindings.size() || bindings*.name.toSet().size() != bindings.size()) throw new GradleException('PatchRelay duplicate function ID or hook name; overloads are unsupported')
            if (entrypoints.contains('main')) throw new GradleException('PatchRelay owns the JS main entrypoint; move application initialization into the screen module')
            if (entrypoints.contains('takeHostRequests') != entrypoints.contains('handleHostResult')) throw new GradleException('PatchRelay host protocol requires both takeHostRequests and handleHostResult')
            if (states.size() > 32 || states*.id.toSet().size() != states.size()) throw new GradleException('PatchRelay state IDs must be unique; maximum 32 values')
            [functions:bindings.sort { it.id }, hostCalls:entrypoints.contains('takeHostRequests'), states:states.sort { it.id }, migration:entrypoints.contains('migrateAppState')]
        } finally { Disposer.dispose(disposable) }
    }

    static boolean supportedType(String type, boolean result) {
        type != null && (type in ['Int', 'Double', 'Boolean', 'String', 'Int?', 'Double?', 'Boolean?', 'String?'] || (result && type == 'Unit'))
    }

    static String decode(String type, String value) {
        if (type == 'Unit') return "patchRelayUnit($value)"
        String name = type.replace('?', '')
        type.endsWith('?') ? "patchRelayNullable($value, ::patchRelay$name)" : "patchRelay$name($value)"
    }

    static String encode(String type, String value) { type == 'Unit' ? "run { $value; JsonNull }" : "JsonPrimitive($value)" }

    static void generate(List<Map> bindings, File output, boolean hostCalls = false, List<Map> states = [], boolean migration = false) {
        File common = new File(output, 'common/PatchRelayFunctions.kt')
        File js = new File(output, 'js/PatchRelayExports.kt')
        common.parentFile.mkdirs(); js.parentFile.mkdirs()
        String imports = bindings.withIndex().collect { b, index -> "import ${b.target} as patchRelayNative$index" }.join('\n')
        imports += '\n' + states.withIndex().collect { state, index -> "import ${state.target} as patchRelayState$index" }.join('\n')
        String schema = Contract.hash(JsonOutput.toJson(states.collect { [it.id, it.type] }).getBytes('UTF-8'))
        String restore = states.withIndex().collect { state, index -> '    val state' + index + ' = ' + decode(state.type, 'values["' + state.id + '"] ?: error("Missing state")') }.join('\n') + '\n' +
            states.withIndex().collect { state, index -> "    patchRelayState$index = state$index" }.join('\n')
        String stateCode = """
internal fun patchRelayExportState(): String = patchRelayText(JsonObject(mapOf(
    "schema" to JsonPrimitive("$schema"), "values" to JsonObject(mapOf(${states.withIndex().collect { state, index -> '"' + state.id + '" to ' + encode(state.type, "patchRelayState$index") }.join(', ')})))))
internal fun patchRelayRestoreState(snapshot: String): String {
    val root = patchRelayParse(snapshot) as? JsonObject ?: error("Expected state object")
    val schema = patchRelayString(root["schema"] ?: error("Missing state schema"))
    val oldValues = root["values"] as? JsonObject ?: error("Missing state values")
    val values = if (schema == "$schema") oldValues else ${migration ? 'migrateAppState(schema, oldValues)' : 'return "incompatible"'}
    require(values.keys == setOf<String>(${states.collect { '"' + it.id + '"' }.join(', ')})) { "Wrong state fields" }
$restore
    return "restored"
}
"""
        String hooks = bindings.withIndex().collect { b, index ->
            String params = b.parameters.withIndex().collect { type, n -> "arg$n: $type" }.join(', ')
            String args = b.parameters.indices.collect { "arg$it" }.join(', ')
            String call = "patchRelayNative$index($args)"
            String encodedArgs = b.parameters.withIndex().collect { type, n -> encode(type, "arg$n") }.join(', ')
            """    fun ${b.name}(${params ? params + ', ' : ''}dispatch: PatchRelayDispatch? = null, onResult: (${b.result}) -> Unit) {
        val native: suspend () -> ${b.result} = { $call }
        if (dispatch == null) native.startCoroutine(Continuation(EmptyCoroutineContext) { onResult(it.getOrThrow()) })
        else dispatch("${b.id}", patchRelayText(JsonArray(listOf($encodedArgs))), { done ->
            native.startCoroutine(Continuation(EmptyCoroutineContext) { result -> done(result.map { value -> patchRelayText(${encode(b.result, 'value')}) }) })
        }, { onResult(${decode(b.result, 'patchRelayParse(it)')}) })
    }"""
        }.join('\n')
        String intercepted = bindings.findAll { it.suspending }.collect { b ->
            String nativeName = 'patchRelayNative' + bindings.indexOf(b)
            String params = b.parameters.withIndex().collect { type, n -> "arg$n: $type" }.join(', ')
            String args = b.parameters.indices.collect { "arg$it" }.join(', ')
            String encodedArgs = b.parameters.withIndex().collect { type, n -> encode(type, "arg$n") }.join(', ')
            """suspend fun patchRelayIntercept_${b.name}($params): ${b.result} {
    val dispatch = PatchRelayFunctions.dispatcher
    if (dispatch == null) return $nativeName($args)
    return suspendCoroutine { continuation ->
        var completed = false
        val finish: (Result<String>) -> Unit = { result ->
            if (!completed) {
                completed = true
                continuation.resumeWith(result.mapCatching { ${decode(b.result, 'patchRelayParse(it)')} })
            }
        }
        try {
            dispatch("${b.id}", patchRelayText(JsonArray(listOf($encodedArgs))), { done ->
                val native: suspend () -> ${b.result} = { $nativeName($args) }
                native.startCoroutine(Continuation(EmptyCoroutineContext) { result -> done(result.mapCatching { value -> patchRelayText(${encode(b.result, 'value')}) }) })
            }, finish)
        } catch (failure: Throwable) { finish(Result.failure(failure)) }
    }
}"""
        }.join('\n')
        common.setText("""// Generated by PatchRelay. Do not edit.
import kotlinx.serialization.json.*
import kotlin.coroutines.*
$imports
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class PatchFunction(val id: String = "")
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PatchState(val id: String = "")

typealias PatchRelayDispatch = (String, String, ((Result<String>) -> Unit) -> Unit, (String) -> Unit) -> Unit
typealias PatchRelaySuspendDispatch = (String, String, ((Result<String>) -> Unit) -> Unit, (Result<String>) -> Unit) -> Unit

internal fun patchRelayParse(value: String): JsonElement { require(value.length <= 8192); return Json.parseToJsonElement(value) }
internal fun patchRelayText(value: JsonElement): String = value.toString().also { require(it.length <= 8192) }
internal fun patchRelayInt(value: JsonElement): Int { require(value is JsonPrimitive && !value.isString); return value.intOrNull ?: error("Expected Int") }
internal fun patchRelayDouble(value: JsonElement): Double { require(value is JsonPrimitive && !value.isString); return (value.doubleOrNull ?: error("Expected Double")).also { require(it.isFinite()) } }
internal fun patchRelayBoolean(value: JsonElement): Boolean { require(value is JsonPrimitive && !value.isString); return value.booleanOrNull ?: error("Expected Boolean") }
internal fun patchRelayString(value: JsonElement): String { require(value is JsonPrimitive && value.isString); return value.content.also { require(it.length <= 4096) } }
internal fun patchRelayUnit(value: JsonElement) { require(value == JsonNull) }
internal fun <T> patchRelayNullable(value: JsonElement, read: (JsonElement) -> T): T? = if (value == JsonNull) null else read(value)

$stateCode
$intercepted
object PatchRelayFunctions {
    @kotlin.concurrent.Volatile private var installedDispatcher: PatchRelaySuspendDispatch? = null
    internal val dispatcher: PatchRelaySuspendDispatch? get() = installedDispatcher
    fun installDispatcher(dispatch: PatchRelaySuspendDispatch?) { installedDispatcher = dispatch }
    val contracts: Map<String, String> = mapOf(${bindings.collect { '"' + it.id + '" to "' + it.signature + '"' }.join(', ')})
    val ids: Set<String> get() = contracts.keys
$hooks
}
""", 'UTF-8')
        String ids = JsonOutput.toJson(bindings*.id)
        String contracts = JsonOutput.toJson(bindings.collectEntries { [(it.id):it.signature] })
        String cases = bindings.collect { b ->
            String args = b.parameters.withIndex().collect { type, n -> decode(type, "args[$n]") }.join(', ')
            '    "' + b.id + '" -> { require(args.size == ' + b.parameters.size() + '); ' + encode(b.result, b.target + '(' + args + ')') + ' }'
        }.join('\n')
        String legacy = bindings.findAll { it.signature == '(Int,Int)->Int' }.collect {
            '    "' + it.id + '" -> ' + it.target + '(first, second).toString()'
        }.join('\n')
        js.setText("""@file:OptIn(kotlin.js.ExperimentalJsExport::class)
import kotlin.js.JsExport
import kotlin.js.Promise
import kotlin.coroutines.*
import kotlinx.serialization.json.*
@JsExport
fun renderScreen(screenId: String): String = renderAppScreen(screenId)
@JsExport
fun handleEvent(screenId: String, action: String, value: String? = null): String = handleAppEvent(screenId, action, value)
@JsExport
fun exportState(): String = patchRelayExportState()
@JsExport
fun restoreState(snapshot: String): String = patchRelayRestoreState(snapshot)
@JsExport
fun patchedFunctionIds(): String = ${JsonOutput.toJson(ids)}
@JsExport
fun patchedFunctionContracts(): String = ${JsonOutput.toJson(contracts)}
@JsExport
fun invokeFunction(functionId: String, arguments: String): Promise<String> = Promise { resolve, reject ->
    val body: suspend () -> String = {
        val args = patchRelayParse(arguments) as? JsonArray ?: error("Expected argument array")
        require(args.size <= 8)
        patchRelayText(when (functionId) {
$cases
            else -> error("Unknown function: \$functionId")
        })
    }
    body.startCoroutine(Continuation(EmptyCoroutineContext) { result -> result.fold(resolve, reject) })
}
@JsExport
fun invokePatch(functionId: String, first: Int, second: Int): String = when (functionId) {
$legacy
    else -> error("Unknown legacy integer function: \$functionId")
}

// Stable SDK registration is independent of the Kotlin project's output-module name.
fun main() {
    val api = js("({})")
    api.exportState = ::exportState
    api.restoreState = ::restoreState
    api.renderScreen = ::renderScreen
    api.handleEvent = ::handleEvent
    api.patchedFunctionIds = ::patchedFunctionIds
    api.patchedFunctionContracts = ::patchedFunctionContracts
    api.invokeFunction = ::invokeFunction
    api.invokePatch = ::invokePatch
${hostCalls ? '    api.takeHostRequests = ::takeHostRequests\n    api.handleHostResult = ::handleHostResult' : ''}
    js("globalThis").__patchrelay = api
}
""", 'UTF-8')
        new File(output, 'functions.json').setText(JsonOutput.toJson([format:2, stateSchema:schema, state:states.collect { [id:it.id, type:it.type] }, functions:bindings.collect { [id:it.id, signature:it.signature] }]) + '\n', 'UTF-8')
    }
}

abstract class GenerateFunctions extends DefaultTask {
    @Input boolean compose
    @InputDirectory File sources
    @OutputDirectory File destination
    @InputFiles FileCollection platformSources
    @TaskAction void generate() {
        if (!platformSources.empty) throw new GradleException('PatchRelay supports commonMain Kotlin only; platform implementations require a new native integration: ' + platformSources.files*.name)
        def files = new TreeMap<String,String>()
        sources.eachFileRecurse { if (it.isFile() && it.name.endsWith('.kt')) files[sources.toPath().relativize(it.toPath()).toString()] = it.getText('UTF-8') }
        def program = FunctionBindings.discover(files, compose)
        FunctionBindings.generate(program.functions, destination, program.hostCalls, program.states, program.migration)
        def backend = new File(destination, 'common/PatchRelayCompose.kt')
        if (compose) backend.setText(GenerateFunctions.getResourceAsStream('/patchrelay/ComposeScreens.kt').getText('UTF-8'), 'UTF-8')
        else if (backend.exists()) backend.delete()
    }
}

class FunctionsPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.withId('org.jetbrains.kotlin.multiplatform') {
            if (project.providers.gradleProperty('kotlin.js.ir.output.granularity').orNull != 'whole-program')
                throw new GradleException('PatchRelay requires a self-contained program: add kotlin.js.ir.output.granularity=whole-program to gradle.properties')
            def output = project.layout.buildDirectory.dir('generated/patchrelay')
            boolean composeEnabled = project.providers.gradleProperty('patchrelay.compose').orNull == 'true'
            def compilerJar = new File(FunctionsPlugin.protectionDomain.codeSource.location.toURI())
            if (!compilerJar.isFile()) throw new GradleException('PatchRelay compiler plugin must run from its built JAR')
            def task = project.tasks.register('generatePatchRelayFunctions', GenerateFunctions) {
                sources = project.file('src/commonMain/kotlin')
                platformSources = project.fileTree('src') { include 'jvmMain/**/*.kt', 'jsMain/**/*.kt', 'androidMain/**/*.kt' }
                destination = output.get().asFile
                compose = composeEnabled
            }
            def kotlin = project.extensions.getByName('kotlin')
            if (kotlin.coreLibrariesVersion != '1.9.23') throw new GradleException('PatchRelay IR interception requires Kotlin 1.9.23; compiler upgrades require a matching plugin')
            kotlin.sourceSets.getByName('commonMain').dependencies { implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3' }
            if (composeEnabled) {
                kotlin.sourceSets.getByName('commonMain').dependencies { implementation 'org.jetbrains.compose.runtime:runtime:1.6.10' }
                def composeCompiler = project.configurations.create('patchRelayComposeCompiler')
                project.dependencies.add(composeCompiler.name, 'org.jetbrains.compose.compiler:compiler:1.5.13.5')
                project.tasks.matching { it.name.startsWith('compileKotlin') || it.name.startsWith('compileTestKotlin') || it.name.startsWith('compileProductionExecutable') || it.name.startsWith('compileTestDevelopmentExecutable') }.configureEach {
                    inputs.files(composeCompiler)
                    kotlinOptions.freeCompilerArgs += ['-Xplugin=' + composeCompiler.singleFile.absolutePath]
                }
            }
            kotlin.sourceSets.getByName('commonMain').kotlin.srcDir(output.map { it.dir('common') })
            // jsMain exists after the consumer declares its JS target.
            project.afterEvaluate {
                kotlin.sourceSets.getByName('jsMain').kotlin.srcDir(output.map { it.dir('js') })
            }
            project.tasks.matching { it.name.startsWith('compileKotlin') || it.name.startsWith('compileProductionExecutable') || it.name.startsWith('compileTestKotlin') }.configureEach { dependsOn(task) }
            kotlin.targets.all { target ->
                if (target.platformType.toString() == 'jvm') target.compilations.matching { it.name == 'main' }.all { compilation ->
                    compilation.compileTaskProvider.configure {
                        inputs.file(compilerJar)
                        kotlinOptions.freeCompilerArgs += ['-Xplugin=' + compilerJar.absolutePath]
                    }
                }
            }
        }
    }
}
