@echo off
rem =====================================================================
rem  D'Tulia · Ejecuta la API en bucle: si se detiene, se reinicia en 5 s.
rem  Si el puerto 8000 ya está ocupado (p. ej. un uvicorn --reload abierto a
rem  mano), espera sin arrancar y toma el control cuando el puerto se libere.
rem  Lo lanza iniciar_api_oculto.vbs (sin ventana). Registro: backend\logs\api.log
rem =====================================================================
cd /d "%~dp0.."
if not exist logs mkdir logs

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
