param(
    [Parameter(Mandatory=$true)][ValidateSet('r8host','r8patch','assets','filedata','missingdep','newdep','upgrade')][string]$Case,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9.:_-]+$')][string]$Device,
    [string]$AndroidSdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
$adb = "$AndroidSdk/platform-tools/adb.exe"
$out = Join-Path $PSScriptRoot "../.tools/visual-phone/$Case"
New-Item -ItemType Directory -Force $out | Out-Null
function Launch([string]$package) {
    & $adb -s $Device shell am start -n "$package/dev.patchrelay.visual.MainActivity" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Cannot open $package" }
}
function Screen([string]$package) {
    & $adb -s $Device shell uiautomator dump /sdcard/patchrelay-pair.xml | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect screen' }
    $raw = & $adb -s $Device shell cat /sdcard/patchrelay-pair.xml
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read screen' }
    [xml]$xml = $raw
    $nodes = $xml.SelectNodes("//node[@package='$package']")
    if ($nodes.Count -eq 0) { throw "Expected $package in foreground; no button tapped" }
    return @{ text = ($nodes | ForEach-Object { $_.text }) -join "`n"; nodes = $nodes; raw = $raw }
}
function WaitScreen([string]$package, [string]$pattern) {
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        $screen = Screen $package
        if ($screen.text -match $pattern) { return $screen }
        if ($screen.text -match 'Download failed:|Startup failed:') { throw $screen.text }
        Start-Sleep -Milliseconds 1000
    }
    throw "Expected screen pattern not reached: $pattern"
}
function TapButton([string]$package, [string]$id) {
    $screen = Screen $package
    $button = $screen.nodes | Where-Object { $_.'resource-id' -eq $id -and $_.enabled -eq 'true' } | Select-Object -First 1
    if (!$button -or $button.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw "Visible enabled button missing: $id" }
    $x = [int]( ([int]$Matches[1] + [int]$Matches[3]) / 2 )
    $y = [int]( ([int]$Matches[2] + [int]$Matches[4]) / 2 )
    & $adb -s $Device shell input tap $x $y
    if ($LASTEXITCODE -ne 0) { throw 'Button tap failed' }
}
function Screenshot([string]$name) {
    & $adb -s $Device shell screencap -p /sdcard/patchrelay-pair.png
    if ($LASTEXITCODE -ne 0) { throw 'Screenshot failed' }
    & $adb -s $Device pull /sdcard/patchrelay-pair.png "$out/$name.png" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Screenshot copy failed' }
}
$before = "dev.patchrelay.visual.$Case.before"
$patch = "dev.patchrelay.visual.$Case.patch"
Launch $before
$baseline = WaitScreen $before 'Active patch: 0'
$baseline.text | Set-Content "$out/before.txt"
Screenshot 'before'
Launch $patch
$initial = WaitScreen $patch 'Active patch: 0'
$initial.text | Set-Content "$out/patch-initial.txt"
$installedBefore = (& $adb -s $Device shell dumpsys package $patch | Select-String 'lastUpdateTime=').ToString().Trim()
if (!$installedBefore) { throw 'Cannot capture initial install timestamp' }
TapButton $patch 'android:id/button1'
$downloaded = WaitScreen $patch 'Downloaded and signature verified'
$downloaded.text | Set-Content "$out/downloaded.txt"
TapButton $patch 'android:id/button2'
Start-Sleep -Milliseconds 500
Launch $patch
$updated = WaitScreen $patch 'Active patch: 1'
$expected = switch ($Case) {
    'r8host' { 'Complex Kotlin patch: hello phone' }
    'r8patch' { 'Complex Kotlin patch: hello phone' }
    'assets' { 'asset-not-present-in-dex' }
    'filedata' { '"revision":2' }
    'missingdep' { 'NoClassDefFoundError[\s\S]*commons[/\.]codec' }
    'newdep' { '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824' }
    'upgrade' { 'NoSuchMethodError[\s\S]*Host Gson remains 2\.8\.9' }
}
if ($updated.text -notmatch $expected) { $updated.text | Set-Content "$out/unexpected.txt"; throw "Unexpected visible result for $Case" }
$updated.text | Set-Content "$out/after.txt"
Screenshot 'after'
$installedAfter = (& $adb -s $Device shell dumpsys package $patch | Select-String 'lastUpdateTime=').ToString().Trim()
if ($installedBefore -ne $installedAfter) { throw 'App install timestamp changed during patch test' }
"$installedBefore`n$installedAfter" | Set-Content "$out/install-times.txt"
Write-Output "VISIBLE RESULT $Case : $($updated.text)"
if ($Case -eq 'filedata') {
    TapButton $patch 'android:id/button2'
    Start-Sleep -Milliseconds 500
    Launch $patch
    $rolledBack = WaitScreen $patch 'Active patch: 0'
    if ($rolledBack.text -notmatch '"revision":2') { throw 'File rollback result differs from expected persistence' }
    $rolledBack.text | Set-Content "$out/rollback.txt"
    Screenshot 'rollback'
    Write-Output 'VISIBLE ROLLBACK: code patch 0; private file still revision 2.'
}
Write-Output "Verified $Case : actual screens, GitHub download, same APK installation."
