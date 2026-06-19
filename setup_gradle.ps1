Write-Host "DraftFlow Gradle Wrapper Bootstrapper" -ForegroundColor Cyan
Write-Host "-------------------------------------" -ForegroundColor Cyan

$tempZip = "gradle-temp.zip"
$tempDir = "gradle-temp"

if (Test-Path "gradlew.bat") {
    Write-Host "Gradle wrapper already exists! You can run .\gradlew build" -ForegroundColor Green
    exit
}

Write-Host "1. Downloading Gradle 9.5.1 binary distribution..." -ForegroundColor Yellow
Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-9.5.1-bin.zip" -OutFile $tempZip

Write-Host "2. Extracting archive..." -ForegroundColor Yellow
Expand-Archive -Path $tempZip -DestinationPath $tempDir

Write-Host "3. Generating local Gradle Wrapper..." -ForegroundColor Yellow
$gradleBat = Get-ChildItem -Path "$tempDir\*\bin\gradle.bat" | Select-Object -First 1 -ExpandProperty FullName
if (-not $gradleBat) {
    Write-Error "Could not find gradle.bat in the extracted directory."
    exit
}

# Generate wrapper
& $gradleBat wrapper

# Stop daemon to release file locks on Windows
Write-Host "Stopping background Gradle daemon to release file locks..." -ForegroundColor Yellow
& $gradleBat --stop

Write-Host "4. Cleaning up temporary files..." -ForegroundColor Yellow
Start-Sleep -Seconds 2
Remove-Item -Recurse -Force $tempZip, $tempDir

Write-Host "-------------------------------------" -ForegroundColor Green
Write-Host "Success! Gradle Wrapper has been initialized." -ForegroundColor Green
Write-Host "You can now build, test, and run the project using:" -ForegroundColor Green
Write-Host "   .\gradlew build" -ForegroundColor Cyan
Write-Host "   .\gradlew test" -ForegroundColor Cyan
