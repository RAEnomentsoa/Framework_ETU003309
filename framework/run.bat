@echo off
REM -------------------------------
REM Simple build for framework.jar
REM -------------------------------

REM Paths
set SRC_DIR=src
set BUILD_DIR=build
set LIB_DIR=lib
set JAR_NAME=framework.jar

REM Make sure build directory exists
if not exist %BUILD_DIR% mkdir %BUILD_DIR%

REM Compile all Java files
echo Compiling Java sources...
javac -d %BUILD_DIR% -cp "%LIB_DIR%\*" %SRC_DIR%\core\*.java
if errorlevel 1 (
    echo Compilation failed!
    exit /b 1
)

REM Create JAR (auto-generates META-INF)
echo Creating JAR...
cd %BUILD_DIR%
jar cvf %JAR_NAME% core
cd ..

echo Done! %JAR_NAME% created in %BUILD_DIR%
pause
