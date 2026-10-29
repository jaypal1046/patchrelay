param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :patch:testStressUnitTest :patch:dependencyPatchJar :patch:dependencyPatchNotices --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Dependency patch checks failed' }
$out = Join-Path $PSScriptRoot '../dist/dependency-patch-2'
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item "$PSScriptRoot/../patch/build/dependency-notices/*.txt" $out -Force
& "$AndroidSdk/build-tools/35.0.0/d8.bat" --min-api 26 --lib "$AndroidSdk/platforms/android-35/android.jar" --output $out "$PSScriptRoot/../patch/build/libs/dependency-patch.jar"
if ($LASTEXITCODE -ne 0) { throw 'Dependency DEX build failed' }
$dex = [Text.Encoding]::Latin1.GetString([IO.File]::ReadAllBytes((Join-Path $out 'classes.dex')))
foreach ($descriptor in @('Ldev/patchrelay/demo/patch/DependencyPatch;', 'Lorg/apache/commons/codec/digest/DigestUtils;')) {
    if (!$dex.Contains($descriptor)) { throw "Required class absent from patch: $descriptor" }
}
& java "$PSScriptRoot/Publisher.java" sign $PrivateKey "$out/classes.dex" dev.patchrelay.demo 3 2 dev.patchrelay.demo.patch.DependencyPatch https://github.com/jaypal1046/patchrelay/releases/download/demo-patch-2/classes.dex "$out/patch.json"
if ($LASTEXITCODE -ne 0) { throw 'Patch signing failed; manifests are not overwritten' }
Write-Output "Patch 2 ready in $out; no APK built or installed."
