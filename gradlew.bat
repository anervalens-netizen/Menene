@echo off
setlocal
set "GRADLE_VERSION=8.13"
if defined GRADLE_USER_HOME (
  set "CACHE_ROOT=%GRADLE_USER_HOME%\mehene-bootstrap"
) else (
  set "CACHE_ROOT=%USERPROFILE%\.gradle\mehene-bootstrap"
)
set "GRADLE_HOME=%CACHE_ROOT%\gradle-%GRADLE_VERSION%"
set "ARCHIVE=%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
set "URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"
  if not exist "%ARCHIVE%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%ARCHIVE%'"
    if errorlevel 1 exit /b 1
  )
  if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ARCHIVE%' '%CACHE_ROOT%'"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
