param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$Tag,
    [Parameter(Mandatory=$true)][string]$NotesFile,
    [switch]$PreRelease,
    [string[]]$Files = @('demo/build/outputs/apk/release/demo-release.apk', 'sdk/build/outputs/aar/sdk-release.aar', 'core/build/outputs/aar/core-release.aar', 'dist/classes.dex', 'dist/patch.json')
)
$ErrorActionPreference = 'Stop'
if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Expected GitHub owner/repository' }
foreach ($file in $Files) { if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing release asset: $file" } }
$notes = Get-Content -LiteralPath $NotesFile -Raw
$token = $env:GITHUB_TOKEN
if (!$token) {
    $env:GCM_INTERACTIVE = 'Never'
    $lines = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Sign in to GitHub through Git Credential Manager or set GITHUB_TOKEN' }
    foreach ($line in $lines) { if ($line.StartsWith('password=')) { $token = $line.Substring(9) } }
}
if (!$token) { throw 'No GitHub credential available' }
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/vnd.github+json' }
$api = "https://api.github.com/repos/$Repository/releases"
$body = @{ tag_name = $Tag; target_commitish = 'main'; name = "PatchRelay $Tag — Android prototype"; body = $notes; draft = $true; prerelease = [bool]$PreRelease } | ConvertTo-Json
$release = Invoke-RestMethod $api -Method Post -Headers $headers -ContentType application/json -Body $body
# Failed uploads leave a draft; no incomplete release becomes publicly downloadable.
foreach ($file in $Files) {
    $name = [Uri]::EscapeDataString([IO.Path]::GetFileName($file))
    $upload = "https://uploads.github.com/repos/$Repository/releases/$($release.id)/assets?name=$name"
    $asset = Invoke-RestMethod $upload -Method Post -Headers $headers -ContentType application/octet-stream -InFile $file
    Write-Output "Uploaded $($asset.name)"
}
$publishBody = @{ draft=$false; make_latest=$(if ($PreRelease) { 'false' } else { 'true' }) } | ConvertTo-Json
$published = Invoke-RestMethod "$api/$($release.id)" -Method Patch -Headers $headers -ContentType application/json -Body $publishBody
Write-Output "Published: $($published.html_url)"
