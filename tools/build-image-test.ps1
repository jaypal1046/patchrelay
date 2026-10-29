param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
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
& "$AndroidSdk/build-tools/35.0.0/d8.bat" --min-api 26 --lib "$AndroidSdk/platforms/android-35/android.jar" --output $out "$PSScriptRoot/../patch/build/libs/image-probe.jar"
if ($LASTEXITCODE -ne 0) { throw 'Image patch DEX failed' }
$dex = [Text.Encoding]::Latin1.GetString([IO.File]::ReadAllBytes((Join-Path $out 'classes.dex')))
if (!$dex.Contains($afterHash) -or $dex.Contains('Ldev/patchrelay/demo/patch/BuildConfig;')) { throw 'Expected image hash was not inlined into patch code' }
& java "$PSScriptRoot/Publisher.java" sign $PrivateKey "$out/classes.dex" dev.patchrelay.demo 3 11 dev.patchrelay.demo.patch.ImageAssetPatch https://github.com/jaypal1046/patchrelay/releases/download/image-assets-test-v1/classes.dex "$out/patch.json"
if ($LASTEXITCODE -ne 0) { throw 'Signing failed; manifests are not overwritten' }
Write-Output "Base contains before image only; patch pins after SHA-256 $afterHash. Phone installation requires approval."
