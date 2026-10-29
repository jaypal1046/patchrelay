param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$PrivateKey = '.keys/patch-signing.key')
$ErrorActionPreference = 'Stop'
if (!$AndroidSdk) { throw 'Set ANDROID_HOME or pass -AndroidSdk' }
& "$PSScriptRoot/../gradlew.bat" :visual:assembleRelease --console=plain --max-workers=2
if ($LASTEXITCODE -ne 0) { throw 'Visual app build failed' }
$out = Join-Path $PSScriptRoot '../dist/visual'
New-Item -ItemType Directory -Force $out | Out-Null
$cases = Get-Content "$PSScriptRoot/../visual/cases.json" -Raw | ConvertFrom-Json
$dexSources = @{ bundled = 'bundled'; thin = 'thin'; r8 = 'minified' }
foreach ($name in $dexSources.Keys) {
    $source = "$PSScriptRoot/../dist/stress/$($dexSources[$name])/classes.dex"
    if (!(Test-Path -LiteralPath $source)) { throw 'Run tools/test-complex.ps1 first to generate the three patch artifacts' }
    Copy-Item $source "$out/$name.dex" -Force
}
$publisher = Join-Path $PSScriptRoot '../.tools/publisher-classes'
New-Item -ItemType Directory -Force $publisher | Out-Null
& javac -d $publisher "$PSScriptRoot/Publisher.java"
if ($LASTEXITCODE -ne 0) { throw 'Publisher compilation failed' }
$index = @()
$number = 0
foreach ($case in $cases) {
    $number++
    foreach ($edition in @('before','patch')) {
        $flavor = $case.id + (Get-Culture).TextInfo.ToTitleCase($edition)
        $source = "$PSScriptRoot/../visual/build/outputs/apk/$flavor/release/visual-$flavor-release.apk"
        $name = "$number-$($case.id)-$edition.apk"
        $package = "dev.patchrelay.visual.$($case.id).$edition"
        $badging = & "$AndroidSdk/build-tools/35.0.0/aapt.exe" dump badging $source
        if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='$([regex]::Escape($package))'") { throw "Wrong APK package: $source" }
        # Prove both installations contain base code only, not the future patch implementation.
        $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $source))
        try {
            foreach ($entry in ($archive.Entries | Where-Object FullName -Match '^classes\d*\.dex$')) {
                $memory = [IO.MemoryStream]::new()
                $stream = $entry.Open()
                try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
                $text = [Text.Encoding]::Latin1.GetString($memory.ToArray())
                $memory.Dispose()
                if ($text.Contains('Ldev/patchrelay/demo/patch/ComplexPatch;')) { throw "Patched code already embedded: $source" }
            }
        } finally { $archive.Dispose() }
        Copy-Item $source "$out/$name" -Force
        $index += [pscustomobject]@{ number=$number; case=$case.id; title=$case.title; edition=$edition; package=$package; apk=$name; sha256=(Get-FileHash "$out/$name" -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    $manifest = "$out/$($case.id).json"
    if (Test-Path -LiteralPath $manifest) { Remove-Item -LiteralPath $manifest }
    & java -cp $publisher Publisher sign $PrivateKey "$out/$($case.artifact).dex" "dev.patchrelay.visual.$($case.id).patch" visual-1 1 dev.patchrelay.demo.patch.ComplexPatch "https://github.com/jaypal1046/patchrelay/releases/download/visual-tests-v1/$($case.artifact).dex" $manifest
    if ($LASTEXITCODE -ne 0) { throw "Manifest signing failed: $($case.id)" }
}
$index | ConvertTo-Json | Set-Content "$out/apps.json"
Write-Output "Prepared $($index.Count) base-code APKs, seven signed manifests, and three patch artifacts in $out"
