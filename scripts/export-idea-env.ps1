param(
    [string]$RunConfiguration = 'JavspyApplication',
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9._-]*\.env$')]
    [string]$OutputName = 'javspy.env',
    [switch]$IncludeDeleteToken
)

$ErrorActionPreference = 'Stop'
Set-PSDebug -Off

$projectDir = Split-Path -Parent $PSScriptRoot
$ideaFile = Join-Path $projectDir '.idea/workspace.xml'
try {
    [xml]$ideaConfig = Get-Content -Raw -Encoding UTF8 -LiteralPath $ideaFile
} catch {
    throw 'Cannot read .idea/workspace.xml. Save the IDE run configuration before exporting.'
}

$runConfigs = @($ideaConfig.SelectNodes('/project/component[@name="RunManager"]/configuration') |
    Where-Object { $_.GetAttribute('name') -ceq $RunConfiguration })
if ($runConfigs.Count -ne 1) {
    throw 'Expected exactly one matching IntelliJ run configuration.'
}

$settings = [ordered]@{
    JAVSPY_DB_URL = ''
    JAVSPY_DB_USERNAME = ''
    JAVSPY_DB_PASSWORD = ''
    JAVSPY_EMBY_ENABLED = ''
    JAVSPY_EMBY_URL = ''
    JAVSPY_EMBY_API_KEY = ''
    JAVSPY_EMBY_DELETE_ADMIN_TOKEN = ''
    JAVSPY_JAVBUS_TOKEN = ''
}
$seen = @{}
foreach ($entry in $runConfigs[0].SelectNodes('envs/env')) {
    $key = $entry.GetAttribute('name')
    if ($settings.Contains($key)) {
        if ($seen.ContainsKey($key)) { throw "Duplicate setting: $key" }
        $seen[$key] = $true
        $settings[$key] = $entry.GetAttribute('value')
    }
}

if ($settings.JAVSPY_DB_URL -cnotmatch '^jdbc:mariadb(:[a-z-]+)?://\S+$') {
    throw 'JAVSPY_DB_URL must be set to a MariaDB JDBC URL in the IDE run configuration.'
}
if ([string]::IsNullOrWhiteSpace($settings.JAVSPY_DB_USERNAME)) {
    throw 'JAVSPY_DB_USERNAME is missing in the IDE run configuration.'
}

$settings.JAVSPY_EMBY_URL = $settings.JAVSPY_EMBY_URL.Trim().TrimEnd('/') -replace '(?i)/emby$',''
if ([string]::IsNullOrWhiteSpace($settings.JAVSPY_EMBY_ENABLED)) {
    $settings.JAVSPY_EMBY_ENABLED = if ($settings.JAVSPY_EMBY_URL -and $settings.JAVSPY_EMBY_API_KEY) { 'true' } else { 'false' }
}
$settings.JAVSPY_EMBY_ENABLED = $settings.JAVSPY_EMBY_ENABLED.Trim().ToLowerInvariant()
if ($settings.JAVSPY_EMBY_ENABLED -notin @('true', 'false')) {
    throw 'JAVSPY_EMBY_ENABLED must be true or false.'
}
if ($settings.JAVSPY_EMBY_URL -and $settings.JAVSPY_EMBY_URL -cnotmatch '^https?://\S+$') {
    throw 'JAVSPY_EMBY_URL must start with http:// or https://. Correct the IDE run configuration first.'
}
if ($settings.JAVSPY_EMBY_ENABLED -eq 'true' -and
    (-not $settings.JAVSPY_EMBY_URL -or [string]::IsNullOrWhiteSpace($settings.JAVSPY_EMBY_API_KEY))) {
    throw 'When Emby is enabled, both JAVSPY_EMBY_URL and JAVSPY_EMBY_API_KEY are required.'
}
if (-not $IncludeDeleteToken) { $settings.JAVSPY_EMBY_DELETE_ADMIN_TOKEN = '' }

$outputLines = foreach ($item in $settings.GetEnumerator()) {
    $value = [string]$item.Value
    if ($value -match '[\r\n\x00]') { throw "$($item.Key) must be a single-line value." }
    $escaped = $value.Replace('\', '\\').Replace('"', '\"').Replace('$', '\$').Replace('`', '\`')
    '{0}="{1}"' -f $item.Key, $escaped
}

$targetDir = Join-Path $projectDir 'target'
[void][System.IO.Directory]::CreateDirectory($targetDir)
$outputFile = Join-Path $targetDir $OutputName
if (Test-Path -LiteralPath $outputFile) { throw 'Output already exists. Use -OutputName to export to a new file.' }
$tempFile = Join-Path $targetDir ('.javspy-env-' + [Guid]::NewGuid().ToString('N'))
try {
    [System.IO.File]::Open($tempFile, [System.IO.FileMode]::CreateNew).Dispose()
    $access = [System.Security.AccessControl.FileSecurity]::new()
    $access.SetAccessRuleProtection($true, $false)
    $exporterId = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $access.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($exporterId, 'FullControl', 'Allow'))
    Set-Acl -LiteralPath $tempFile -AclObject $access
    [System.IO.File]::WriteAllText($tempFile, ($outputLines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
    [System.IO.File]::Move($tempFile, $outputFile)
} finally {
    if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile }
}

Write-Output "Exported to $outputFile. The file contains credentials; transfer it securely and do not commit it."
