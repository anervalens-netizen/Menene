param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [Parameter(Mandatory = $true)]
    [string]$Destination
)

$ErrorActionPreference = "Stop"

foreach ($command in @("ffmpeg", "ffprobe")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command nu este instalat sau nu este disponibil în PATH."
    }
}

$sourcePath = (Resolve-Path $Source).Path.TrimEnd('\', '/')
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$destinationPath = (Resolve-Path $Destination).Path.TrimEnd('\', '/')
if ($destinationPath -eq $sourcePath -or $destinationPath.StartsWith($sourcePath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Folderul destinație nu poate fi identic cu sursa sau în interiorul ei."
}

$videoExtensions = @(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".webm")
$imageExtensions = @(".jpg", ".jpeg", ".png", ".webp")
$converted = 0
$skipped = 0
$failed = 0

function Test-MeheneVideo([string]$Path) {
    $videoJson = & ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height -of json $Path 2>$null
    if ($LASTEXITCODE -ne 0) { return $false }
    $video = ($videoJson | ConvertFrom-Json).streams | Select-Object -First 1
    if (-not $video -or $video.codec_name -ne "h264" -or $video.width -gt 1280 -or $video.height -gt 720) { return $false }

    $audioCodec = (& ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of default=nw=1:nk=1 $Path 2>$null | Select-Object -First 1)
    return (-not $audioCodec -or $audioCodec.Trim() -eq "aac")
}

Get-ChildItem -Path $sourcePath -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($sourcePath.Length).TrimStart('\', '/')
    $relativeDirectory = [IO.Path]::GetDirectoryName($relative)
    $targetDirectory = if ([string]::IsNullOrEmpty($relativeDirectory)) {
        $destinationPath
    } else {
        Join-Path $destinationPath $relativeDirectory
    }
    New-Item -ItemType Directory -Force -Path $targetDirectory | Out-Null

    if ($videoExtensions -contains $_.Extension.ToLowerInvariant()) {
        $target = Join-Path $targetDirectory ($_.BaseName + ".mp4")
        if ((Test-Path $target) -and (Get-Item $target).LastWriteTimeUtc -ge $_.LastWriteTimeUtc -and (Test-MeheneVideo $target)) {
            Write-Host "Păstrat: $relative"
            $script:skipped++
        } else {
            $temporary = $target + ".tmp.mp4"
            Remove-Item -Force -ErrorAction SilentlyContinue $temporary
            Write-Host "Conversie: $relative"
            & ffmpeg -hide_banner -loglevel warning -y -i $_.FullName `
                -map 0:v:0 -map 0:a:0? `
                -vf "scale='min(1280,iw)':'min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2" `
                -fpsmax 30 `
                -c:v libx264 -preset medium -crf 22 -profile:v main -level 3.1 -pix_fmt yuv420p `
                -c:a aac -b:a 128k -ac 2 -ar 48000 `
                -movflags +faststart $temporary
            if ($LASTEXITCODE -eq 0 -and (Test-MeheneVideo $temporary)) {
                Move-Item -Force $temporary $target
                $script:converted++
            } else {
                Remove-Item -Force -ErrorAction SilentlyContinue $temporary
                Write-Warning "Eșec: $relative"
                $script:failed++
            }
        }
    }
    elseif ($imageExtensions -contains $_.Extension.ToLowerInvariant()) {
        $target = Join-Path $targetDirectory $_.Name
        if (-not (Test-Path $target) -or (Get-Item $target).LastWriteTimeUtc -lt $_.LastWriteTimeUtc) {
            Copy-Item -Force $_.FullName $target
        }
    }
}

Write-Host "Finalizat: $converted convertite, $skipped păstrate, $failed eșuate."
Write-Host "Biblioteca optimizată este în: $destinationPath"
if ($failed -gt 0) { exit 1 }
