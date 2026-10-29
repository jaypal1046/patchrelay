param(
    [Parameter(Mandatory=$true)][string]$ArtifactUrl,
    [long]$Version = 1,
    [string]$AppId = 'dev.patchrelay.demo',
    [string]$BaseVersion = '3',
    [string]$EntryPoint = 'dev.patchrelay.demo.patch.DemoPatch',
    [string]$PrivateKey = '.keys/patch-signing.key',
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$BuildTools = '35.0.0',
    [string]$OutputDirectory = "$PSScriptRoot/../dist"
)
$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/PatchBuild.psm1" -Force
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :patch:jar --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Patch compilation failed' }
$out = $OutputDirectory
ConvertTo-PatchDex -AndroidSdk $AndroidSdk -BuildTools $BuildTools -OutputDirectory $out -Jar "$PSScriptRoot/../patch/build/libs/patch.jar"
Write-PatchManifest -PrivateKey $PrivateKey -OutputDirectory $out -AppId $AppId -BaseVersion $BaseVersion -Version $Version -EntryPoint $EntryPoint -ArtifactUrl $ArtifactUrl
