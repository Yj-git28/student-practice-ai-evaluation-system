# Nginx 操作说明（等价手动命令）

本目录为项目自带的 Nginx（Windows 版 1.30）。`start-https.ps1` 是一键脚本，
下面是与它**完全等价的手动操作**，脚本不可用时按本文手工执行即可。

---

## 一、目录结构

```
deploy\nginx\
├── nginx.exe            主程序
├── start-https.ps1      一键启动脚本
├── ssl\
│   ├── nginx.conf       实际使用的配置（HTTPS / 反向代理）
│   ├── error.log        错误日志（运行时生成）
│   ├── nginx.pid        进程号（运行时生成）
│   ├── logs\            日志目录
│   ├── temp\            临时目录
│   └── cert\
│       ├── cert.pem     证书
│       └── key.pem      私钥
├── conf\                Nginx 默认配置（未使用，可忽略）
└── html\                Nginx 默认页面（未使用，可忽略）
```

> 配置和证书都放在 `ssl\` 子目录，所以启动时必须用 `-p` 指定工作目录、`-c` 指定配置文件。
> 直接在 `deploy\nginx` 下执行 `nginx.exe` 会读 `conf\nginx.conf`，那是默认配置，不是本项目的。

---

## 二、每次启动前

| 检查项 | 手动确认方式 |
|---|---|
| 后端已在 8080 监听 | `Get-NetTCPConnection -LocalPort 8080 -State Listen` |
| 前端 `frontend\dist\index.html` 存在 | `Test-Path ..\..\frontend\dist\index.html` |
| 证书存在 | `Test-Path .\ssl\cert\cert.pem` |
| 配置存在 | `Test-Path .\ssl\nginx.conf` |

---

## 三、手动操作命令

以下命令都在 **`deploy\nginx`** 目录下执行。

### 0. 先定义路径变量（推荐，避免写死盘符）

```powershell
$prefix = (Resolve-Path .\ssl).Path.Replace('\','/') + "/"
$conf   = (Resolve-Path .\ssl\nginx.conf).Path.Replace('\','/')
```

### 1. 检查配置语法

```powershell
.\nginx.exe -p $prefix -c $conf -t
```

期望输出：

```
nginx: the configuration file ... syntax is ok
nginx: configuration file ... test is successful
```

### 2. 启动

```powershell
Start-Process -FilePath .\nginx.exe -ArgumentList @("-p", $prefix, "-c", $conf) -WorkingDirectory (Get-Location).Path -WindowStyle Hidden
```

> 不要直接执行 `.\nginx.exe -p ... -c ...`：Windows 下它会占住当前终端不返回，
> 看起来像卡住。用 `Start-Process` 才会立刻回到命令行。

启动后不会有窗口，用下面的命令确认：

```powershell
Get-Process nginx
Get-NetTCPConnection -LocalPort 443 -State Listen
Get-NetTCPConnection -LocalPort 81  -State Listen
```

正常应有 3 个 nginx 进程（1 个 master + 2 个 worker）。

### 3. 重载配置（不中断服务）

改过 `ssl\nginx.conf` 后：

```powershell
.\nginx.exe -p $prefix -c $conf -s reload
```

### 4. 停止

```powershell
.\nginx.exe -p $prefix -c $conf -s stop
```

若报 `OpenEvent failed`（说明进程不是用这个前缀启动的），直接强制结束：

```powershell
Get-Process nginx -ErrorAction SilentlyContinue | Stop-Process -Force
```

### 5. 完整重启（脚本采用的顺序）

```powershell
Get-Process nginx -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Start-Process -FilePath .\nginx.exe -ArgumentList @("-p", $prefix, "-c", $conf) -WorkingDirectory (Get-Location).Path -WindowStyle Hidden
Start-Sleep -Seconds 3
```

---

## 四、验证是否生效

```powershell
curl.exe -sk -o NUL -w "health: %{http_code}`n" https://localhost/nginx-health
curl.exe -sk -o NUL -w "home:   %{http_code}`n" https://localhost/
curl.exe -s  -o NUL -w "api:    %{http_code}`n" http://localhost:8080/test
```

| 地址 | 期望 |
|---|---|
| `https://localhost/nginx-health` | 200 |
| `https://localhost/` | 200（登录页） |
| `https://localhost/api/auth/login` | 200 / 400（未带参数时的正常响应） |
| `http://localhost:81/` | 301，跳转 HTTPS |

---

## 五、证书：缺失时手动生成

相当于脚本的第 3 步。需要 OpenSSL（`winget install ShiningLight.OpenSSL.Light`）。

```powershell
$openssl = "C:\Program Files\OpenSSL-Win64\bin\openssl.exe"
New-Item -ItemType Directory -Force -Path .\ssl\cert | Out-Null

& $openssl genrsa -out .\ssl\cert\key.pem 2048
& $openssl req -new -x509 -key .\ssl\cert\key.pem -out .\ssl\cert\cert.pem `
    -days 730 -subj "/CN=localhost" `
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
```

生成后把 `cert.pem` 导入「受信任的根证书颁发机构」，浏览器就不再提示不安全。

查看当前证书信息：

```powershell
& $openssl x509 -in .\ssl\cert\cert.pem -noout -subject -dates -ext subjectAltName
```

---

## 六、运行目录补齐

脚本会自动创建，手动执行时：

```powershell
foreach ($d in @("logs","temp")) {
    if (-not (Test-Path ".\ssl\$d")) { New-Item -ItemType Directory -Force -Path ".\ssl\$d" | Out-Null }
}
```

---

## 七、日志

| 文件 | 内容 |
|---|---|
| `ssl\error.log` | 错误日志，启动失败、502 都看这里 |
| `ssl\logs\access.log` | 访问日志（若配置开启） |

实时查看：

```powershell
Get-Content .\ssl\error.log -Tail 20 -Wait
```

---

## 八、常见问题

| 报错 | 原因 | 处理 |
|---|---|---|
| `nginx: [emerg] bind() to 0.0.0.0:443 failed (10013)` | 443 被占用或无权限 | `Get-NetTCPConnection -LocalPort 443` 查占用进程 |
| `nginx: [emerg] cannot load certificate ...` | 证书路径错或文件缺失 | 检查 `ssl\cert\`，或按第五节重新生成 |
| `nginx: [emerg] unknown directive` | 配置文件编码/语法问题 | 用 `-t` 定位行号 |
| 启动后立刻消失 | 端口冲突或配置错误 | 看 `ssl\error.log` 最后几行 |
| `-s reload` 报 `OpenEvent failed` | 进程不是用当前前缀启动 | 用第 5 节的完整重启 |
| 页面 502 | 后端 8080 没起 | 启动后端后刷新 |
| 页面显示旧内容 | 前端没重新构建 | `cd frontend; npm run build` 后 Ctrl+F5 |

---

## 九、一键脚本与本文章节对照

| 脚本步骤 | 对应本文 |
|---|---|
| 1. 定位项目根目录 | 手动执行时可省略（直接在 `deploy\nginx` 下） |
| 2. 定位 OpenSSL | 第五节，直接写完整路径 |
| 3. 证书缺失则生成 | 第五节 |
| 4. 补齐 logs/temp | 第六节 |
| 5. 杀旧进程并启动 | 第三节第 5 小节 |
| 6. 输出状态 | 第三节第 2 小节 + 第四节 |
