@echo off
rem Compiles and starts the game. Uses the JDK on PATH, or else the one from Android Studio.
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 set "PATH=C:\Program Files\Android\openjdk\jdk-21.0.8\bin;%PATH%"
javac ScheduleRush.java
if errorlevel 1 (
    pause
    exit /b 1
)
java ScheduleRush
