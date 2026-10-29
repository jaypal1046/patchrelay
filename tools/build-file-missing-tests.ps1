param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/PatchBuild.psm1" -Force
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :patch:testStressUnitTest :patch:fileProbeJar :patch:missingProbeJar :patch:fixedMissingProbeJar :patch:stressClasspath :patch:dependencyPatchNotices --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Build/tests failed' }
$libraries = Get-Content "$PSScriptRoot/../patch/build/stress-classpath.txt"
$sdkJar = $libraries | Where-Object { $_ -like '*sdk*classes.jar' } | Select-Object -First 1
$stdlib = $libraries | Where-Object { $_ -like '*kotlin-stdlib-1.9.23.jar' } | Select-Object -First 1
& java -cp "$sdkJar;$stdlib" "$PSScriptRoot/CheckMissingDependency.java" "$PSScriptRoot/../patch/build/libs/missing-probe.jar" "$PSScriptRoot/../patch/build/libs/missing-probe-fixed.jar"
if ($LASTEXITCODE -ne 0) { throw 'Missing/fixed classloader checks failed' }
$cases = @(
    @{version=8; jar='file-probe.jar'; entry='FileChangePatch'},
    @{version=9; jar='missing-probe.jar'; entry='MissingDependencyPatch'},
    @{version=10; jar='missing-probe-fixed.jar'; entry='MissingDependencyPatch'}
)
foreach ($case in $cases) {
    $out = Join-Path $PSScriptRoot "../dist/test-patch-$($case.version)"
    New-Item -ItemType Directory -Force $out | Out-Null
    ConvertTo-PatchDex -AndroidSdk $AndroidSdk -OutputDirectory $out -Jar "$PSScriptRoot/../patch/build/libs/$($case.jar)"
    # Type references exist even when a class is missing; inspect actual definitions.
    $definitions = & "$AndroidSdk/build-tools/35.0.0/dexdump.exe" "$out/classes.dex" | Select-String 'Class descriptor'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect DEX definitions' }
    $codecPresent = ($definitions -join "`n") -match "Class descriptor\s*:\s*'Lorg/apache/commons/codec/digest/DigestUtils;'"
    if (($case.version -eq 9 -and $codecPresent) -or ($case.version -eq 10 -and !$codecPresent)) { throw 'Dependency packaging differs from test intent' }
    if ($case.version -eq 10) { Copy-Item "$PSScriptRoot/../patch/build/dependency-notices/*.txt" $out -Force }
    Write-PatchManifest -PrivateKey $PrivateKey -OutputDirectory $out -AppId dev.patchrelay.demo -BaseVersion 3 -Version $case.version -EntryPoint "dev.patchrelay.demo.patch.$($case.entry)" -ArtifactUrl "https://github.com/jaypal1046/patchrelay/releases/download/demo-patch-$($case.version)/classes.dex"
}
Write-Output 'Built patches 8 (file), 9 (missing dependency), 10 (bundled fix). Publish/test sequentially, not together.'
