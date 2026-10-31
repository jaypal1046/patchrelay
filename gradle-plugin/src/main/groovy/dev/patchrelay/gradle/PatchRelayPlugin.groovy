package dev.patchrelay.gradle

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.BuildConfigField
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.*
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class Contract {
    static String hash(byte[] bytes) { MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString() }
    static String fileHash(File file) {
        def digest = MessageDigest.getInstance('SHA-256')
        file.withInputStream { input ->
            byte[] buffer = new byte[8192]
            int length
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length)
        }
        digest.digest().encodeHex().toString()
    }
    static Map read(File file) { new JsonSlurper().parse(file) as Map }
    static File inside(File root, String path) {
        File file = new File(root, path).canonicalFile
        if (!file.toPath().startsWith(root.canonicalFile.toPath())) throw new GradleException('PatchRelay input escapes project directory')
        file
    }
    static Map fingerprint(File root, List paths, List extensions) {
        def files = new TreeMap<String,String>()
        paths.each { path ->
            File input = inside(root, path.toString())
            if (!input.exists()) throw new GradleException("Missing PatchRelay input: $path")
            def visit = { File file ->
                inside(root, root.toPath().relativize(file.toPath()).toString())
                if (Files.isSymbolicLink(file.toPath())) throw new GradleException('PatchRelay source links are unsupported')
                String digest = (extensions.any { file.name.endsWith(it.toString()) }
                    ? hash(file.getText('UTF-8').replace('\r\n','\n').getBytes('UTF-8')) : fileHash(file))
                files[root.toPath().relativize(file.toPath()).toString().replace('\\','/')] = digest
            }
            if (input.isFile()) visit(input)
            else input.eachFileRecurse { File file -> if (file.isFile()) visit(file) }
        }
        files
    }
}

abstract class PrepareBaseline extends DefaultTask {
    @OutputDirectory abstract DirectoryProperty getOutputDirectory()
    @Internal Map options
    @Internal def namespaceValue
    @Internal def applicationId

    @TaskAction void prepare() {
        File root = project.rootDir
        String namespace = namespaceValue.get()
        String appId = applicationId.get()
        Map runtime = Contract.read(Contract.inside(root, options.runtimeFile))
        if (runtime.format != 1 || runtime.screenApi != 2 || runtime.appId != appId) throw new GradleException('PatchRelay runtime does not match this application variant')
        File program = Contract.inside(root, options.programFile)
        byte[] bytes = program.bytes
        if (!bytes.length || bytes.length > 2 * 1024 * 1024) throw new GradleException('PatchRelay program must be 1 byte–2 MiB')
        java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes))
        File asset = Contract.inside(root, options.baselineAsset)
        asset.parentFile.mkdirs()
        if (!asset.isFile() || !Arrays.equals(asset.bytes, bytes)) Files.copy(program.toPath(), asset.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Map nativeFiles = Contract.fingerprint(root, runtime.nativeInputs, runtime.textExtensions)
        Map screenFiles = Contract.fingerprint(root, options.screenInputs, runtime.textExtensions)
        String host = Contract.hash(JsonOutput.toJson(nativeFiles).getBytes('UTF-8'))
        File output = outputDirectory.get().asFile
        output.mkdirs()
        File source = new File(output, namespace.replace('.','/') + '/patchrelay/PatchRelayBuild.java')
        source.parentFile.mkdirs()
        source.setText("package ${namespace}.patchrelay;\npublic final class PatchRelayBuild { public static final String HOST_SHA256 = \"$host\"; }\n", 'UTF-8')
        new File(output, 'baseline-screen.js').bytes = bytes
        new File(output, 'contract.json').setText(JsonOutput.toJson([
            format:3, appId:appId, baseVersion:runtime.baseVersion, screenApi:runtime.screenApi,
            screenIds:runtime.screenIds, hostSha256:host, nativeFiles:nativeFiles,
            screenFiles:screenFiles, programSha256:Contract.hash(bytes)
        ]), 'UTF-8')
    }
}

abstract class RecordBaseline extends DefaultTask {
    @Internal Map options
    @Internal def prepared
    @Internal def artifactProvider
    @Internal def artifactsLoader
    @Internal String artifactType
    @Internal String variantName

    private void result(File destination, File artifact) {
        File pointer = project.layout.buildDirectory.file("outputs/patchrelay/$variantName/$artifactType-record.json").get().asFile
        pointer.parentFile.mkdirs()
        pointer.setText(JsonOutput.toJson([
            baselineFile:project.rootDir.toPath().relativize(new File(destination, 'baseline.json').toPath()).toString().replace('\\','/'),
            artifactFile:project.rootDir.toPath().relativize(artifact.toPath()).toString().replace('\\','/')
        ]) + '\n', 'UTF-8')
    }

    @TaskAction void record() {
        File root = project.rootDir
        File preparedDir = prepared.get().outputDirectory.get().asFile
        Map baseline = Contract.read(new File(preparedDir, 'contract.json'))
        Map runtime = Contract.read(Contract.inside(root, options.runtimeFile))
        if (Contract.fingerprint(root, runtime.nativeInputs, runtime.textExtensions) != baseline.nativeFiles ||
            Contract.fingerprint(root, options.screenInputs, runtime.textExtensions) != baseline.screenFiles) {
            throw new GradleException('Sources changed during the build; no PatchRelay baseline recorded')
        }
        File artifact
        if (artifactType == 'apk') {
            def built = artifactsLoader.load(artifactProvider.get())
            if (built == null || built.applicationId != baseline.appId || built.elements.size() != 1) throw new GradleException('PatchRelay currently requires one non-split APK per variant')
            artifact = new File(built.elements.first().outputFile)
        } else artifact = artifactProvider.get().asFile
        String entryName = (artifactType == 'aab' ? 'base/' : '') + 'assets/baseline-screen.js'
        new ZipFile(artifact).withCloseable { zip ->
            def entry = zip.getEntry(entryName)
            if (entry == null || entry.size < 1 || entry.size > 2 * 1024 * 1024) throw new GradleException('Built artifact has no supported bundled screen program')
            byte[] embedded = zip.getInputStream(entry).withCloseable { it.readNBytes(2 * 1024 * 1024 + 1) }
            if (Contract.hash(embedded) != baseline.programSha256) throw new GradleException('Built artifact contains a different screen program; baseline rejected')
        }
        String artifactHash = Contract.fileHash(artifact)
        File destination = Contract.inside(root, ".patchrelay/releases/baselines/${baseline.appId}/$variantName/$artifactType-$artifactHash")
        File existing = new File(destination, 'baseline.json')
        if (existing.isFile()) {
            Map saved = Contract.read(existing)
            if (saved.artifactSha256 != artifactHash || saved.nativeFiles != baseline.nativeFiles || saved.screenFiles != baseline.screenFiles ||
                !new File(destination, 'baseline-sources.zip').isFile() ||
                Contract.fileHash(new File(destination, 'baseline-sources.zip')) != saved.sourcesSha256 ||
                Contract.fileHash(new File(destination, "screen-base.$artifactType")) != artifactHash ||
                Contract.hash(new File(destination, 'baseline-screen.js').bytes) != baseline.programSha256) {
                throw new GradleException('Existing PatchRelay record differs or is damaged; restore it rather than overwriting')
            }
            logger.lifecycle("PatchRelay baseline already recorded: $destination")
            result(destination, artifact)
            return
        }
        if (destination.exists()) throw new GradleException('Incomplete PatchRelay output exists; preserve it and choose a fresh build')
        destination.mkdirs()
        Files.copy(artifact.toPath(), new File(destination, "screen-base.$artifactType").toPath())
        new File(destination, 'baseline-screen.js').bytes = new File(preparedDir, 'baseline-screen.js').bytes
        File archive = new File(destination, 'baseline-sources.zip')
        new ZipOutputStream(new FileOutputStream(archive)).withCloseable { zip ->
            (baseline.nativeFiles.keySet() + baseline.screenFiles.keySet()).toSorted().unique().each { String path ->
                if (path.split('/').any { it in ['.keys', '.env', 'local.properties'] } || path ==~ /(?i).*\.(key|pem|p12|jks|keystore)$/) throw new GradleException('Private credentials cannot be baseline inputs')
                File file = Contract.inside(root, path)
                def entry = new ZipEntry(path); entry.time = 0
                zip.putNextEntry(entry); file.withInputStream { it.transferTo(zip) }; zip.closeEntry()
            }
        }
        baseline.artifactType = artifactType
        baseline.artifactSha256 = artifactHash
        baseline[artifactType == 'apk' ? 'apkSha256' : 'aabSha256'] = artifactHash
        baseline.variant = variantName
        baseline.sourcesSha256 = Contract.fileHash(archive)
        Files.writeString(existing.toPath(), JsonOutput.prettyPrint(JsonOutput.toJson(baseline)) + '\n', java.nio.file.StandardOpenOption.CREATE_NEW)
        result(destination, artifact)
        logger.lifecycle("PatchRelay baseline recorded: $destination\nBack up the entire directory privately; patch selection remains explicit.")
    }
}

class PatchRelayPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create('patchrelayBaseline', BaselineOptions, project)
        project.plugins.withId('com.android.application') {
            project.extensions.getByName('androidComponents').onVariants(
                project.extensions.getByName('androidComponents').selector().withBuildType('release')) { variant ->
                def options = extension.values()
                String cap = variant.name.capitalize()
                def prepare = project.tasks.register("preparePatchRelay$cap", PrepareBaseline) {
                    it.options = options; it.namespaceValue = variant.namespace; it.applicationId = variant.applicationId
                    outputDirectory.set(project.layout.buildDirectory.dir("generated/patchrelay/${variant.name}"))
                    dependsOn(options.compileTasks)
                    outputs.upToDateWhen { false }
                    notCompatibleWithConfigurationCache('PatchRelay snapshots declared project sources at execution time')
                }
                variant.sources.java.addGeneratedSourceDirectory(prepare, { it.outputDirectory })
                variant.buildConfigFields.put('HOST_SHA256', project.providers.provider {
                    new BuildConfigField('String', "${variant.namespace.get()}.patchrelay.PatchRelayBuild.HOST_SHA256", null)
                })
                project.tasks.matching { it.name in ["merge${cap}Assets", "generate${cap}BuildConfig"] }.configureEach { dependsOn(prepare) }
                ['apk','aab'].each { type ->
                    String buildName = type == 'apk' ? "assemble$cap" : "bundle$cap"
                    def provider = variant.artifacts.get(type == 'apk' ? SingleArtifact.APK.INSTANCE : SingleArtifact.BUNDLE.INSTANCE)
                    def record = project.tasks.register("recordPatchRelay${cap}${type.toUpperCase()}", RecordBaseline) {
                        it.options = options; it.prepared = prepare; artifactProvider = provider
                        artifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
                        artifactType = type; variantName = variant.name
                        dependsOn(prepare, provider)
                        notCompatibleWithConfigurationCache('PatchRelay writes immutable release records outside build/')
                        onlyIf {
                            def build = project.tasks.findByName(buildName)
                            build != null && build.state.executed && build.state.failure == null
                        }
                    }
                    project.tasks.matching { it.name == buildName }.configureEach { build ->
                        build.finalizedBy(record)
                    }
                }
            }
        }
    }
}

class BaselineOptions {
    final Project project
    String runtimeFile = 'runtime.json'
    String programFile = 'screens/build/compileSync/js/main/productionExecutable/kotlin/PatchRelayScreens.js'
    String baselineAsset
    List<String> screenInputs = ['screens/src/commonMain']
    List<String> compileTasks = [':screens:jvmTest', ':screens:compileProductionExecutableKotlinJs']
    BaselineOptions(Project project) { this.project = project; baselineAsset = project.rootDir.toPath().relativize(new File(project.projectDir, 'src/main/assets/baseline-screen.js').toPath()).toString().replace('\\','/') }
    Map values() { [runtimeFile:runtimeFile, programFile:programFile, baselineAsset:baselineAsset, screenInputs:screenInputs, compileTasks:compileTasks] }
}
