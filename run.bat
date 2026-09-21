@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo  CardGame - Windows 快速运行（开发模式）
echo  先决条件：Windows + JDK 17+ + Maven
echo ==========================================

where mvn >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Maven，请先安装并加入 PATH：
    echo        https://maven.apache.org/download.cgi
    exit /b 1
)

call mvn -Dmaven.repo.local=.m2repo javafx:run
endlocal