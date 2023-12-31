@echo off
setlocal EnableDelayedExpansion

:MAIN
SET libs=
for %%i in (*.jar) do SET libs=!libs!;%%i
for /r "./libs" %%i in (*.jar) do SET libs=!libs!;%%i
SET libs=%libs:~1%

java -cp "%libs%" org.asf.edge.contentserver.EdgeContentServerMain %*
IF %ERRORLEVEL% EQU 237 goto MAIN

if EXIST contentserverupdater.jar goto UPDATE
exit

:UPDATE
java -cp contentserverupdater.jar org.asf.edge.contentserver.EdgeContentServerUpdater --update
del contentserverupdater.jar
echo.
goto MAIN
