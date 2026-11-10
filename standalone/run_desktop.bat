@echo off
title abug Standalone Desktop Studio
echo Starting abug (🐞) Standalone Desktop Studio...

where python >nul 2>nul
if %errorlevel% equ 0 (
    python "%~dp0abug_standalone.py"
    goto end
)

where py >nul 2>nul
if %errorlevel% equ 0 (
    py "%~dp0abug_standalone.py"
    goto end
)

echo.
echo [ERROR] Python was not found on your system!
echo Please download and install Python from https://www.python.org/downloads/
echo (Make sure to check "Add Python to PATH" during installation)
echo.

:end
pause

