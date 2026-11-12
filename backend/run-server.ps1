$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$configDirectory = Join-Path $PSScriptRoot '.secrets'
$configPath = Join-Path $configDirectory 'server.properties'

function Read-ServerConfig {
    $config = @{}
    if (Test-Path -LiteralPath $configPath) {
        foreach ($line in Get-Content -LiteralPath $configPath) {
            $trimmed = $line.Trim()
            if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
            $separator = $trimmed.IndexOf('=')
            if ($separator -lt 1) { continue }
            $key = $trimmed.Substring(0, $separator).Trim()
            $value = $trimmed.Substring($separator + 1).Trim()
            $config[$key] = $value
        }
    }
    return $config
}

$config = Read-ServerConfig
if (-not $config.DB_USERNAME -or -not $config.DB_PASSWORD) {
    Write-Host '처음 실행합니다. MySQL 접속 정보를 한 번만 입력해 주세요.' -ForegroundColor Cyan
    $hostInput = Read-Host 'MySQL 주소 (같은 PC면 Enter, 다른 PC면 해당 PC의 IP/이름 입력) [127.0.0.1]'
    $portInput = Read-Host 'MySQL 포트 [3306]'
    $databaseInput = Read-Host '데이터베이스 이름 [tayotayo_dev]'
    $usernameInput = Read-Host 'MySQL 계정 이름 [ssafy]'
    $securePassword = Read-Host 'MySQL 비밀번호' -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $passwordInput = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }

    $dbHost = if ($hostInput.Trim()) { $hostInput.Trim() } else { '127.0.0.1' }
    $dbPort = if ($portInput.Trim()) { $portInput.Trim() } else { '3306' }
    $dbName = if ($databaseInput.Trim()) { $databaseInput.Trim() } else { 'tayotayo_dev' }
    $dbUsername = if ($usernameInput.Trim()) { $usernameInput.Trim() } else { 'ssafy' }
    if (-not $passwordInput) { throw 'MySQL 비밀번호를 입력해야 합니다.' }

    New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
    @(
        "DB_HOST=$dbHost"
        "DB_PORT=$dbPort"
        "DB_NAME=$dbName"
        "DB_USERNAME=$dbUsername"
        "DB_PASSWORD=$passwordInput"
        'FIREBASE_ENABLED=false'
        'FIREBASE_CREDENTIALS_PATH='
        'FIREBASE_PROJECT_ID='
        'FIREBASE_TEST_ENDPOINT_ENABLED=false'
    ) | Set-Content -LiteralPath $configPath -Encoding UTF8
    $config = Read-ServerConfig
    Write-Host "접속 정보는 $configPath 에 저장했습니다. 이 파일은 Git에 올라가지 않습니다." -ForegroundColor Green
}

$env:DB_USERNAME = $config.DB_USERNAME
$env:DB_PASSWORD = $config.DB_PASSWORD
$env:FIREBASE_ENABLED = if ($config.FIREBASE_ENABLED) { $config.FIREBASE_ENABLED } else { 'false' }
$env:FIREBASE_CREDENTIALS_PATH = if ($config.FIREBASE_CREDENTIALS_PATH) { $config.FIREBASE_CREDENTIALS_PATH } else { '' }
$env:FIREBASE_PROJECT_ID = if ($config.FIREBASE_PROJECT_ID) { $config.FIREBASE_PROJECT_ID } else { '' }
$env:FIREBASE_TEST_ENDPOINT_ENABLED = if ($config.FIREBASE_TEST_ENDPOINT_ENABLED) { $config.FIREBASE_TEST_ENDPOINT_ENABLED } else { 'false' }
$dbHost = if ($config.DB_HOST) { $config.DB_HOST } else { '127.0.0.1' }
$dbPort = if ($config.DB_PORT) { $config.DB_PORT } else { '3306' }
$dbName = if ($config.DB_NAME) { $config.DB_NAME } else { 'tayotayo_dev' }
$env:DB_URL = "jdbc:mysql://${dbHost}:$dbPort/${dbName}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"

Write-Host '서버를 준비합니다. 첫 실행은 필요한 파일을 내려받느라 시간이 걸릴 수 있습니다.' -ForegroundColor Cyan
& .\gradlew.bat bootJar
if ($LASTEXITCODE -ne 0) { throw '서버 파일을 만드는 중 오류가 발생했습니다.' }

$jar = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'build\libs') -Filter '*.jar' |
    Where-Object { $_.Name -notmatch 'plain|original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) { throw '실행 가능한 서버 파일을 찾지 못했습니다.' }

$physicalAdapters = Get-NetAdapter -Physical -ErrorAction SilentlyContinue |
    Where-Object { $_.Status -eq 'Up' }
$addresses = $physicalAdapters |
    ForEach-Object { Get-NetIPAddress -InterfaceIndex $_.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue } |
    Select-Object -ExpandProperty IPAddress |
    Where-Object { $_ -and $_ -notmatch '^(127\.|169\.254\.)' } |
    Select-Object -Unique

Write-Host ''
Write-Host '서버가 시작됩니다.' -ForegroundColor Green
Write-Host '이 PC:       http://localhost:8080/health'
Write-Host 'Swagger UI:  http://localhost:8080/swagger-ui.html'
foreach ($address in $addresses) { Write-Host "같은 Wi-Fi의 다른 기기: http://${address}:8080/health" }
Write-Host '서버를 끄려면 이 창에서 Ctrl+C를 누르세요.'
Write-Host ''

& java -jar $jar.FullName
if ($LASTEXITCODE -ne 0) { throw '서버가 오류로 종료되었습니다.' }
