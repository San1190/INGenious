@echo off

:: Create clean distribution
call mvn clean install -U --file pom.xml

:: Define output directory
set outputDir=Dist\target

:: Find the correct ZIP file dynamically
for /f %%F in ('dir /b "%outputDir%\ingenious-playwright-*-setup.zip"') do set "zipPath=%outputDir%\%%F"

:: Check if a ZIP file was found
if not defined zipPath (
    echo ERROR: No ZIP file found in %outputDir%
    exit /b 1
)

:: Unzip using tar command
tar -xf "%zipPath%" -C "%outputDir%"

:: Find the extracted folder dynamically
for /d %%D in ("%outputDir%\ingenious-playwright-*") do set "appDir=%%D"

:: Check if extraction was successful
if not defined appDir (
    echo ERROR: Extraction failed or folder not found.
    exit /b 1
)

:: Run the extracted batch file
set "runner=%appDir%\Run.bat"

if exist "%runner%" (
    call "%runner%"
) else (
    echo ERROR: Runner script not found: %runner%
    exit /b 1
)