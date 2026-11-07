# Regenerates the mod's sounds: synthesizes WAVs (make_sounds.py, needs numpy) and encodes them to Ogg Vorbis
# with VLC, straight into the mod's assets. Run from the project folder:  powershell -File tools\make_sounds.ps1
param([string]$Vlc = "C:\Program Files\VideoLAN\VLC\vlc.exe")
$ErrorActionPreference = "Stop"
$tmp = Join-Path $env:TEMP "horizons_unbound_sounds"
New-Item -ItemType Directory -Force $tmp | Out-Null
python tools\make_sounds.py $tmp
$dst = "src\main\resources\assets\horizons_unbound\sounds"
New-Item -ItemType Directory -Force $dst | Out-Null
Get-ChildItem "$tmp\*.wav" | ForEach-Object {
    $ogg = Join-Path $tmp ($_.BaseName + ".ogg")
    if (Test-Path $ogg) { Remove-Item $ogg }
    & $Vlc -I dummy --no-repeat --no-loop $_.FullName --sout "#transcode{acodec=vorb,ab=96,channels=1,samplerate=32000}:std{access=file,mux=ogg,dst=$ogg}" vlc://quit | Out-Null
    $tries = 0
    while (-not (Test-Path $ogg) -and $tries -lt 50) { Start-Sleep -Milliseconds 200; $tries++ }
    Start-Sleep -Milliseconds 300
    Copy-Item $ogg (Join-Path $dst ($_.BaseName + ".ogg")) -Force
    Write-Output "encoded $($_.BaseName)"
}
