<#
  D'Tulia · Control de la API en segundo plano (tarea programada de Windows)

  Uso (desde la carpeta backend):
    .\scripts\api_automatica.ps1 instalar      Arranca la API al iniciar sesión en Windows y la inicia ahora
    .\scripts\api_automatica.ps1 reiniciar     Reinicia la API (por ejemplo, después de cambiar el código)
    .\scripts\api_automatica.ps1 detener       Detiene la API hasta el próximo inicio de sesión
    .\scripts\api_automatica.ps1 estado        Muestra si la API responde
    .\scripts\api_automatica.ps1 desinstalar   Detiene la API y quita el arranque automático
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('instalar', 'reiniciar', 'detener', 'estado', 'desinstalar')]
    [string]$Accion
)

$ErrorActionPreference = 'Stop'
$NombreTarea = "DTulia API"
$Lanzador = Join-Path $PSScriptRoot 'iniciar_api_oculto.vbs'

function Get-ProcesosApi {
    # El bucle (cmd) y el servidor (python) de esta instalación
    Get-CimInstance Win32_Process | Where-Object {
        ($_.Name -eq 'cmd.exe' -and $_.CommandLine -match 'api_servicio\.cmd') -or
        ($_.Name -eq 'python.exe' -and $_.CommandLine -match 'uvicorn app\.main:app --host 127\.0\.0\.1 --port 8000')
    }
}

function Stop-Api {
    # Primero el bucle, para que no vuelva a levantar el servidor
    Get-ProcesosApi | Sort-Object { $_.Name -ne 'cmd.exe' } | ForEach-Object {
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
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
        $accionTarea = New-ScheduledTaskAction -Execute 'wscript.exe' -Argument "`"$Lanzador`""
        $disparador = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
        $ajustes = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
            -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
        Register-ScheduledTask -TaskName $NombreTarea -Action $accionTarea -Trigger $disparador `
            -Settings $ajustes -Description "Inicia la API de D'Tulia (FastAPI) en segundo plano" -Force | Out-Null
        Write-Host "Tarea '$NombreTarea' registrada: la API arrancará sola al iniciar sesión." -ForegroundColor Green
        Stop-Api
        Start-Process wscript.exe -ArgumentList "`"$Lanzador`""
        Show-Estado
    }
    'reiniciar' {
        Stop-Api
        Start-Process wscript.exe -ArgumentList "`"$Lanzador`""
        Show-Estado
    }
    'detener' {
        Stop-Api
        Write-Host "API detenida. Volverá a arrancar en el próximo inicio de sesión (o con 'reiniciar')."
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
