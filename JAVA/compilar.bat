@echo off
REM ============================================================
REM  compilar.bat
REM  Compila el proyecto desde consola/terminal, sin depender de
REM  ningun IDE, y deja los .class en la carpeta out\
REM ============================================================

echo Compilando Almacen Robotico Concurrente...

if not exist out mkdir out

javac -d out *.java

if %errorlevel% neq 0 (
    echo.
    echo ERROR: la compilacion fallo. Revise los mensajes anteriores.
    pause
    exit /b 1
)

echo.
echo Compilacion exitosa. Los archivos .class quedaron en out\
echo.
echo Para ejecutar el programa use, por ejemplo:
echo   java -cp out SimulacionRobotsSokoban entrada_ejemplo.txt
echo   java -cp out SimulacionRobotsSokoban entrada_estres.txt
echo.
pause
