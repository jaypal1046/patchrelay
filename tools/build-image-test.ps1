param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/PatchBuild.psm1" -Force
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :demo:assembleRelease :patch:testStressUnitTest :patch:imageProbeJar --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Image test build failed' }
$out = Join-Path $PSScriptRoot '../dist/image-assets-test-v1'
New-Item -ItemType Directory -Force $out | Out-Null
Copy-Item "$PSScriptRoot/../demo/build/outputs/apk/release/demo-release.apk" "$out/image-base-0.4.0.apk" -Force
Copy-Item "$PSScriptRoot/../demo/src/main/assets/image-before.png" "$out/before.png" -Force
Copy-Item "$PSScriptRoot/../test-assets/image-after.png" "$out/after.png" -Force
$afterHash = (Get-FileHash "$out/after.png" -Algorithm SHA256).Hash.ToLowerInvariant()
if ((Get-FileHash "$out/before.png" -Algorithm SHA256).Hash.ToLowerInvariant() -eq $afterHash) { throw 'Before/after images must differ' }
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path "$out/image-base-0.4.0.apk"))
try {
    if (!$archive.GetEntry('assets/image-before.png')) { throw 'Base image absent from APK' }
    foreach ($entry in ($archive.Entries | Where-Object FullName -Match '\.(png|jpg|webp)$')) {
        $stream = $entry.Open()
        try { $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($stream)).ToLowerInvariant() }
        finally { $stream.Dispose() }
        if ($hash -eq $afterHash) { throw 'After image already embedded in base APK' }
    }
} finally { $archive.Dispose() }
ConvertTo-PatchDex -AndroidSdk $AndroidSdk -BuildTools '35.0.0' -OutputDirectory $out -Jar "$PSScriptRoot/../patch/build/libs/image-probe.jar"
$dex = [Text.Encoding]::Latin1.GetString([IO.File]::ReadAllBytes((Join-Path $out 'classes.dex')))
if (!$dex.Contains($afterHash) -or $dex.Contains('Ldev/patchrelay/demo/patch/BuildConfig;')) { throw 'Expected image hash was not inlined into patch code' }
Write-PatchManifest -PrivateKey $PrivateKey -OutputDirectory $out -AppId dev.patchrelay.demo -BaseVersion 3 -Version 11 -EntryPoint dev.patchrelay.demo.patch.ImageAssetPatch -ArtifactUrl https://github.com/jaypal1046/patchrelay/releases/download/image-assets-test-v1/classes.dex
Write-Output "Base contains before image only; patch pins after SHA-256 $afterHash. Phone installation requires approval."
