@echo off
if not exist out mkdir out
javac -d out java\*.java
if errorlevel 1 pause & exit /b
java -cp out EcoNestServer
pause
