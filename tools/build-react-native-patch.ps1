param(
    [long]$Version = 1,
    [string]$Tag = 'react-native-android-v1',
    [string]$Repository = 'jaypal1046/patchrelay',
    [string]$PrivateKey = '.keys/patch-signing.key'
)
$ErrorActionPreference = 'Stop'
if ($Version -le 0 -or $Tag -notmatch '^[A-Za-z0-9._-]+$' -or $Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Invalid version, tag or repository' }
$example = Join-Path $PSScriptRoot '../examples/react-native'
$out = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../dist/$Tag"))
if (Test-Path -LiteralPath "$out/patch.json") { throw 'Signed output exists; use a new release tag' }
New-Item -ItemType Directory -Force $out | Out-Null
Push-Location $example
try {
    & npx --no-install react-native bundle --platform android --dev false --max-workers 2 --entry-file index.patch.js --bundle-output "$out/index.android.bundle" --assets-dest "$out/assets"
    if ($LASTEXITCODE -ne 0) { throw 'React Native bundle failed; run npm ci in examples/react-native first' }
} finally { Pop-Location }
if ((Test-Path "$out/assets") -and (Get-ChildItem "$out/assets" -File -Recurse)) { throw 'This first adapter supports JS-only bundles; downloaded asset packs are not implemented' }
& java "$PSScriptRoot/Publisher.java" sign-js $PrivateKey "$out/index.android.bundle" com.patchrelayreactdemo rn-0.82.1-hermes-base1 $Version react-native-android "https://github.com/$Repository/releases/download/$Tag/index.android.bundle" "$out/patch.json"
if ($LASTEXITCODE -ne 0) { throw 'React Native signing failed' }
Write-Output "Signed React Native Android bundle ready in $out. Native APK not changed or installed."
