param([string]$Base = "http://localhost:8080")

# Сквозной автотест API через Gateway: логины, рекомендации, сага, идемпотентность,
# конфликт дат, отмена, админ-CRUD, разграничение прав.
# Сервисы должны быть уже запущены (demo\demo.bat) и зарегистрированы в Eureka.

$script:Passed = 0
$script:Failed = 0

function Check {
    param($Name, $Ok, $Detail = '')
    if ($Ok) {
        $script:Passed++
        Write-Host "  [OK]   $Name" -ForegroundColor Green
    } else {
        $script:Failed++
        if ($Detail) { Write-Host "  [FAIL] $Name -- $Detail" -ForegroundColor Red }
        else         { Write-Host "  [FAIL] $Name" -ForegroundColor Red }
    }
}

function Invoke-Api {
    # любой ответ (в т.ч. 4xx/5xx) возвращается как @{ code; body }, без исключений
    param([string]$Method, [string]$Uri, [string]$Token, $Body, [hashtable]$ExtraHeaders)
    $h = @{}
    if ($Token) { $h['Authorization'] = "Bearer $Token" }
    if ($ExtraHeaders) { foreach ($k in $ExtraHeaders.Keys) { $h[$k] = $ExtraHeaders[$k] } }
    try {
        $p = @{ Method = $Method; Uri = $Uri; UseBasicParsing = $true; TimeoutSec = 20 }
        if ($h.Count -gt 0) { $p.Headers = $h }
        if ($null -ne $Body) {
            $p.Body = ConvertTo-Json -InputObject $Body -Depth 6
            $p.ContentType = 'application/json'
        }
        $resp = Invoke-WebRequest @p
        return @{ code = [int]$resp.StatusCode; body = $resp.Content }
    } catch {
        $code = 0; $content = ''
        $r = $_.Exception.Response
        if ($r) {
            try { $code = [int]$r.StatusCode } catch {}
            try {
                $s = $r.GetResponseStream()
                if ($s) { $reader = New-Object System.IO.StreamReader($s); $content = $reader.ReadToEnd() }
            } catch {}
        }
        return @{ code = $code; body = $content }
    }
}

function Json($text) { try { return $text | ConvertFrom-Json } catch { return $null } }

Write-Host "======================================================"
Write-Host " smoke-api: сквозная проверка через Gateway ($Base)"
Write-Host "======================================================"

# --- Gateway жив
$r = Invoke-Api GET "$Base/actuator/health"
Check "Gateway отвечает на /actuator/health" ($r.code -eq 200) "код $($r.code)"

# --- админ
$r = Invoke-Api POST "$Base/api/users/auth" $null @{ username = 'admin'; password = 'admin123' }
$admin = Json $r.body
Check "логин admin/admin123 -> JWT" ($r.code -eq 200 -and $admin.accessToken) "код $($r.code)"
$adminToken = $admin.accessToken

# --- демо-пользователь: логин, если нет - регистрация
$r = Invoke-Api POST "$Base/api/users/auth" $null @{ username = 'demo-user'; password = 'demo12345' }
$user = Json $r.body
if (-not $user.accessToken) {
    $r = Invoke-Api POST "$Base/api/users/register" $null @{ username = 'demo-user'; password = 'demo12345' }
    $user = Json $r.body
}
Check "demo-user получил JWT (логин или регистрация)" ($user.accessToken) "код $($r.code)"
$userToken = $user.accessToken

# --- рекомендации: свободные номера, сортировка по timesBooked ASC
$from = (Get-Date).AddDays(30).ToString('yyyy-MM-dd')
$to   = (Get-Date).AddDays(32).ToString('yyyy-MM-dd')
$r = Invoke-Api GET "$Base/api/rooms/recommend?startDate=$from&endDate=$to" $userToken
$rec = Json $r.body
$count = @($rec | Where-Object { $_ }).Count
$sorted = $true
for ($i = 1; $i -lt $count; $i++) {
    if (@($rec)[$i].timesBooked -lt @($rec)[$i - 1].timesBooked) { $sorted = $false }
}
Check "GET /api/rooms/recommend: есть свободные номера" ($r.code -eq 200 -and $count -gt 0) "код $($r.code), номеров: $count"
Check "рекомендации отсортированы по timesBooked ASC" $sorted

# --- бронирование с автоподбором
$rid = [guid]::NewGuid().ToString()
$r = Invoke-Api POST "$Base/api/bookings" $userToken @{ autoSelect = $true; startDate = $from; endDate = $to; requestId = $rid }
$booking = Json $r.body
Check "POST /api/bookings (autoSelect): бронь создана" ($r.code -eq 201 -and $booking.id) "код $($r.code), тело: $($r.body)"
Check "сага прошла: статус CONFIRMED" ($booking.status -eq 'CONFIRMED') "статус $($booking.status)"
$bookingId = $booking.id
$roomId = $booking.roomId

# --- идемпотентность: повтор с тем же requestId -> 200 и та же бронь
$r2 = Invoke-Api POST "$Base/api/bookings" $userToken @{ autoSelect = $true; startDate = $from; endDate = $to; requestId = $rid }
$b2 = Json $r2.body
Check "повтор с тем же requestId: 200, дубля нет" ($r2.code -eq 200 -and $b2.id -eq $bookingId) "код $($r2.code), id $($b2.id)"

# --- конфликт: тот же номер на те же даты, новый requestId -> 409
$rid2 = [guid]::NewGuid().ToString()
$r3 = Invoke-Api POST "$Base/api/bookings" $userToken @{ autoSelect = $false; roomId = $roomId; startDate = $from; endDate = $to; requestId = $rid2 }
Check "конфликт дат на занятый номер: 409" ($r3.code -eq 409) "код $($r3.code), тело: $($r3.body)"

# --- история бронирований пользователя
$r4 = Invoke-Api GET "$Base/api/bookings?page=0&size=20" $userToken
$hist = Json $r4.body
$found = $false
if ($hist -and $hist.content) { foreach ($b in $hist.content) { if ($b.id -eq $bookingId) { $found = $true } } }
Check "GET /api/bookings: созданная бронь видна в истории" $found

# --- без токена -> 401
$r5 = Invoke-Api GET "$Base/api/bookings"
Check "запрос без JWT: 401" ($r5.code -eq 401) "код $($r5.code)"

# --- отмена: CANCELLED + release слота (даты снова свободны)
$r6 = Invoke-Api DELETE "$Base/api/bookings/$bookingId" $userToken
$b6 = Json $r6.body
Check "DELETE брони: статус CANCELLED" ($r6.code -eq 200 -and $b6.status -eq 'CANCELLED') "код $($r6.code), статус $($b6.status)"

# --- админ: создать отель и номер, потом убрать за собой
$r7code = 0; $r8code = 0; $hotelId = $null; $roomId2 = $null
if ($adminToken) {
    $hotelName = "Smoke Hotel " + (Get-Random -Maximum 9999)
    $r7 = Invoke-Api POST "$Base/api/hotels" $adminToken @{ name = $hotelName; address = 'ул. Тестовая, 1'; city = 'Казань' }
    $hotel = Json $r7.body
    $r7code = $r7.code; $hotelId = $hotel.id
    if ($hotelId) {
        $r8 = Invoke-Api POST "$Base/api/rooms" $adminToken @{ hotelId = $hotelId; number = ("SM" + (Get-Random -Maximum 999)); pricePerNight = 4500 }
        $room = Json $r8.body
        $r8code = $r8.code; $roomId2 = $room.id
    }
}
Check "POST /api/hotels (ADMIN): отель создан" ($r7code -eq 201 -and $hotelId) "код $r7code"
Check "POST /api/rooms (ADMIN): номер добавлен" ($r8code -eq 201 -and $roomId2) "код $r8code"
if ($roomId2) { Invoke-Api DELETE "$Base/api/rooms/$roomId2" $adminToken | Out-Null }
if ($hotelId) { Invoke-Api DELETE "$Base/api/hotels/$hotelId" $adminToken | Out-Null }

# --- статистика: админу можно, пользователю нельзя
$r9 = Invoke-Api GET "$Base/api/statistics/occupancy" $adminToken
Check "GET /api/statistics/occupancy (ADMIN): 200" ($r9.code -eq 200) "код $($r9.code)"
$r10 = Invoke-Api GET "$Base/api/statistics/occupancy" $userToken
Check "статистика под USER: 403" ($r10.code -eq 403) "код $($r10.code)"

# --- внутренний шаг саги (роль INTERNAL) под пользовательским токеном: 403
if ($roomId) {
    $r11 = Invoke-Api POST "$Base/api/rooms/$roomId/confirm-availability" $userToken @{
        requestId = [guid]::NewGuid().ToString(); bookingId = $bookingId; startDate = $from; endDate = $to
    }
    Check "внутренний эндпойнт саги под USER: 403" ($r11.code -eq 403) "код $($r11.code)"
}

# --- неверный пароль: 401
$r12 = Invoke-Api POST "$Base/api/users/auth" $null @{ username = 'admin'; password = 'wrong-password' }
Check "неверный пароль: 401" ($r12.code -eq 401) "код $($r12.code)"

Write-Host "======================================================"
Write-Host (" Итог: пройдено " + $script:Passed + ", провалено " + $script:Failed)
if ($script:Failed -eq 0) {
    Write-Host " ВСЁ РАБОТАЕТ" -ForegroundColor Green
    exit 0
} else {
    Write-Host " ЕСТЬ ПРОБЛЕМЫ - смотрите [FAIL] выше" -ForegroundColor Red
    exit 1
}
