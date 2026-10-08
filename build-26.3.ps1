param(
    [string]$ReplayModJar = '',
    [string]$JavaHome = 'C:/Program Files/Java/jdk-25.0.2',
    [string]$BlenderExecutable = 'C:/Program Files/Blender Foundation/Blender 4.5/blender.exe'
)
$ErrorActionPreference = 'Stop'
if (-not $ReplayModJar) {
    $replayModLibs = Join-Path $PSScriptRoot '../ReplayMod/versions/26.3/build/libs'
    $candidate = Get-ChildItem -LiteralPath $replayModLibs -Filter 'replaymod-26.3-*.jar' |
        Where-Object Name -NotMatch '-(raw|sources)\.jar$' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($candidate) { $ReplayModJar = $candidate.FullName }
}
if (-not $ReplayModJar -or -not (Test-Path -LiteralPath $ReplayModJar)) {
    throw 'Build the matching ReplayMod 26.3 port first, or pass -ReplayModJar.'
}
if (-not (Test-Path -LiteralPath "$JavaHome/bin/javac.exe")) { throw "JDK missing at $JavaHome" }
if (-not (Test-Path -LiteralPath $BlenderExecutable)) { throw "Blender missing at $BlenderExecutable" }
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome/bin;$env:Path"
Push-Location $PSScriptRoot
try {
    & ./gradlew.bat build buildAddon --console=plain --max-workers=4 "-Preplaymod_jar=$ReplayModJar" "-Pblender_executable=$BlenderExecutable"
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
    Get-ChildItem build/libs | Where-Object Name -NotMatch '-sources\.jar$' | Select-Object FullName, Length
} finally { Pop-Location }
