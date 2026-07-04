@echo off
REM ============================================================
REM ASVOSONK Report Generator — PyInstaller Build Script
REM ============================================================
REM
REM Builds a standalone Windows executable from main.py
REM The resulting .exe will be placed in the dist/ directory.
REM Copy it to C:\asvosonk\reports\asvosonk_reports.exe
REM ============================================================

echo Building ASVOSONK Report Generator...
echo.

pyinstaller --onefile ^
    --name asvosonk_reports ^
    --add-data "reports;reports" ^
    --hidden-import psycopg2 ^
    --hidden-import psycopg2.extras ^
    --hidden-import weasyprint ^
    --hidden-import jinja2 ^
    main.py

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo BUILD FAILED with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo.
echo Build successful!
echo.
echo The executable is at: dist\asvosonk_reports.exe
echo.
echo To deploy:
echo   mkdir C:\asvosonk\reports
echo   copy dist\asvosonk_reports.exe C:\asvosonk\reports\
echo   mkdir C:\asvosonk\reports\output
echo.

exit /b 0
