param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :patch:embeddedUpgradeJar :patch:stressClasspath --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Upgrade patch build failed' }
$libraries = Get-Content "$PSScriptRoot/../patch/build/stress-classpath.txt"
$sdkJar = $libraries | Where-Object { $_ -like '*sdk*classes.jar' } | Select-Object -First 1
$oldRuntime = $libraries | Where-Object { $_ -like '*kotlin-stdlib-1.9.23.jar' } | Select-Object -First 1
if (!$sdkJar -or !$oldRuntime) { throw 'Expected host classpath missing' }
$jar = "$PSScriptRoot/../patch/build/libs/embedded-upgrade.jar"
& java -cp "$sdkJar;$oldRuntime" "$PSScriptRoot/CheckEmbeddedUpgrade.java" $jar $sdkJar
if ($LASTEXITCODE -ne 0) { throw 'Classloader checks failed' }
$out = Join-Path $PSScriptRoot '../dist/embedded-upgrade-7'
New-Item -ItemType Directory -Force $out | Out-Null
& "$AndroidSdk/build-tools/35.0.0/d8.bat" --min-api 26 --lib "$AndroidSdk/platforms/android-35/android.jar" --output $out $jar
if ($LASTEXITCODE -ne 0) { throw 'DEX build failed' }
$dex = [Text.Encoding]::Latin1.GetString([IO.File]::ReadAllBytes((Join-Path $out 'classes.dex')))
foreach ($descriptor in @('Ldev/patchrelay/demo/patch/EmbeddedUpgradePatch;', 'Lkotlin/KotlinVersion;')) {
    if (!$dex.Contains($descriptor)) { throw "Missing class: $descriptor" }
}
Invoke-WebRequest https://raw.githubusercontent.com/JetBrains/kotlin/v2.0.21/license/LICENSE.txt -OutFile "$out/LICENSE.txt" -TimeoutSec 30
Invoke-WebRequest https://raw.githubusercontent.com/JetBrains/kotlin/v2.0.21/license/NOTICE.txt -OutFile "$out/NOTICE.txt" -TimeoutSec 30
& java "$PSScriptRoot/Publisher.java" sign $PrivateKey "$out/classes.dex" dev.patchrelay.demo 3 7 dev.patchrelay.demo.patch.EmbeddedUpgradePatch https://github.com/jaypal1046/patchrelay/releases/download/demo-patch-7/classes.dex "$out/patch.json"
if ($LASTEXITCODE -ne 0) { throw 'Signing failed; manifests are not overwritten' }
Write-Output 'Test 7 patch ready. No APK built or installed.'
