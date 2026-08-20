@echo off
rem dataeng-cli launcher (Windows CMD)
rem Usage: bin\dataeng-cli.cmd --help
rem ASCII-only on purpose: cmd.exe parses this file in the system codepage,
rem and non-ASCII bytes would corrupt batch parsing.
setlocal
rem Switch console to UTF-8 so Chinese output from the JVM displays correctly
chcp 65001 >nul
set "DIR=%~dp0.."
set "JAR=%DIR%\target\dataeng-cli.jar"

if exist "%JAR%" goto :run

echo dataeng-cli: executable jar not found: %JAR%
echo Build it first:  mvn -q package
exit /b 1

:run
java -Dfile.encoding=UTF-8 -jar "%JAR%" %*
exit /b %ERRORLEVEL%
