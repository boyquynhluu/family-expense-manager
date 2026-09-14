@echo off
REM Double-click this file to see the current Cloudflare quick-tunnel URL.
REM (Windows doesn't run .sh files on double-click, and the window closes the
REM instant a script finishes unless it pauses — this .bat does both correctly.)

powershell -NoProfile -Command "$m = (docker logs fem-cloudflared 2>&1 | Select-String -Pattern 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' | Select-Object -Last 1); if ($m) { Write-Host ('Tunnel URL: ' + $m.Matches[0].Value) } else { Write-Host 'Chua thay tunnel URL trong log. Kiem tra container dang chay: docker ps | findstr cloudflared' }"

echo.
pause
