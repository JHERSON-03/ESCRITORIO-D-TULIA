' D'Tulia - Lanza api_servicio.cmd sin abrir ninguna ventana
Set shell = CreateObject("WScript.Shell")
carpeta = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
shell.Run """" & carpeta & "\api_servicio.cmd""", 0, False
