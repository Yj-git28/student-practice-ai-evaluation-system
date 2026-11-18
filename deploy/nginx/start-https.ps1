# 启动 Nginx（HTTPS 反向代理）
# 用法:
#   powershell -ExecutionPolicy Bypass -File start-https.ps1
#   powershell -ExecutionPolicy Bypass -File start-https.ps1 -ProjectRoot "D:\code\student-practice-ai-evaluation-system"
#
# 脚本会自动寻找项目根目录，组员把项目放在任意盘符、任意路径都能直接用。
# 本文件保存为 UTF-8 with BOM，否则 Windows PowerShell 5.1 会把中文按 GBK 解析而出错。

param(
    [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

# ---------- 1. 定位项目根目录 ----------
function Test-ProjectRoot([string]$p) {
    return (Test-Path "$p\backend\pom.xml") -and (Test-Path "$p\frontend\package.json") -and (Test-Path "$p\deploy\nginx\nginx.exe")
}

if ($ProjectRoot) {
    if (-not (Test-ProjectRoot $ProjectRoot)) { throw "指定目录不是有效的项目根目录: $ProjectRoot" }
} else {
    # 1) 脚本放在 deploy\nginx 下时，自动向上两级
    if ($PSScriptRoot) {
        $up2 = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
        if (Test-ProjectRoot $up2) { $ProjectRoot = $up2 }
    }
    # 2) 脚本放在项目根目录下
    if (-not $ProjectRoot -and $PSScriptRoot -and (Test-ProjectRoot $PSScriptRoot)) { $ProjectRoot = $PSScriptRoot }
    # 3) 本机默认路径
    if (-not $ProjectRoot) {
        $default = "E:\SE\student-practice-ai-evaluation-system"
        if (Test-ProjectRoot $default) { $ProjectRoot = $default }
    }
    # 4) 都没找到
    if (-not $ProjectRoot) {
        throw "未能定位项目根目录。请用 -ProjectRoot 参数指定，例如: -ProjectRoot `"D:\code\student-practice-ai-evaluation-system`""
    }
}

$NginxHome = "$ProjectRoot\deploy\nginx"
$SslDir    = "$NginxHome\ssl"
$Conf      = "$SslDir\nginx.conf"
$CertDir   = "$SslDir\cert"

Write-Host "[ok] 项目根目录: $ProjectRoot"

if (-not (Test-Path $Conf)) {
    throw "缺少 Nginx 配置: $Conf`n请先确认已把 nginx.conf 放入 deploy\nginx\ssl\ 目录。"
}

# ---------- 2. 定位 OpenSSL ----------
$OpenSSL = ""
foreach ($c in @(
    (Get-Command openssl.exe -ErrorAction SilentlyContinue).Source,
    "C:\Program Files\OpenSSL-Win64\bin\openssl.exe",
    "C:\Program Files\OpenSSL-Win32\bin\openssl.exe"
)) {
    if ($c -and (Test-Path $c)) { $OpenSSL = $c; break }
}

# ---------- 3. 证书不存在则自动生成 ----------
if (-not (Test-Path "$CertDir\cert.pem") -or -not (Test-Path "$CertDir\key.pem")) {
    if (-not $OpenSSL) {
        throw "缺少证书且未找到 OpenSSL。请安装 OpenSSL 后重试: winget install ShiningLight.OpenSSL.Light"
    }
    New-Item -ItemType Directory -Force -Path $CertDir | Out-Null
    & $OpenSSL genrsa -out "$CertDir\key.pem" 2048 2>&1 | Out-Null
    & $OpenSSL req -new -x509 -key "$CertDir\key.pem" -out "$CertDir\cert.pem" -days 730 `
        -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" 2>&1 | Out-Null
    Write-Host "[cert] 已生成自签名证书（localhost / 127.0.0.1）"
}

# ---------- 4. 补齐运行目录 ----------
foreach ($d in @("logs", "temp")) {
    if (-not (Test-Path "$SslDir\$d")) { New-Item -ItemType Directory -Force -Path "$SslDir\$d" | Out-Null }
}

# ---------- 5. 重启 Nginx ----------
Get-Process nginx -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

$prefix  = ($SslDir -replace '\\', '/') + "/"
$confFwd = $Conf -replace '\\', '/'
Start-Process -FilePath "$NginxHome\nginx.exe" -ArgumentList @("-p", $prefix, "-c", $confFwd) `
    -WorkingDirectory $NginxHome -WindowStyle Hidden
Start-Sleep -Seconds 3

# ---------- 6. 输出状态 ----------
$n = (Get-Process nginx -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Host "[ok] nginx 进程数: $n"
Write-Host "[ok] https://localhost        -> 前端 (frontend\dist 静态文件)"
Write-Host "[ok] https://localhost/api/*  -> 后端 (Spring 8080)"
Write-Host "[ok] http://localhost:81      -> 301 跳转 HTTPS"
if ($n -eq 0) {
    Write-Host "[err] 启动失败，请查看 $SslDir\error.log" -ForegroundColor Red
    exit 1
}
