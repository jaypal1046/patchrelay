param(
    [string]$Serial = 'emulator-5580',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [switch]$SmokeOnly,
    [switch]$DemoWorkflow,
    [ValidateSet('baseline','patch')][string]$Edition = 'baseline',
    [long]$Version = 0
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Only an explicit emulator serial is accepted; phones are prohibited.' }
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$qemu = (& $adb -s $Serial shell getprop ro.kernel.qemu) -join ''
if ($LASTEXITCODE -ne 0 -or $qemu -ne '1') { throw 'Target is not a verified Android emulator.' }
if ($SmokeOnly -and $DemoWorkflow) { throw 'Choose either SDK smoke or demo workflow.' }
if ($DemoWorkflow) {
    foreach ($relative in @('../demo/build/outputs/apk/release/demo-release.apk', '../demo/build/outputs/apk/androidTest/release/demo-release-androidTest.apk')) {
        $demoApk = Join-Path $PSScriptRoot $relative
        if (!(Test-Path -LiteralPath $demoApk)) { throw 'Build :demo:assembleRelease :demo:assembleReleaseAndroidTest first.' }
        & $adb -s $Serial install -r $demoApk
        if ($LASTEXITCODE -ne 0) { throw 'Emulator demo APK installation failed.' }
    }
    foreach ($action in @('generated_workflow', 'generated_restore')) {
        if ($action -eq 'generated_restore') {
            & $adb -s $Serial shell am force-stop dev.patchrelay.screens.demo
            if ($LASTEXITCODE -ne 0) { throw 'Emulator demo process stop failed.' }
        }
        $result = & $adb -s $Serial shell am instrument -w -e action $action -e edition $Edition -e version "$Version" dev.patchrelay.screens.demo.test/dev.patchrelay.screens.demo.SilentRecoveryInstrumentation
        $result | Write-Output
        if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch [regex]::Escape("OK ($action)")) { throw "Emulator demo $action failed." }
    }
    return
}
$apk = Join-Path $PSScriptRoot '../sdk/build/outputs/apk/androidTest/debug/sdk-debug-androidTest.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'Build :sdk:assembleDebugAndroidTest before running.' }
& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Emulator test APK installation failed.' }
$instrumentArgs = @('-s', $Serial, 'shell', 'am', 'instrument', '-w')
if ($SmokeOnly) { $instrumentArgs += @('-e', 'workerSmokeOnly', 'true') }
$instrumentArgs += 'dev.patchrelay.test/dev.patchrelay.screens.SandboxInstrumentation'
$result = & $adb @instrumentArgs
$result | Write-Output
$expected = if ($SmokeOnly) { 'OK \(worker smoke\)' } else { 'OK \(1 test\)' }
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch $expected) { throw 'Emulator instrumentation failed.' }
