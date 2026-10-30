# Plays a .wav file synchronously. Used by Claude Code hooks (Stop, Notification).
# If Path is a folder, a random .wav from it is played.
# Never fails the hook: any error is swallowed and the script always exits 0.
param([string]$Path)

try {
    if ($Path -and (Test-Path -LiteralPath $Path -PathType Container)) {
        $Path = Get-ChildItem -LiteralPath $Path -Filter *.wav | Get-Random | ForEach-Object FullName
    }
    if ($Path -and (Test-Path -LiteralPath $Path)) {
        $player = New-Object System.Media.SoundPlayer $Path
        $player.PlaySync()
    }
} catch {
    # ignore: a missing or broken sound must never block Claude
}
exit 0
