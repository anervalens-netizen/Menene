param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$AudioLanguage = "ron",
    [switch]$PublishPartial
)
$ErrorActionPreference = "Stop"
$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$Arguments = @(
    (Join-Path $ScriptDirectory "menene_builder.py"),
    $Source,
    $Destination,
    "--audio-language",
    $AudioLanguage
)
if ($PublishPartial) { $Arguments += "--publish-partial" }
python @Arguments
exit $LASTEXITCODE
