# Builds ds_ysm_compat without Gradle (offline fallback).
#
# Why: ModDevGradle needs to download the NeoForge "userdev" artifact from
# https://maven.neoforged.net , which currently fails with a TLS "Connection reset"
# on this machine. This script compiles the same sources against the NeoForge 1.21.1
# runtime jars that are already installed in the Minecraft libraries folder.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\build-no-gradle.ps1
#   powershell -ExecutionPolicy Bypass -File .\build-no-gradle.ps1 -LibrariesDir "D:\...\libraries"
#
# Dragon Survival and its bundled MixinSquared API are compile-only dependencies.

param(
    [string]$ProjectDir = $PSScriptRoot,
    [string]$LibrariesDir = "$env:APPDATA\.minecraft\libraries",
    [string]$DragonSurvivalJar = "",
    [string]$JavaHome = "",
    [string]$NeoForgeVersion = "21.1.249",
    [string]$McClientJar = "net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-srg.jar"
)

$ErrorActionPreference = "Stop"

if (-not $JavaHome) {
    foreach ($candidate in @(
        "C:\Program Files\Java\jdk-21.0.12.1",
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1",
        "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"
    )) {
        if (Test-Path (Join-Path $candidate "bin\javac.exe")) {
            $JavaHome = $candidate
            break
        }
    }
}

$javac = Join-Path $JavaHome "bin\javac.exe"
$jarTool = Join-Path $JavaHome "bin\jar.exe"
if (-not (Test-Path $javac)) {
    throw "javac.exe not found. Pass -JavaHome <path-to-jdk-21>."
}
if (-not $DragonSurvivalJar) {
    throw "Pass -DragonSurvivalJar <path-to-Dragon-Survival-jar>."
}
if (-not (Test-Path -LiteralPath $DragonSurvivalJar -PathType Leaf)) {
    throw "Missing Dragon Survival jar: $DragonSurvivalJar"
}

# Use the MixinSquared API already bundled with the target Dragon Survival jar.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$dependencyDir = Join-Path $ProjectDir "build\manual-dependencies"
New-Item -ItemType Directory -Force -Path $dependencyDir | Out-Null
$dragonArchive = [System.IO.Compression.ZipFile]::OpenRead($DragonSurvivalJar)
try {
    $mixinSquaredEntry = $dragonArchive.Entries | Where-Object FullName -like 'META-INF/jarjar/mixinsquared-forge-*.jar' | Select-Object -First 1
    if (-not $mixinSquaredEntry) {
        throw "MixinSquared was not found inside $DragonSurvivalJar"
    }
    $mixinSquaredArchive = [System.IO.Compression.ZipArchive]::new(
        $mixinSquaredEntry.Open(), [System.IO.Compression.ZipArchiveMode]::Read)
    try {
        $apiEntry = $mixinSquaredArchive.Entries | Where-Object FullName -like 'META-INF/jars/MixinSquared-*.jar' | Select-Object -First 1
        if (-not $apiEntry) {
            throw "MixinSquared API was not found inside $($mixinSquaredEntry.Name)"
        }
        $mixinSquaredJar = Join-Path $dependencyDir $apiEntry.Name
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($apiEntry, $mixinSquaredJar, $true)
    } finally {
        $mixinSquaredArchive.Dispose()
    }
} finally {
    $dragonArchive.Dispose()
}

# --- project metadata -------------------------------------------------------
$props = @{}
Get-Content (Join-Path $ProjectDir "gradle.properties") | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z0-9_.]+)\s*=\s*(.*)$') {
        $props[$matches[1]] = $matches[2].Trim()
    }
}

function Get-Prop([string]$name, [string]$fallback) {
    if ($props.ContainsKey($name) -and $props[$name]) { return $props[$name] }
    return $fallback
}

$modId = Get-Prop "mod_id" "ds_ysm_compat"
$modVersion = Get-Prop "mod_version" "1.0.0"
$modName = Get-Prop "mod_name" "DS x YSM Compat"
$modLicense = Get-Prop "mod_license" "MIT"
$modLogo = if (Test-Path -LiteralPath (Join-Path $ProjectDir "src\main\resources\ds_ysm_compat_logo.png") -PathType Leaf) {
    'logoFile="ds_ysm_compat_logo.png"' + [Environment]::NewLine + 'logoBlur=false'
} else {
    ""
}
$loaderVersionRange = Get-Prop "loader_version_range" "[1,)"
$minecraftVersionRange = Get-Prop "minecraft_version_range" "[1.21.1]"

# --- classpath --------------------------------------------------------------
$classpath = @(
    (Join-Path $LibrariesDir $McClientJar),
    (Join-Path $LibrariesDir "net\neoforged\neoforge\$NeoForgeVersion\neoforge-$NeoForgeVersion-universal.jar"),
    (Join-Path $LibrariesDir "net\neoforged\fancymodloader\loader\4.0.44\loader-4.0.44.jar"),
    (Join-Path $LibrariesDir "net\neoforged\bus\8.0.5\bus-8.0.5.jar"),
    (Join-Path $LibrariesDir "net\neoforged\mergetool\2.0.0\mergetool-2.0.0-api.jar"),
    (Join-Path $LibrariesDir "net\fabricmc\sponge-mixin\0.15.2+mixin.0.8.7\sponge-mixin-0.15.2+mixin.0.8.7.jar"),
    (Join-Path $LibrariesDir "org\ow2\asm\asm\9.9.1\asm-9.9.1.jar"),
    (Join-Path $LibrariesDir "org\ow2\asm\asm-tree\9.9.1\asm-tree-9.9.1.jar"),
    (Join-Path $LibrariesDir "com\mojang\logging\1.2.7\logging-1.2.7.jar"),
    (Join-Path $LibrariesDir "com\mojang\brigadier\1.3.10\brigadier-1.3.10.jar"),
    (Join-Path $LibrariesDir "org\joml\joml\1.10.5\joml-1.10.5.jar"),
    (Join-Path $LibrariesDir "org\slf4j\slf4j-api\2.0.16\slf4j-api-2.0.16.jar"),
    $DragonSurvivalJar,
    $mixinSquaredJar
)

foreach ($entry in $classpath) {
    if (-not (Test-Path $entry)) {
        throw "Missing classpath entry: $entry"
    }
}

# --- compile ----------------------------------------------------------------
$classesDir = Join-Path $ProjectDir "build\manual-classes"
$stageDir = Join-Path $ProjectDir "build\manual-stage"
$libsDir = Join-Path $ProjectDir "build\libs"
$jarPath = Join-Path $libsDir "$modId-$modVersion.jar"

foreach ($dir in @($classesDir, $stageDir)) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
New-Item -ItemType Directory -Force -Path $libsDir | Out-Null

$sources = @(Get-ChildItem (Join-Path $ProjectDir "src\main\java") -Recurse -File -Filter *.java | ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw "No Java sources found." }

Write-Host "Compiling $($sources.Count) sources with $javac ..."
& $javac --release 21 -encoding UTF-8 -proc:none -cp ($classpath -join ';') -d $classesDir $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

# --- stage ------------------------------------------------------------------
Copy-Item -Path (Join-Path $classesDir "*") -Destination $stageDir -Recurse -Force
Copy-Item -Path (Join-Path $ProjectDir "src\main\resources\*") -Destination $stageDir -Recurse -Force

$modMetadataDir = Join-Path $stageDir "META-INF"
New-Item -ItemType Directory -Force -Path $modMetadataDir | Out-Null
foreach ($licenseFile in @("LICENSE", "TEMPLATE_LICENSE.txt")) {
    Copy-Item -LiteralPath (Join-Path $ProjectDir $licenseFile) -Destination $modMetadataDir
}

$tomlTemplate = Get-Content (Join-Path $ProjectDir "src\main\templates\META-INF\neoforge.mods.toml") -Raw
$replacements = @{
    'loader_version_range'     = $loaderVersionRange
    'mod_license'              = $modLicense
    'mod_id'                   = $modId
    'mod_version'              = $modVersion
    'mod_name'                 = $modName
    'mod_logo'                 = $modLogo
    'minecraft_version_range'  = $minecraftVersionRange
}
foreach ($key in $replacements.Keys) {
    $tomlTemplate = $tomlTemplate.Replace('${' + $key + '}', $replacements[$key])
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $modMetadataDir "neoforge.mods.toml"), $tomlTemplate, $utf8NoBom)

# --- package ----------------------------------------------------------------
& $jarTool "-J-Djava.io.tmpdir=$dependencyDir" --create --file $jarPath -C $stageDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }

Write-Host ""
Write-Host "Built: $jarPath"
Write-Host "Copy it into the client mods folder. Requires Dragon Survival 2.0.7x + Yes Steve Model."
