@echo off
cd /d "%~dp0backend"
if not exist bin mkdir bin
echo Compiling Java JDBC Backend...
javac -cp "lib/*" -d bin src/*.java
if %errorlevel% neq 0 (
  echo Compilation failed!
  pause
  exit /b 1
)
echo Starting Java JDBC API Server on port 8080...
java -cp "bin;lib/*" ApiServer
pause
