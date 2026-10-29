param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/PatchBuild.psm1" -Force
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :patch:testStressUnitTest :patch:dependencyPatchJar :patch:dependencyPatchNotices --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Dependency patch checks failed' }
$out = Join-Path $PSScriptRoot '../dist/dependency-patch-2'
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item "$PSScriptRoot/../patch/build/dependency-notices/*.txt" $out -Force
ConvertTo-PatchDex -AndroidSdk $AndroidSdk -BuildTools '35.0.0' -OutputDirectory $out -Jar "$PSScriptRoot/../patch/build/libs/dependency-patch.jar"
$dex = [Text.Encoding]::Latin1.GetString([IO.File]::ReadAllBytes((Join-Path $out 'classes.dex')))
foreach ($descriptor in @('Ldev/patchrelay/demo/patch/DependencyPatch;', 'Lorg/apache/commons/codec/digest/DigestUtils;')) {
    if (!$dex.Contains($descriptor)) { throw "Required class absent from patch: $descriptor" }
}
Write-PatchManifest -PrivateKey $PrivateKey -OutputDirectory $out -AppId dev.patchrelay.demo -BaseVersion 3 -Version 2 -EntryPoint dev.patchrelay.demo.patch.DependencyPatch -ArtifactUrl https://github.com/jaypal1046/patchrelay/releases/download/demo-patch-2/classes.dex
Write-Output "Patch 2 ready in $out; no APK built or installed."
