# setup-testing-sandbox.ps1
# Generates an extremely advanced and realistic VCS testing sandbox directory.
# Pre-populates multiple directories, files, branches, cherry-pick commits, rebase commits,
# large files for chunktree CDC verification, pre-commit/post-commit hooks, and ECDSA key signing.

$ErrorActionPreference = "Stop"

# Define project locations
$projectRoot = Resolve-Path $PSScriptRoot
$sandboxDir = Join-Path $projectRoot "vcs-advanced-sandbox"

# Define classpath
$cpList = @(
    (Join-Path $projectRoot "target/classes"),
    "C:/Users/User/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0/527175ca6d81050b53bdd4c457a6d6e017626b0e/gson-2.11.0.jar",
    "C:/Users/User/.gradle/caches/modules-2/files-2.1/info.picocli/picocli/4.7.6/77c2cb87814b6a03d431fc856024a9f8ff605ad4/picocli-4.7.6.jar",
    "C:/Users/User/.gradle/caches/modules-2/files-2.1/com.h2database/h2/2.2.224/7bdade27d8cd197d9b5ce9dc251f41d2edc5f7ad/h2-2.2.224.jar"
)
$cp = $cpList -join ";"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "             DRAFTFLOW VCS ADVANCED SANDBOX GENERATOR" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Project Root: $projectRoot" -ForegroundColor Gray
Write-Host "Sandbox Target: $sandboxDir" -ForegroundColor Gray
Write-Host ""

# Verify compiler and runner are available
if (!(Get-Command "java" -ErrorAction SilentlyContinue)) {
    Write-Error "java is not found in your PATH."
}

# Clean old sandbox if exists
if (Test-Path $sandboxDir) {
    Write-Host "[*] Cleaning old sandbox folder..." -ForegroundColor Gray
    Remove-Item $sandboxDir -Recurse -Force -ErrorAction SilentlyContinue
}

# Create sandbox folder
New-Item -ItemType Directory -Path $sandboxDir | Out-Null
Set-Location $sandboxDir

# Helper to run DraftFlow commands in sandbox context
function Invoke-DF {
    param([string[]]$ArgsList)
    $originalDir = Get-Location
    Set-Location $sandboxDir
    try {
        & java -cp $cp com.draftflow.DraftFlow $ArgsList
    } finally {
        Set-Location $originalDir
    }
}

Write-Host "[*] Initializing repository..." -ForegroundColor Yellow
Invoke-DF @("setup")

# 1. Generate ECDSA Cryptographic Keypair
Write-Host "[*] Generating keypair for commit signing..." -ForegroundColor Yellow
Invoke-DF @("keys")

# 2. Create Base Structure & Files
Write-Host "[*] Creating project folders (src, utils, docs, config, assets)..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path (Join-Path $sandboxDir "src") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $sandboxDir "src/utils") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $sandboxDir "docs") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $sandboxDir "config") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $sandboxDir "assets") | Out-Null

# Write file contents
Set-Content -Path (Join-Path $sandboxDir "src/main.js") -Value @"
console.log("Starting DraftFlow VCS demo...");
// Base initialization
const appName = "DraftFlow Sandbox";
"@

Set-Content -Path (Join-Path $sandboxDir "src/utils/helper.js") -Value @"
// Helper functions
function formatLog(message) {
    return `[VCS Log] ${message}`;
}
const version = "v1.0.0";
"@

Set-Content -Path (Join-Path $sandboxDir "docs/index.md") -Value @"
# DraftFlow VCS Sandbox
This repository serves as a testing sandbox for DraftFlow VCS features.
"@

Set-Content -Path (Join-Path $sandboxDir "config/settings.json") -Value @"
{
  "theme": "dark",
  "debug": true
}
"@

# Create ignore file
Set-Content -Path (Join-Path $sandboxDir ".dfignore") -Value @"
# Ignore log files
*.log
# Ignore temp directory
temp/
"@

# 3. Create Large Binary/Text File to Test CDC Chunk Trees (> 1MB)
Write-Host "[*] Creating 1.2MB asset to test FastCDC chunk trees..." -ForegroundColor Yellow
[byte[]]$largeData = New-Object byte[] 1250000
(New-Object Random).NextBytes($largeData)
[System.IO.File]::WriteAllBytes((Join-Path $sandboxDir "assets/large-file.bin"), $largeData)

# Save initial commit
Write-Host "[*] Saving initial commit..." -ForegroundColor Yellow
Invoke-DF @("save", "-m", "Initial base structure and 1.2MB binary asset")

# 4. Setup Custom Repository Hooks
Write-Host "[*] Installing repository pre-commit and post-commit hooks..." -ForegroundColor Yellow
$hooksDir = Join-Path $sandboxDir ".draftflow/hooks"
# pre-commit hook that rejects commit if any file contains "TODO_CHECK_FAILED"
Set-Content -Path (Join-Path $hooksDir "pre-commit.bat") -Value @"
@echo off
findstr /s /m "TODO_CHECK_FAILED" *
if %errorlevel%==0 (
    echo [Hook Error] Commit rejected: Files contain TODO_CHECK_FAILED
    exit /b 1
)
exit /b 0
"@
Set-Content -Path (Join-Path $hooksDir "post-commit.bat") -Value @"
@echo off
echo [Hook Info] Commit completed successfully!
exit /b 0
"@

# 5. Branch: feature-auth (standard branch)
Write-Host "[*] Creating branch: feature-auth..." -ForegroundColor Yellow
Invoke-DF @("branch", "-c", "feature-auth")
Invoke-DF @("switch", "feature-auth")

New-Item -ItemType Directory -Path (Join-Path $sandboxDir "src/auth") | Out-Null
Set-Content -Path (Join-Path $sandboxDir "src/auth/login.js") -Value @"
export function login(username, password) {
    console.log(`Logging in: ${username}`);
    return true;
}
"@

# Modify main.js to import login
Set-Content -Path (Join-Path $sandboxDir "src/main.js") -Value @"
console.log("Starting DraftFlow VCS demo...");
import { login } from "./auth/login.js";
// Base initialization
const appName = "DraftFlow Sandbox";
login("developer", "password123");
"@

Write-Host "[*] Saving feature-auth changes..." -ForegroundColor Yellow
Invoke-DF @("save", "-m", "Implement login module")

# 6. Branch: feature-rebase (set up for rebase testing)
Write-Host "[*] Setting up branch: feature-rebase..." -ForegroundColor Yellow
Invoke-DF @("switch", "main")
Invoke-DF @("branch", "-c", "feature-rebase")
Invoke-DF @("switch", "feature-rebase")

Set-Content -Path (Join-Path $sandboxDir "config/settings.json") -Value @"
{
  "theme": "dark",
  "debug": true,
  "rebaseOption": "enabled"
}
"@
Invoke-DF @("save", "-m", "Enable rebase option in settings")

# 7. Branch: feature-cherry (set up for cherry-pick testing)
Write-Host "[*] Setting up branch: feature-cherry..." -ForegroundColor Yellow
Invoke-DF @("switch", "main")
Invoke-DF @("branch", "-c", "feature-cherry")
Invoke-DF @("switch", "feature-cherry")

Set-Content -Path (Join-Path $sandboxDir "docs/index.md") -Value @"
# DraftFlow VCS Sandbox
This repository serves as a testing sandbox for DraftFlow VCS features.
Check out cherry-pick docs!
"@
Invoke-DF @("save", "-m", "Add cherry-pick documentation details")

# 8. Branch: conflict-branch (set up for merge conflict testing)
Write-Host "[*] Setting up branch: conflict-branch..." -ForegroundColor Yellow
Invoke-DF @("switch", "main")
Invoke-DF @("branch", "-c", "conflict-branch")
Invoke-DF @("switch", "conflict-branch")

# Modify line 5 of helper.js on conflict-branch
Set-Content -Path (Join-Path $sandboxDir "src/utils/helper.js") -Value @"
// Helper functions
function formatLog(message) {
    return `[VCS Log] ${message}`;
}
const version = "v2.0.0-conflict-branch";
"@
Invoke-DF @("save", "-m", "Update version code on conflict branch")

# Modify helper.js on main branch (conflicting change)
Write-Host "[*] Setting up conflicting change on main branch..." -ForegroundColor Yellow
Invoke-DF @("switch", "main")
Set-Content -Path (Join-Path $sandboxDir "src/utils/helper.js") -Value @"
// Helper functions
function formatLog(message) {
    return `[VCS Log] ${message}`;
}
const version = "v1.1.0-main-branch";
"@
Invoke-DF @("save", "-m", "Update version code on main branch")

# 9. Setup local uncommitted modifications & ignored log file
Write-Host "[*] Writing temporary log file (ignored) and dirty docs file..." -ForegroundColor Yellow
Set-Content -Path (Join-Path $sandboxDir "debug.log") -Value "Local debug logs (ignored)"
Set-Content -Path (Join-Path $sandboxDir "docs/index.md") -Value @"
# DraftFlow VCS Sandbox
This repository serves as a testing sandbox for DraftFlow VCS features.
(WIP uncommitted modifications to test stash/undo)
"@

# Switch location back to project root
Set-Location $projectRoot

Write-Host ""
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "              SANDBOX SETUP COMPLETE SUCCESSFUL" -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "VCS Sandbox initialized at: $sandboxDir" -ForegroundColor White
Write-Host ""
Write-Host "You can now test the following advanced commands manually:" -ForegroundColor White
Write-Host "  cd '$sandboxDir'" -ForegroundColor Green
Write-Host ""
Write-Host "  1. Test Rebase: Rebase feature-rebase commits on top of main" -ForegroundColor Gray
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow switch feature-rebase" -ForegroundColor Yellow
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow rebase main" -ForegroundColor Yellow
Write-Host ""
Write-Host "  2. Test Cherry-Pick: Apply cherry-pick doc commits to main" -ForegroundColor Gray
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow switch main" -ForegroundColor Yellow
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow cherry-pick feature-cherry" -ForegroundColor Yellow
Write-Host ""
Write-Host "  3. Test Merge & Conflict Resolution:" -ForegroundColor Gray
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow merge conflict-branch" -ForegroundColor Yellow
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow status" -ForegroundColor Yellow
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow resolve" -ForegroundColor Yellow
Write-Host ""
Write-Host "  4. Git Export: Export DraftFlow history to a local Git repo" -ForegroundColor Gray
Write-Host "     New-Item -ItemType Directory -Path git-export-repo" -ForegroundColor Yellow
Write-Host "     java -cp `"$cp`" com.draftflow.DraftFlow git-export git-export-repo" -ForegroundColor Yellow
Write-Host "======================================================================" -ForegroundColor Cyan
