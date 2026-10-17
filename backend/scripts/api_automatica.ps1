<#
  D'Tulia · Control de la API en segundo plano (tarea programada de Windows)

  Uso (desde la carpeta backend):
    .\scripts\api_automatica.ps1 instalar      Arranca la API al iniciar sesión en Windows y la inicia ahora
    .\scripts\api_automatica.ps1 reiniciar     Reinicia la API (por ejemplo, después de cambiar el código)
    .\scripts\api_automatica.ps1 detener       Detiene la API y pausa el arranque automático (vuelve con 'reiniciar')
    .\scripts\api_automatica.ps1 estado        Muestra si la API responde
    .\scripts\api_automatica.ps1 desinstalar   Detiene la API y quita el arranque automático

  La tarea arranca 1 minuto después de iniciar sesión y se repite cada 5 minutos como
  respaldo: mientras el bucle de la API corre, la tarea figura "En ejecución" y Windows
  ignora las repeticiones; si el bucle muere, la siguiente repetición lo vuelve a levantar.

  No usar wscript/VBScript: lanzado desde el Programador de tareas, Windows 11 lo deja
  suspendido y la API no arranca (pasó el 17/10/2026). Por eso se usa conhost --headless.
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('instalar', 'reiniciar', 'detener', 'estado', 'desinstalar')]
    [string]$Accion
)

$ErrorActionPreference = 'Stop'
$NombreTarea = "DTulia API"
$Servicio = Join-Path $PSScriptRoot 'api_servicio.cmd'

function Get-ProcesosApi {
    # El bucle (cmd) y el servidor (python) de esta instalación
    Get-CimInstance Win32_Process | Where-Object {
        ($_.Name -eq 'cmd.exe' -and $_.CommandLine -match 'api_servicio\.cmd') -or
        ($_.Name -eq 'python.exe' -and $_.CommandLine -match 'uvicorn app\.main:app --host 127\.0\.0\.1 --port 8000')
    }
}

function Stop-Api {
    Stop-ScheduledTask -TaskName $NombreTarea -ErrorAction SilentlyContinue
    # Primero el bucle, para que no vuelva a levantar el servidor
    Get-ProcesosApi | Sort-Object { $_.Name -ne 'cmd.exe' } | ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Start-Api {
    Stop-Api
    Start-ScheduledTask -TaskName $NombreTarea
}

function Show-Estado {
    for ($i = 0; $i -lt 20; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://127.0.0.1:8000/salud'
            Write-Host "API activa: $($r.Content)" -ForegroundColor Green
            return
        } catch { Start-Sleep -Milliseconds 750 }
    }
    Write-Host "La API no responde. Revisa backend\logs\api.log" -ForegroundColor Yellow
}

switch ($Accion) {
    'instalar' {
        $accionTarea = New-ScheduledTaskAction -Execute 'conhost.exe' `
            -Argument "--headless cmd.exe /c `"$Servicio`""
        $alIniciarSesion = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
        $alIniciarSesion.Delay = 'PT1M'
        # Reloj aparte: la repetición dentro del disparador de inicio de sesión
        # solo empieza a contar tras un inicio de sesión real
        $cada5Minutos = New-ScheduledTaskTrigger -Once -At (Get-Date) `
            -RepetitionInterval (New-TimeSpan -Minutes 5)
        $disparador = @($alIniciarSesion, $cada5Minutos)
        $ajustes = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
            -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -StartWhenAvailable
        Register-ScheduledTask -TaskName $NombreTarea -Action $accionTarea -Trigger $disparador `
            -Settings $ajustes -Description "Inicia la API de D'Tulia (FastAPI) en segundo plano" -Force | Out-Null
        Write-Host "Tarea '$NombreTarea' registrada: la API arrancará sola al iniciar sesión." -ForegroundColor Green
        Start-Api
        Show-Estado
    }
    'reiniciar' {
        Enable-ScheduledTask -TaskName $NombreTarea | Out-Null
        Start-Api
        Show-Estado
    }
    'detener' {
        # Sin pausar la tarea, la repetición de cada 5 minutos la volvería a levantar
        Disable-ScheduledTask -TaskName $NombreTarea -ErrorAction SilentlyContinue | Out-Null
        Stop-Api
        Write-Host "API detenida y arranque automático pausado. Vuelve a activarla con 'reiniciar'."
    }
    'estado' {
        $procesos = @(Get-ProcesosApi)
        Write-Host "Procesos de la API: $($procesos.Count)"
        Show-Estado
    }
    'desinstalar' {
        Stop-Api
        Unregister-ScheduledTask -TaskName $NombreTarea -Confirm:$false -ErrorAction SilentlyContinue
        Write-Host "Arranque automático eliminado y API detenida."
    }
}
