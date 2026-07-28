param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$AudioLanguage = "ron"
)
$ErrorActionPreference = "Stop"
$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
python (Join-Path $ScriptDirectory "mehene_library.py") $Source $Destination --audio-language $AudioLanguage
exit $LASTEXITCODE
