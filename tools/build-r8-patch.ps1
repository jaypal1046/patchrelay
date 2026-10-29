param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :patch:testStressUnitTest :patch:stressJar :patch:stressClasspath --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'R8 patch source checks failed' }
$out = Join-Path $PSScriptRoot '../dist/r8-patch-3'
New-Item -ItemType Directory -Force $out | Out-Null
$arguments = @('-cp', "$AndroidSdk/build-tools/35.0.0/lib/d8.jar", 'com.android.tools.r8.R8', '--release', '--no-data-resources', '--min-api', '26',
    '--lib', "$AndroidSdk/platforms/android-35/android.jar", '--pg-conf', "$PSScriptRoot/../patch/r8-patch-rules.pro",
    '--pg-map-output', "$out/mapping.txt", '--output', $out)
foreach ($library in (Get-Content "$PSScriptRoot/../patch/build/stress-classpath.txt")) { $arguments += @('--lib', $library) }
$arguments += "$PSScriptRoot/../patch/build/libs/stress-thin.jar"
& java @arguments
if ($LASTEXITCODE -ne 0) { throw 'R8 compilation failed' }
$mapping = Get-Content "$out/mapping.txt" -Raw
if ($mapping -notmatch '(?m)^dev\.patchrelay\.demo\.patch\.PatchFormatter -> ([^:]+):\r?$') { throw 'Formatter missing from mapping' }
$renamed = $Matches[1]
if ($renamed -eq 'dev.patchrelay.demo.patch.PatchFormatter') { throw 'Helper was not obfuscated' }
$dex = [Text.Encoding]::Latin1.GetString([IO.File]::ReadAllBytes((Join-Path $out 'classes.dex')))
if (!$dex.Contains('Ldev/patchrelay/demo/patch/R8Patch;')) { throw 'Patch entry point was renamed or removed' }
$descriptor = 'L' + $renamed.Replace('.', '/') + ';'
if (!$dex.Contains($descriptor) -or $dex.Contains('Ldev/patchrelay/demo/patch/PatchFormatter;')) { throw 'DEX does not match helper mapping' }
& java "$PSScriptRoot/Publisher.java" sign $PrivateKey "$out/classes.dex" dev.patchrelay.demo 3 3 dev.patchrelay.demo.patch.R8Patch https://github.com/jaypal1046/patchrelay/releases/download/demo-patch-3/classes.dex "$out/patch.json"
if ($LASTEXITCODE -ne 0) { throw 'Signing failed; manifests are not overwritten' }
Write-Output "R8 verified: helper renamed to $renamed; reflective entry point preserved. No APK built or installed."
