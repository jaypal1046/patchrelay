$ErrorActionPreference = 'Stop'

function ConvertTo-PatchDex {
    param(
        [Parameter(Mandatory)][string]$Jar,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [Parameter(Mandatory)][string]$AndroidSdk,
        [string]$BuildTools = '35.0.0'
    )
    if (Test-Path -LiteralPath "$OutputDirectory/patch.json") { throw 'Signed output already exists; choose a fresh output directory' }
    New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
    & "$AndroidSdk/build-tools/$BuildTools/d8.bat" --min-api 26 --lib "$AndroidSdk/platforms/android-35/android.jar" --output $OutputDirectory $Jar
    if ($LASTEXITCODE -ne 0) { throw "DEX compilation failed: $Jar" }
}

function Write-PatchManifest {
    param(
        [Parameter(Mandatory)][string]$PrivateKey,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [Parameter(Mandatory)][long]$Version,
        [Parameter(Mandatory)][string]$EntryPoint,
        [Parameter(Mandatory)][string]$ArtifactUrl,
        [string]$AppId = 'dev.patchrelay.demo',
        [string]$BaseVersion = '3'
    )
    & java "$PSScriptRoot/Publisher.java" sign $PrivateKey "$OutputDirectory/classes.dex" $AppId $BaseVersion $Version $EntryPoint $ArtifactUrl "$OutputDirectory/patch.json"
    if ($LASTEXITCODE -ne 0) { throw 'Patch signing failed; use a fresh manifest path for each publication' }
}

Export-ModuleMember -Function ConvertTo-PatchDex, Write-PatchManifest
