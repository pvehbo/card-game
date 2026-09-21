@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo  CardGame - Windows 打包（自包含可运行目录）
echo  先决条件：Windows + JDK 17+ + Maven
echo ==========================================

where mvn >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Maven，请先安装：https://maven.apache.org/download.cgi
    exit /b 1
)
where jpackage >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 jpackage，请确认 PATH 里是 JDK 17 或更高版本
    exit /b 1
)

echo [1/3] 编译打包 jar ...
call mvn -Dmaven.repo.local=.m2repo package -DskipTests
if errorlevel 1 (
    echo [错误] 编译失败
    exit /b 1
)

echo [2/3] 收集运行依赖 ...
if not exist target\pkg\input mkdir target\pkg\input
call mvn -Dmaven.repo.local=.m2repo dependency:copy-dependencies -DoutputDirectory=target\pkg\input -DincludeScope=runtime
if errorlevel 1 (
    echo [错误] 收集依赖失败
    exit /b 1
)
copy /y target\card-game-1.0-SNAPSHOT.jar target\pkg\input\ >nul

echo [3/3] jpackage 生成应用 ...
if not exist target\pkg\output mkdir target\pkg\output
jpackage --type app-image --name CardGame --app-version 1.0.0 ^
  --input target\pkg\input --main-jar card-game-1.0-SNAPSHOT.jar ^
  --main-class org.example.card.ui.Main --dest target\pkg\output ^
  --java-options "--enable-native-access=ALL-UNNAMED"
if errorlevel 1 (
    echo [错误] jpackage 失败
    exit /b 1
)

echo.
echo ==========================================
echo  完成！应用目录：target\pkg\output\CardGame\
echo  双击 CardGame.exe 即可运行。
echo  如需安装包：jpackage --type msi（需 WiX Toolset）
echo ==========================================
endlocal