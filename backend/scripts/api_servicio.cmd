@echo off
rem =====================================================================
rem  D'Tulia · Ejecuta la API en bucle: si se detiene, se reinicia en 5 s.
rem  Si el puerto 8000 ya está ocupado (p. ej. un uvicorn --reload abierto a
rem  mano), espera sin arrancar y toma el control cuando el puerto se libere.
rem  Lo lanza la tarea "DTulia API" sin ventana (conhost --headless), al iniciar
rem  sesión y cada 5 minutos como respaldo. Registro: backend\logs\api.log
rem =====================================================================
cd /d "%~dp0.."
if not exist logs mkdir logs

rem Solo una copia del bucle: mientras corre mantiene abierto logs\servicio.lock.
rem Si otra copia ya lo tiene, la redirección falla y esta sale sin hacer nada.
2>nul (call :inicio 9>"logs\servicio.lock")
exit /b

:inicio
netstat -ano | findstr /r /c:"127.0.0.1:8000 .*LISTENING" /c:"0.0.0.0:8000 .*LISTENING" > nul
if not errorlevel 1 (
    ping -n 31 127.0.0.1 > nul
    goto inicio
)

echo [%date% %time%] Iniciando API D'Tulia en http://127.0.0.1:8000 >> logs\api.log
".venv\Scripts\python.exe" -m uvicorn app.main:app --host 127.0.0.1 --port 8000 >> logs\api.log 2>&1
echo [%date% %time%] La API se detuvo; se reinicia en 5 segundos >> logs\api.log
ping -n 6 127.0.0.1 > nul
goto inicio
