$ErrorActionPreference = 'Stop'

$javac = "$env:USERPROFILE\.jdks\temurin-25.0.3\bin\javac.exe"
$jar = "$env:USERPROFILE\.jdks\temurin-25.0.3\bin\jar.exe"
$classpathArgs = Join-Path $PSScriptRoot 'v1\build\javac.args'
$classpath = (Get-Content $classpathArgs)[3].Trim('"')
$classes = Join-Path $PSScriptRoot 'build\classes'
$outputJar = Join-Path $PSScriptRoot 'LavaRising-2.5.30-paper26.1.2.jar'

if (Test-Path $classes) {
    Remove-Item -Recurse -Force -LiteralPath $classes
}
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$sources = Get-ChildItem -Path (Join-Path $PSScriptRoot 'src\main\java') -Recurse -Filter *.java |
        ForEach-Object { $_.FullName }
& $javac --release 25 -cp $classpath -d $classes $sources

Copy-Item -Path (Join-Path $PSScriptRoot 'src\main\resources\*') -Destination $classes -Recurse -Force
& $jar --create --file $outputJar -C $classes .

Write-Host "Built $outputJar"
