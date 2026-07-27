param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [Parameter(Mandatory = $true)]
    [string]$Destination
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    throw "ffmpeg nu este instalat sau nu este disponibil în PATH."
}

$sourcePath = (Resolve-Path $Source).Path
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$destinationPath = (Resolve-Path $Destination).Path
$videoExtensions = @(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm")
$imageExtensions = @(".jpg", ".jpeg", ".png", ".webp")

Get-ChildItem -Path $sourcePath -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($sourcePath.Length).TrimStart('\', '/')
    $targetDirectory = Join-Path $destinationPath ([IO.Path]::GetDirectoryName($relative))
    New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

    if ($videoExtensions -contains $_.Extension.ToLowerInvariant()) {
        $target = Join-Path $targetDirectory ($_.BaseName + ".mp4")
        Write-Host "Conversie: $relative"
        & ffmpeg -hide_banner -loglevel warning -y -i $_.FullName `
            -map 0:v:0 -map 0:a:0? `
            -vf "scale=1280:720:force_original_aspect_ratio=decrease:force_divisible_by=2" `
            -c:v libx264 -preset medium -crf 22 -profile:v main -level 3.1 -pix_fmt yuv420p `
            -c:a aac -b:a 128k -ac 2 -ar 48000 `
            -movflags +faststart $target
        if ($LASTEXITCODE -ne 0) { throw "Conversia a eșuat pentru $relative" }
    }
    elseif ($imageExtensions -contains $_.Extension.ToLowerInvariant()) {
        Copy-Item -Force $_.FullName (Join-Path $targetDirectory $_.Name)
    }
}

Write-Host "Biblioteca optimizată este în: $destinationPath"
