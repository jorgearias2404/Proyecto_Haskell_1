@echo off
if not exist out mkdir out
javac -d out MonitorTablero.java ProductorSokoban.java RobotCarga.java SimulacionRobotsSokoban.java
if errorlevel 1 exit /b %errorlevel%
echo Compilacion completada. Ejecute: java -cp out SimulacionRobotsSokoban archivo.txt