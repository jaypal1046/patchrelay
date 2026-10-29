param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [ValidatePattern('^emulator-\d+$')][string]$Device = 'emulator-5554',
    [string]$PrivateKey = '.keys/patch-signing.key'
)
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :demo:assembleStress :patch:stressJar :patch:bundledStressJar :patch:stressClasspath --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Stress build failed' }
$dist = Join-Path $PSScriptRoot '../dist/stress'
$fixtures = Join-Path $PSScriptRoot '../demo/src/androidTestStress/assets/stress'
New-Item -ItemType Directory -Force $dist,$fixtures | Out-Null
$androidJar = "$AndroidSdk/platforms/android-35/android.jar"
$r8Jar = "$AndroidSdk/build-tools/35.0.0/lib/d8.jar"
foreach ($variant in @('thin','bundled','minified')) {
    $output = Join-Path $dist $variant
    New-Item -ItemType Directory -Force $output | Out-Null
    if ($variant -eq 'minified') {
        $arguments = @('-cp', $r8Jar, 'com.android.tools.r8.R8', '--release', '--min-api', '26', '--lib', $androidJar,
            '--pg-conf', "$PSScriptRoot/../patch/stress-rules.pro", '--pg-map-output', "$output/mapping.txt", '--output', $output)
        foreach ($library in (Get-Content "$PSScriptRoot/../patch/build/stress-classpath.txt")) { $arguments += @('--lib', $library) }
        $arguments += "$PSScriptRoot/../patch/build/libs/stress-thin.jar"
        & java @arguments
    } else {
        & "$AndroidSdk/build-tools/35.0.0/d8.bat" --min-api 26 --lib $androidJar --output $output "$PSScriptRoot/../patch/build/libs/stress-$variant.jar"
    }
    if ($LASTEXITCODE -ne 0) { throw "DEX/R8 build failed: $variant" }
    $manifest = Join-Path $output 'patch.json'
    if (Test-Path -LiteralPath $manifest) { Remove-Item -LiteralPath $manifest }
    # Offline fixtures; the URL is signed metadata only, never fetched by these tests.
    & java "$PSScriptRoot/Publisher.java" sign $PrivateKey "$output/classes.dex" dev.patchrelay.demo.stress stress-1 1 dev.patchrelay.demo.patch.ComplexPatch "https://github.com/jaypal1046/patchrelay/releases/download/stress-fixtures/$variant.dex" $manifest
    if ($LASTEXITCODE -ne 0) { throw "Signing failed: $variant" }
    Copy-Item "$output/classes.dex" "$fixtures/$variant.dex" -Force
    Copy-Item $manifest "$fixtures/$variant.json" -Force
}
& "$PSScriptRoot/../gradlew.bat" :demo:assembleStressAndroidTest -PtestBuildType=stress --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Stress test compilation failed' }
$mapping = Get-Content "$PSScriptRoot/../demo/build/outputs/mapping/stress/mapping.txt" -Raw
if ($mapping -notmatch '(?m)^\S+ -> [a-z][a-z0-9.]*:$') { throw 'No evidence of host obfuscation in R8 mapping' }
$adb = "$AndroidSdk/platform-tools/adb.exe"
& $adb -s $Device install -r "$PSScriptRoot/../demo/build/outputs/apk/stress/demo-stress.apk"
if ($LASTEXITCODE -ne 0) { throw 'Host install failed' }
& $adb -s $Device install -r "$PSScriptRoot/../demo/build/outputs/apk/androidTest/stress/demo-stress-androidTest.apk"
if ($LASTEXITCODE -ne 0) { throw 'Test install failed' }
$result = & $adb -s $Device shell am instrument -w -r -e class dev.patchrelay.demo.ComplexPatchTest dev.patchrelay.demo.stress.test/android.test.InstrumentationTestRunner
$result | Set-Content "$dist/results.txt"
$result | Select-Object -Last 16
if (($result -join "`n") -notmatch 'OK \(7 tests\)') { throw "Stress tests failed; see $dist/results.txt" }
