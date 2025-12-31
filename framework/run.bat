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


REM Step 1 - Compile annotations first
echo Compiling annotations...
javac -d %BUILD_DIR% %SRC_DIR%\core\annotation\*.java
if errorlevel 1 (
    echo Failed to compile annotations!
    exit /b 1
)

REM Step 1 - Compile Rest
echo Compiling annotations...
javac -d %BUILD_DIR% %SRC_DIR%\core\rest\*.java
if errorlevel 1 (
    echo Failed to compile rest!
    exit /b 1
)

REM Step 2 - Compile the rest (Router, etc.)
echo Compiling other Java sources...
javac -d %BUILD_DIR% -cp "%BUILD_DIR%;%LIB_DIR%\*" %SRC_DIR%\core\*.java
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
