@echo off
setlocal
cd /d "%~dp0"

rem CardGame Windows 直接启动脚本（解压 zip 后双击本文件即可）
rem 前提：已安装 JDK 17 或更高版本（任意目录，或已加入 PATH）

where java >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 java，请先安装 JDK 17+：
    echo         https://adoptium.net
    pause
    exit /b 1
)

java -cp "libs\*;card-game-1.0-SNAPSHOT.jar" org.example.card.ui.Main
pause