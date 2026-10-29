param(
    [Parameter(Mandatory=$true)][string]$ArtifactUrl,
    [long]$Version = 1,
    [string]$AppId = 'dev.patchrelay.demo',
    [string]$BaseVersion = '3',
    [string]$EntryPoint = 'dev.patchrelay.demo.patch.DemoPatch',
    [string]$PrivateKey = '.keys/patch-signing.key',
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$BuildTools = '35.0.0'
)
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :patch:jar --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Patch compilation failed' }
$out = Join-Path $PSScriptRoot '../dist'
New-Item -ItemType Directory -Force $out | Out-Null
& "$AndroidSdk/build-tools/$BuildTools/d8.bat" --min-api 26 --lib "$AndroidSdk/platforms/android-35/android.jar" --output $out "$PSScriptRoot/../patch/build/libs/patch.jar"
if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed' }
& java "$PSScriptRoot/Publisher.java" sign $PrivateKey "$out/classes.dex" $AppId $BaseVersion $Version $EntryPoint $ArtifactUrl "$out/patch.json"
if ($LASTEXITCODE -ne 0) { throw 'Patch signing failed (use a fresh output manifest for each publication)' }
