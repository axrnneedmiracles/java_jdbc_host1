@echo off
cd /d "%~dp0frontend"
echo Starting Frontend Website on http://localhost:3000 ...
echo (For phone access on same Wi-Fi, use http://YOUR-PC-IP:3000)
python -m http.server 3000 --bind 0.0.0.0
pause
