@echo off
echo Building project...
.\mvnw clean package -DskipTests
echo.
echo Build completed!
pause