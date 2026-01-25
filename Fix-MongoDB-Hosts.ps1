# PowerShell script to update hosts file with MongoDB IPs
# Must be run as Administrator

$hostsFile = "$env:windir\System32\drivers\etc\hosts"
$entries = @(
    "159.41.224.34 ac-2vlmdhx-shard-00-00.puesjra.mongodb.net",
    "159.41.246.109 ac-2vlmdhx-shard-00-01.puesjra.mongodb.net",
    "159.41.229.253 ac-2vlmdhx-shard-00-02.puesjra.mongodb.net"
)

Write-Host "Updating hosts file at $hostsFile..." -ForegroundColor Cyan

try {
    $currentContent = Get-Content $hostsFile -Raw -ErrorAction Stop
} catch {
    # If file doesn't exist or can't be read, start empty
    $currentContent = ""
}

$needsUpdate = $false

foreach ($entry in $entries) {
    # check if the hostname is already there to avoid duplicates
    $hostname = $entry.Split(" ")[1]
    if ($currentContent -match [regex]::Escape($hostname)) {
        Write-Host "Entry for $hostname already exists. Skipping." -ForegroundColor Yellow
    } else {
        Add-Content -Path $hostsFile -Value $entry
        Write-Host "Added: $entry" -ForegroundColor Green
        $needsUpdate = $true
    }
}

if ($needsUpdate) {
    Write-Host "`nFlushing DNS cache..." -ForegroundColor Cyan
    ipconfig /flushdns
    Write-Host "Hosts file updated successfully." -ForegroundColor Green
} else {
    Write-Host "No changes needed." -ForegroundColor Green
}

Write-Host "`nPlease restart your Java application now." -ForegroundColor Cyan
