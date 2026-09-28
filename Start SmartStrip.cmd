@echo off
setlocal
cd /d "%~dp0"
start "SmartStrip API" cmd /k "npm run dev --prefix server"
start "SmartStrip Web" cmd /k "npm run dev --prefix web -- --host localhost"
timeout /t 2 /nobreak >nul
start "" "http://localhost:5173"