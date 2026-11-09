@echo off
setlocal EnableExtensions EnableDelayedExpansion
>nul chcp 65001
cd /d "%~dp0.."
title hotel-booking - launcher

echo ==============================================================
echo   hotel-booking: запуск всех сервисов (Windows)
echo   Eureka :8761  Hotel :8082  Booking :8081  Gateway :8080
echo ==============================================================
echo.

rem --- 1. java в PATH?
where java >nul 2>nul
if errorlevel 1 (
  echo [ОШИБКА] java не найден в PATH.
  echo   Установите JDK 17+ и/или добавьте папку bin JDK в PATH.
  goto :err
)

rem --- 2. порты свободны?
set "BUSY="
for %%p in (8761 8082 8081 8080) do (
  netstat -ano | findstr /C:":%%p " | findstr /C:"LISTENING" >nul && set "BUSY=!BUSY! %%p"
)
if defined BUSY (
  echo [ОШИБКА] порты!BUSY! уже заняты - сервисы уже запущены?
  echo   Остановите их: .\demo\stop.bat   либо закройте окна, где висит java -jar
  goto :err
)

rem --- 3. jar-ы на месте? если нет - пробуем собрать
if not exist "booking-service\target\booking-service-1.0.0.jar" (
  echo jar-ы не найдены, собираю проект: mvn -DskipTests package ...
  where mvn >nul 2>nul || where mvn.cmd >nul 2>nul || (
    echo [ОШИБКА] jar-ов нет и Maven не найден. Сначала соберите: mvn clean package
    goto :err
  )
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo [ОШИБКА] сборка не удалась. Запустите mvn clean package и посмотрите вывод.
    goto :err
  )
)

rem --- 4. старт: каждый сервис в своём окне; окно можно закрыть или Ctrl+C
echo === Запускаю Eureka (реестр сервисов)...
start "hb-eureka" cmd /k "chcp 65001 >nul && title hb-eureka && java -jar eureka-server\target\eureka-server-1.0.0.jar"

call :waitup "http://localhost:8761/actuator/health" 120 "Eureka"
if errorlevel 1 goto :failstart

echo === Запускаю Hotel Service и Booking Service...
start "hb-hotel" cmd /k "chcp 65001 >nul && title hb-hotel && java -jar hotel-service\target\hotel-service-1.0.0.jar"
start "hb-booking" cmd /k "chcp 65001 >nul && title hb-booking && java -jar booking-service\target\booking-service-1.0.0.jar"

call :waitup "http://localhost:8082/actuator/health" 150 "Hotel Service"
if errorlevel 1 goto :failstart
call :waitup "http://localhost:8081/actuator/health" 150 "Booking Service"
if errorlevel 1 goto :failstart

echo === Запускаю API Gateway...
start "hb-gateway" cmd /k "chcp 65001 >nul && title hb-gateway && java -jar api-gateway\target\api-gateway-1.0.0.jar"

call :waitup "http://localhost:8080/actuator/health" 120 "API Gateway"
if errorlevel 1 goto :failstart

rem --- 5. ждём регистрации в Eureka: без неё Gateway отвечает 503
echo === Жду регистрации HOTEL-SERVICE и BOOKING-SERVICE в Eureka (до 60 сек)...
powershell -NoProfile -Command "$ok=$false; for($i=0;$i -lt 60;$i+=2){ try{ $c=(Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 -Headers @{Accept='application/json'} -Uri 'http://localhost:8761/eureka/apps').Content; if($c -match 'HOTEL-SERVICE' -and $c -match 'BOOKING-SERVICE'){ $ok=$true; break } }catch{}; Start-Sleep -Seconds 2 }; if($ok){ exit 0 } else { exit 1 }"
if errorlevel 1 (
  echo [ОШИБКА] сервисы не зарегистрировались в Eureka за 60 сек.
  echo   Смотрите окна hb-hotel / hb-booking - там будет текст ошибки.
  goto :err
)
echo === Сервисы зарегистрированы в Eureka.

echo.
echo ==============================================================
echo   ГОТОВО: все 4 сервиса подняты
echo --------------------------------------------------------------
echo   Eureka dashboard:   http://localhost:8761
echo   Swagger UI:         http://localhost:8081/swagger-ui.html
echo                       http://localhost:8082/swagger-ui.html
echo   Логины:             admin/admin123 (ADMIN), demo-user/demo12345 (USER)
echo --------------------------------------------------------------
echo   Дальше:
echo     .\demo\smoke-api.bat  - автопроверка всего API (13 шагов)
echo     api.http              - ручные запросы в IntelliJ
echo     .\demo\stop.bat       - остановить всё
echo ==============================================================
echo.
echo Это окно можно закрыть - сервисы работают в своих окнах hb-*.
pause
exit /b 0

:failstart
echo.
echo [ОШИБКА] сервис не поднялся. Откройте соответствующее окно
echo   hb-eureka / hb-hotel / hb-booking / hb-gateway - там текст ошибки.
echo   Потом запустите .\demo\stop.bat и попробуйте снова.
goto :err

:err
echo.
pause
exit /b 1

:waitup
rem %1 = url, %2 = максимум секунд, %3 = имя сервиса
set "WAIT_URL=%~1"
set /a WAIT_MAX=%~2
set "WAIT_NAME=%~3"
powershell -NoProfile -Command "$sw=[Diagnostics.Stopwatch]::StartNew(); $ok=$false; while($sw.Elapsed.TotalSeconds -lt $env:WAIT_MAX){ try{ Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 -Uri $env:WAIT_URL | Out-Null; $ok=$true; break }catch{ Write-Host -NoNewline '.'; Start-Sleep -Seconds 2 } }; if($ok){ Write-Host ''; Write-Host ('    ' + $env:WAIT_NAME + ' готов за ' + [int]$sw.Elapsed.TotalSeconds + ' сек') } else { Write-Host ''; Write-Host ('    ' + $env:WAIT_NAME + ' не ответил за ' + $env:WAIT_MAX + ' сек'); exit 1 }"
exit /b %errorlevel%
