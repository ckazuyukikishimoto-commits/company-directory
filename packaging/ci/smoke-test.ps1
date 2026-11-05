# できあがったデスクトップ版（app-image）を Windows で 2 回起動して確かめる。
#   1. /login が 200 を返すか  2. CDS ファイルが作られるか  3. 2 回目の起動時間
# 使い方: pwsh packaging/ci/smoke-test.ps1 -AppDir <アプリのフォルダ> -HomeDir <.env のあるフォルダ>
param(
    [Parameter(Mandatory = $true)][string]$AppDir,
    [Parameter(Mandatory = $true)][string]$HomeDir
)
$ErrorActionPreference = 'Stop'
$env:COMPANY_DIRECTORY_HOME = $HomeDir
$env:DESKTOP_SMOKE_TEST = '1'
$log = Join-Path $HomeDir 'logs\app.log'

foreach ($run in 1, 2) {
    Remove-Item $log -ErrorAction SilentlyContinue
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $p = Start-Process -FilePath (Join-Path $AppDir 'CompanyDirectory.exe') -PassThru
    $ok = $false
    for ($i = 0; $i -lt 600; $i++) {
        try {
            $r = Invoke-WebRequest -Uri 'http://127.0.0.1:18080/login' -UseBasicParsing -TimeoutSec 20
            if ($r.StatusCode -eq 200) { $ok = $true; break }
        } catch { }
        if ($p.HasExited) { break }
        Start-Sleep -Milliseconds 100
    }
    $sw.Stop()
    if (-not $ok) {
        Write-Host "::error::$run 回目: /login に届きませんでした"
        if (Test-Path $log) { Get-Content $log -Tail 80 }
        if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force }
        exit 1
    }
    $started = (Select-String -Path $log -CaseSensitive -Pattern 'Started .*' | Select-Object -First 1).Matches.Value
    Write-Host "$run 回目: /login が応答するまで $($sw.ElapsedMilliseconds) ms / $started"
    # DESKTOP_SMOKE_TEST=1 なので 30 秒後に自分で正しく終了する
    if (-not $p.WaitForExit(90000)) { Write-Host '::error::自分で終了しませんでした'; Stop-Process $p; exit 1 }
    if ($run -eq 1) {
        $jsa = Get-ChildItem (Join-Path $AppDir 'app') -Filter '*.jsa'
        if (-not $jsa) { Write-Host '::error::CDS ファイルが作られていません'; exit 1 }
        Write-Host "CDS ファイル: $($jsa.Name) ($([math]::Round($jsa.Length / 1MB)) MB)"
    }
}
