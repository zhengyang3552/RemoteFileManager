@echo off
rem Gradle wrapper script for Windows

set GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists
set GRADLE_VERSION=8.2
set GRADLE_ZIP=gradle-%GRADLE_VERSION%-bin.zip
set GRADLE_DIST=%GRADLE_HOME%\%GRADLE_ZIP%
set GRADLE_EXTRACT=%GRADLE_HOME%\gradle-%GRADLE_VERSION%

if not exist "%GRADLE_EXTRACT%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    mkdir "%GRADLE_HOME%" 2>nul
    powershell -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/%GRADLE_ZIP%' -OutFile '%GRADLE_DIST%'"
    echo Extracting...
    tar -xf "%GRADLE_DIST%" -C "%GRADLE_HOME%"
)

set GRADLE_BIN=%GRADLE_EXTRACT%\bin\gradle.bat
if exist "%GRADLE_BIN%" (
    "%GRADLE_BIN%" %*
) else (
    echo Error: Gradle not found
    exit /b 1
)
