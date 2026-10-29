param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/PatchBuild.psm1" -Force
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }

# Fresh local keys/artifacts only; never overwrite a published fixture or use release keys.
$out = Join-Path $PSScriptRoot "../.tools/packaging-check-$([guid]::NewGuid().ToString('N'))"
New-Item -ItemType Directory -Force $out | Out-Null
& java "$PSScriptRoot/Publisher.java" keygen "$out/private.key" "$out/public.key"
if ($LASTEXITCODE -ne 0) { throw 'Test key generation failed' }
ConvertTo-PatchDex -AndroidSdk $AndroidSdk -OutputDirectory $out -Jar "$PSScriptRoot/../patch/build/libs/patch.jar"
Write-PatchManifest -PrivateKey "$out/private.key" -OutputDirectory $out -Version 1 -EntryPoint dev.patchrelay.demo.patch.DemoPatch -ArtifactUrl https://example.com/classes.dex

$envelope = Get-Content "$out/patch.json" -Raw | ConvertFrom-Json
$payload = [Convert]::FromBase64String($envelope.payload)
$metadata = [Text.Encoding]::UTF8.GetString($payload) | ConvertFrom-Json
$rsa = [Security.Cryptography.RSA]::Create()
try {
    $rsa.ImportSubjectPublicKeyInfo([Convert]::FromBase64String((Get-Content "$out/public.key" -Raw).Trim()), [ref]$null)
    if (!$rsa.VerifyData($payload, [Convert]::FromBase64String($envelope.signature), [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)) { throw 'Signature did not verify' }
} finally { $rsa.Dispose() }
if ($metadata.sha256 -ne (Get-FileHash "$out/classes.dex" -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $metadata.appId -ne 'dev.patchrelay.demo' -or $metadata.baseVersion -ne '3' -or
    $metadata.version -ne 1 -or $metadata.entryPoint -ne 'dev.patchrelay.demo.patch.DemoPatch' -or
    $metadata.artifactUrl -ne 'https://example.com/classes.dex') { throw 'Signed manifest differs from packaging inputs' }
$before = (Get-FileHash "$out/patch.json").Hash
$dexBefore = (Get-FileHash "$out/classes.dex").Hash
$rejected = $false
try {
    ConvertTo-PatchDex -AndroidSdk $AndroidSdk -OutputDirectory $out -Jar "$PSScriptRoot/../patch/build/libs/patch.jar"
} catch { $rejected = $true }
if (!$rejected -or (Get-FileHash "$out/classes.dex").Hash -ne $dexBefore) { throw 'Signed DEX was overwritten' }
$rejected = $false
try {
    Write-PatchManifest -PrivateKey "$out/private.key" -OutputDirectory $out -Version 2 -EntryPoint dev.patchrelay.demo.patch.DemoPatch -ArtifactUrl https://example.com/classes.dex
} catch { $rejected = $true }
if (!$rejected -or (Get-FileHash "$out/patch.json").Hash -ne $before) { throw 'Existing signed manifest was overwritten' }
Write-Output 'Packaging verified: DEX, signature, metadata and signed-output overwrite protection.'
exit 0
