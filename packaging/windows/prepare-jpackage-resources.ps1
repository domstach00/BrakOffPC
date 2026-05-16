param(
    [Parameter(Mandatory = $true)]
    [string] $JavaHome,

    [Parameter(Mandatory = $true)]
    [string] $OutputDirectory
)

$ErrorActionPreference = "Stop"

$jmod = Join-Path $JavaHome "jmods\jdk.jpackage.jmod"
$jmodTool = Join-Path $JavaHome "bin\jmod.exe"

if (-not (Test-Path $jmod)) {
    throw "jdk.jpackage.jmod not found at $jmod"
}

if (-not (Test-Path $jmodTool)) {
    throw "jmod.exe not found at $jmodTool"
}

if (Test-Path $OutputDirectory) {
    Remove-Item $OutputDirectory -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$extractDirectory = Join-Path $OutputDirectory "_jmod"
New-Item -ItemType Directory -Force -Path $extractDirectory | Out-Null

& $jmodTool extract --dir $extractDirectory $jmod
if ($LASTEXITCODE -ne 0) {
    throw "jmod extraction failed with exit code $LASTEXITCODE"
}

$sourceMainWxs = Join-Path $extractDirectory "classes\jdk\jpackage\internal\resources\main.wxs"
if (-not (Test-Path $sourceMainWxs)) {
    throw "jpackage main.wxs resource not found in extracted jmod."
}

$mainWxs = Get-Content -Raw -Path $sourceMainWxs

$cleanupCustomAction = @'
  <CustomAction Id="JpRemoveBrakOffPCAutoStartScript"
                Directory="TARGETDIR"
                Execute="immediate"
                Return="ignore"
                ExeCommand='&quot;[SystemFolder]cmd.exe&quot; /c if exist &quot;[StartupFolder]BrakOffPC.vbs&quot; del /f /q &quot;[StartupFolder]BrakOffPC.vbs&quot;' />
'@

$startupDirectory = @'
  <Directory Id="TARGETDIR" Name="SourceDir">
    <Directory Id="StartupFolder"/>
  </Directory>
'@

$cleanupSequenceEntry = @'
    <Custom Action="JpRemoveBrakOffPCAutoStartScript" Before="RemoveFiles">REMOVE="ALL" AND NOT UPGRADINGPRODUCTCODE</Custom>
'@

$originalMainWxs = $mainWxs
$mainWxs = $mainWxs.Replace('  <!-- Standard required root -->', "$cleanupCustomAction`r`n`r`n  <!-- Standard required root -->")
$mainWxs = $mainWxs.Replace('  <Directory Id="TARGETDIR" Name="SourceDir"/>', $startupDirectory)
$mainWxs = $mainWxs.Replace('  </InstallExecuteSequence>', "$cleanupSequenceEntry`r`n  </InstallExecuteSequence>")

if ($mainWxs -eq $originalMainWxs) {
    throw "No jpackage WiX resource changes were applied."
}

foreach ($expected in @(
        "JpRemoveBrakOffPCAutoStartScript",
        '<Directory Id="StartupFolder"/>',
        'NOT UPGRADINGPRODUCTCODE'
    )) {
    if (-not $mainWxs.Contains($expected)) {
        throw "Patched jpackage WiX resource is missing expected content: $expected"
    }
}

$targetMainWxs = Join-Path $OutputDirectory "main.wxs"
[System.IO.File]::WriteAllText($targetMainWxs, $mainWxs, [System.Text.UTF8Encoding]::new($false))

Remove-Item $extractDirectory -Recurse -Force
