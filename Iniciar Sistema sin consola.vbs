Set shell = CreateObject("WScript.Shell")
shell.Run """" & CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\Iniciar Sistema.bat" & """", 0, False
