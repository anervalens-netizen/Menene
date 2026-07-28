@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "GRADLE_VERSION=8.13"
set "EXPECTED_SHA256=20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
if defined GRADLE_USER_HOME (
  set "CACHE_ROOT=%GRADLE_USER_HOME%\mehene-bootstrap"
) else (
  set "CACHE_ROOT=%USERPROFILE%\.gradle\mehene-bootstrap"
)
set "GRADLE_HOME=%CACHE_ROOT%\gradle-%GRADLE_VERSION%"
set "ARCHIVE=%CACHE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
set "TEMP_ARCHIVE=%ARCHIVE%.tmp"
set "URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"

if exist "%ARCHIVE%" (
  for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%ARCHIVE%').Hash.ToLowerInvariant()"`) do set "ACTUAL_SHA256=%%H"
  if /I not "!ACTUAL_SHA256!"=="%EXPECTED_SHA256%" (
    echo Mehene: cached Gradle archive has an invalid checksum; removing it. 1>&2
    del /f /q "%ARCHIVE%"
  )
)

if not exist "%ARCHIVE%" (
  if exist "%TEMP_ARCHIVE%" del /f /q "%TEMP_ARCHIVE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing '%URL%' -OutFile '%TEMP_ARCHIVE%'"
  if errorlevel 1 exit /b 1
  for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 -LiteralPath '%TEMP_ARCHIVE%').Hash.ToLowerInvariant()"`) do set "ACTUAL_SHA256=%%H"
  if /I not "!ACTUAL_SHA256!"=="%EXPECTED_SHA256%" (
    del /f /q "%TEMP_ARCHIVE%"
    echo Mehene: Gradle checksum verification failed. 1>&2
    exit /b 1
  )
  move /y "%TEMP_ARCHIVE%" "%ARCHIVE%" >nul
)

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  set "EXTRACT_ROOT=%CACHE_ROOT%\extract-%RANDOM%-%RANDOM%"
  if exist "%EXTRACT_ROOT%" rmdir /s /q "%EXTRACT_ROOT%"
  if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ARCHIVE%' '%EXTRACT_ROOT%'"
  if errorlevel 1 exit /b 1
  move "%EXTRACT_ROOT%\gradle-%GRADLE_VERSION%" "%GRADLE_HOME%" >nul
  rmdir /s /q "%EXTRACT_ROOT%"
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
