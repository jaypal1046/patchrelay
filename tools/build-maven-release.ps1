param([switch]$CreateSigningKey, [switch]$ResumeKeySetup, [switch]$Diagnostic)
$ErrorActionPreference = 'Stop'
$releaseRoot = Split-Path $PSScriptRoot -Parent
$releaseKeys = Join-Path $releaseRoot '.keys'
$releaseGpgHome = Join-Path $releaseKeys 'maven-gnupg'
$releaseMetadata = Join-Path $releaseKeys 'maven-signing.json'
$releasePassFile = Join-Path $releaseKeys 'maven-passphrase.dpapi'
$releasePrivateFile = Join-Path $releaseKeys 'maven-signing-private.asc'
$releasePublicFile = Join-Path $releaseKeys 'maven-signing-public.asc'
$releaseGpg = (Get-Command gpg -ErrorAction SilentlyContinue).Source
if (!$releaseGpg) { $releaseGpg = 'C:\Program Files\Git\usr\bin\gpg.exe' }
if (!(Test-Path -LiteralPath $releaseGpg)) { throw 'GnuPG is required; no key was created.' }
$releaseGpgHomeArg = $releaseGpgHome
$releaseCygpath = Join-Path (Split-Path $releaseGpg) 'cygpath.exe'
if (Test-Path -LiteralPath $releaseCygpath) {
    $releaseGpgHomeArg = & $releaseCygpath -u $releaseGpgHome
    if ($LASTEXITCODE -ne 0) { throw 'Cannot convert the Git GnuPG home path.' }
}
if (!$IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw 'This local key setup uses Windows DPAPI. Use CI secret-based signing on other platforms.' }
New-Item -ItemType Directory -Force -Path $releaseKeys | Out-Null

if ($CreateSigningKey -and $ResumeKeySetup) { throw 'Choose creation or explicit resume, not both.' }
if ($CreateSigningKey -or $ResumeKeySetup) {
    if ($ResumeKeySetup) {
        if (!(Test-Path -LiteralPath $releasePassFile) -or !(Test-Path -LiteralPath $releaseGpgHome) -or
            (Test-Path -LiteralPath $releaseMetadata) -or (Test-Path -LiteralPath $releasePrivateFile) -or (Test-Path -LiteralPath $releasePublicFile)) {
            throw 'Resume only supports the protected, incomplete key-generation stage; nothing was overwritten.'
        }
        $releaseSecureResume = Get-Content -LiteralPath $releasePassFile -Raw | ConvertTo-SecureString
        $releaseResumePointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($releaseSecureResume)
        try { $releaseSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($releaseResumePointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($releaseResumePointer) }
    } else {
    foreach ($releaseExisting in @($releaseMetadata, $releasePassFile, $releasePrivateFile, $releasePublicFile, $releaseGpgHome)) {
        if (Test-Path -LiteralPath $releaseExisting) { throw 'Publication key setup already exists or is incomplete; inspect it rather than overwriting.' }
    }
    New-Item -ItemType Directory -Path $releaseGpgHome | Out-Null
    $releaseRandom = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($releaseRandom)
    $releaseSecret = [Convert]::ToBase64String($releaseRandom)
    $releaseProtected = ConvertTo-SecureString -String $releaseSecret -AsPlainText -Force | ConvertFrom-SecureString
    [IO.File]::WriteAllText($releasePassFile, $releaseProtected)
    }
    $releaseBeforeKeys = & $releaseGpg --homedir $releaseGpgHomeArg --batch --with-colons --list-secret-keys
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the publication key store.' }
    if (!($releaseBeforeKeys | Where-Object { $_.StartsWith('sec:') })) {
    $releaseSecret | & $releaseGpg --homedir $releaseGpgHomeArg --batch --pinentry-mode loopback --passphrase-fd 0 --quick-generate-key 'PatchRelay Maven Signing (io.github.jaypal1046)' rsa3072 sign 2y
    if ($LASTEXITCODE -ne 0) { throw 'PGP key generation failed; preserve the partial setup for diagnosis.' }
    }
    $releaseKeyLines = & $releaseGpg --homedir $releaseGpgHomeArg --batch --with-colons --list-secret-keys
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the generated publication key.' }
    $releaseFingerprint = (($releaseKeyLines | Where-Object { $_.StartsWith('fpr:') } | Select-Object -First 1) -split ':')[9]
    if ($releaseFingerprint -notmatch '^[A-F0-9]{40}$') { throw 'Unexpected publication key fingerprint.' }
    $releasePrivate = $releaseSecret | & $releaseGpg --homedir $releaseGpgHomeArg --batch --pinentry-mode loopback --passphrase-fd 0 --armor --export-secret-keys $releaseFingerprint
    if ($LASTEXITCODE -ne 0) { throw 'Cannot export the protected publication key.' }
    [IO.File]::WriteAllText($releasePrivateFile, ($releasePrivate -join "`n") + "`n")
    $releasePublic = & $releaseGpg --homedir $releaseGpgHomeArg --batch --armor --export $releaseFingerprint
    if ($LASTEXITCODE -ne 0) { throw 'Cannot export the public verification key.' }
    [IO.File]::WriteAllText($releasePublicFile, ($releasePublic -join "`n") + "`n")
    # Git's MSYS GnuPG preserves the CR from Windows text-pipeline CRLF input.
    $releaseSuffix = if (Test-Path -LiteralPath $releaseCygpath) { 'CR' } else { 'none' }
    [IO.File]::WriteAllText($releaseMetadata, (@{fingerprint=$releaseFingerprint; identity='PatchRelay Maven Signing (io.github.jaypal1046)'; passphraseSuffix=$releaseSuffix; createdUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json))
    Write-Output "Created protected publication key: $releaseFingerprint"
}

foreach ($releaseRequired in @($releaseMetadata, $releasePassFile, $releasePrivateFile, $releasePublicFile)) {
    if (!(Test-Path -LiteralPath $releaseRequired)) { throw 'Publication key setup is missing. Run once with -CreateSigningKey.' }
}
$releaseSecure = Get-Content -LiteralPath $releasePassFile -Raw | ConvertTo-SecureString
$releasePointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($releaseSecure)
$releaseOldKey = $env:MAVEN_SIGNING_KEY
$releaseOldPassword = $env:MAVEN_SIGNING_PASSWORD
try {
    $env:MAVEN_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($releasePointer)
    $releaseKeySettings = Get-Content -LiteralPath $releaseMetadata -Raw | ConvertFrom-Json
    if ($releaseKeySettings.passphraseSuffix -eq 'CR') { $env:MAVEN_SIGNING_PASSWORD += "`r" }
    $env:MAVEN_SIGNING_KEY = [IO.File]::ReadAllText($releasePrivateFile)
    Push-Location $releaseRoot
    try {
        $releaseArguments = @(':sdk:centralBundle', '--no-daemon', '--console=plain', '--max-workers=2')
        if ($Diagnostic) { $releaseArguments += '--stacktrace' }
        & (Join-Path $releaseRoot 'gradlew.bat') @releaseArguments
        if ($LASTEXITCODE -ne 0) { throw 'Signed Maven bundle build failed.' }
    } finally { Pop-Location }
} finally {
    $env:MAVEN_SIGNING_KEY = $releaseOldKey
    $env:MAVEN_SIGNING_PASSWORD = $releaseOldPassword
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($releasePointer)
    $releaseSecret = $null
    $releasePrivate = $null
}
Write-Output 'Signed bundle is under sdk/build/central/. Private key/passphrase were not printed or uploaded.'
