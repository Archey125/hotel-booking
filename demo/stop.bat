@echo off
setlocal EnableExtensions EnableDelayedExpansion
>nul chcp 65001

echo === Останавливаю java-процессы проекта hotel-booking...
powershell -NoProfile -Command "$ours = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'hotel-booking|eureka-server-1\.0\.0|hotel-service-1\.0\.0|booking-service-1\.0\.0|api-gateway-1\.0\.0' }; if(-not $ours){ Write-Host '   java-процессы проекта не найдены - уже остановлены?' } else { foreach($p in $ours){ Write-Host ('   останавливаю PID ' + $p.ProcessId); Stop-Process -Id $p.ProcessId -Force } }"

rem секунда на освобождение портов
ping -n 2 127.0.0.1 >nul

set "STILL="
for %%p in (8761 8082 8081 8080) do (
  netstat -ano | findstr /C:":%%p " | findstr /C:"LISTENING" >nul && set "STILL=!STILL! %%p"
)
if defined STILL (
  echo ВНИМАНИЕ: порты!STILL! всё ещё заняты чем-то посторонним.
  echo   Найдите процесс:   netstat -ano ^| findstr ":8080"
  echo   И завершите его:   taskkill /F /PID число
) else (
  echo Готово: сервисы остановлены, порты 8761/8082/8081/8080 свободны.
  echo Теперь можно пересобирать: mvn clean package
)
echo.
pause
