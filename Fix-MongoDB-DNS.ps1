# ============================================
# FIX MONGODB DNS - Run as Administrator
# ============================================
# This script fixes Windows DNS issues that prevent MongoDB Atlas connections
# Right-click PowerShell and select "Run as Administrator", then run this script

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "MongoDB DNS Fix Script" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Check if running as administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "❌ ERROR: This script must be run as Administrator!" -ForegroundColor Red
    Write-Host "`nTo run as Administrator:" -ForegroundColor Yellow
    Write-Host "1. Right-click PowerShell icon" -ForegroundColor Yellow
    Write-Host "2. Select 'Run as Administrator'" -ForegroundColor Yellow
    Write-Host "3. Navigate to this directory and run the script again" -ForegroundColor Yellow
    Write-Host "`nPress any key to exit..." -ForegroundColor Gray
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

Write-Host "✅ Running with Administrator privileges`n" -ForegroundColor Green

# Step 1: Find active network adapter
Write-Host "[1/5] Finding active network adapter..." -ForegroundColor Cyan
$adapter = Get-NetAdapter | Where-Object {$_.Status -eq 'Up'} | Select-Object -First 1

if ($null -eq $adapter) {
    Write-Host "❌ ERROR: No active network adapter found!" -ForegroundColor Red
    exit 1
}

Write-Host "✅ Found adapter: $($adapter.Name)`n" -ForegroundColor Green

# Step 2: Set DNS servers to Google & Cloudflare
Write-Host "[2/5] Configuring DNS servers (Google: 8.8.8.8, 8.8.4.4 + Cloudflare: 1.1.1.1)..." -ForegroundColor Cyan
try {
    Set-DnsClientServerAddress -InterfaceIndex $adapter.ifIndex -ServerAddresses ('8.8.8.8','8.8.4.4','1.1.1.1')
    Write-Host "✅ DNS servers configured successfully`n" -ForegroundColor Green
} catch {
    Write-Host "❌ ERROR setting DNS servers: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 3: Flush DNS cache
Write-Host "[3/5] Flushing DNS cache..." -ForegroundColor Cyan
ipconfig /flushdns | Out-Null
Write-Host "✅ DNS cache flushed`n" -ForegroundColor Green

# Step 4: Reset Windows network stack
Write-Host "[4/5] Resetting Windows network stack..." -ForegroundColor Cyan
netsh winsock reset | Out-Null
netsh int ip reset | Out-Null
Write-Host "✅ Network stack reset`n" -ForegroundColor Green

# Step 5: Test MongoDB DNS resolution
Write-Host "[5/5] Testing MongoDB Atlas DNS resolution..." -ForegroundColor Cyan
Write-Host "`nTesting: topupdb.puesjra.mongodb.net" -ForegroundColor Gray
$testResult = nslookup topupdb.puesjra.mongodb.net 8.8.8.8 2>&1

if ($testResult -match "Name:.*topupdb.puesjra.mongodb.net") {
    Write-Host "✅ DNS resolution successful!`n" -ForegroundColor Green
} else {
    Write-Host "⚠️  DNS test inconclusive. May need computer restart.`n" -ForegroundColor Yellow
}

# Show current DNS configuration
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Current DNS Configuration:" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Get-DnsClientServerAddress -InterfaceAlias $adapter.Name | Format-Table

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "✅ DNS CONFIGURATION COMPLETE!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nNext steps:" -ForegroundColor Yellow
Write-Host "1. Restart your computer for all changes to take effect" -ForegroundColor White
Write-Host "2. After restart, test the backend:" -ForegroundColor White
Write-Host "   cd 'topup backend'" -ForegroundColor Gray
Write-Host "   mvn spring-boot:run" -ForegroundColor Gray
Write-Host "3. The 'Database connection error' should be resolved`n" -ForegroundColor White

Write-Host "Press any key to exit..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
