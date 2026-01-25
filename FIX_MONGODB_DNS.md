# Permanent Fix for MongoDB Atlas DNS Connection Issues

## Problem
Windows DNS resolver is failing to resolve MongoDB Atlas replica set node addresses, causing intermittent connection failures.

## Root Cause
- Local DNS cache not properly resolving `.mongodb.net` domains
- IPv6 vs IPv4 resolution conflicts
- Short DNS cache TTL causing repeated lookups

## Solutions Applied

### ✅ Solution 1: Application Level Fixes (ALREADY APPLIED)
1. Updated MongoDB connection string with:
   - Longer timeouts: 30 seconds instead of 5 seconds
   - Connection pooling configuration
   - Retry logic enabled
   
2. Added JVM arguments to force IPv4:
   - `-Djava.net.preferIPv4Stack=true`
   - `-Djava.net.preferIPv6Addresses=false`
   - DNS cache configuration

### ⚠️ Solution 2: Windows DNS Configuration (REQUIRES ADMIN ACCESS)

**Option A: Change DNS Servers (Recommended)**

Run PowerShell **AS ADMINISTRATOR**:

```powershell
# Find your active network adapter
$adapter = Get-NetAdapter | Where-Object {$_.Status -eq 'Up'} | Select-Object -First 1

# Set Google and Cloudflare DNS
Set-DnsClientServerAddress -InterfaceIndex $adapter.ifIndex -ServerAddresses ('8.8.8.8','8.8.4.4','1.1.1.1')

# Flush DNS cache
ipconfig /flushdns

# Verify
ipconfig /all
```

**Option B: Reset Windows Network Settings**

Run Command Prompt **AS ADMINISTRATOR**:

```cmd
netsh winsock reset
netsh int ip reset
ipconfig /flushdns
ipconfig /registerdns
```

Then restart your computer.

### Solution 3: Use MongoDB Compass or Atlas UI

Test your connection using MongoDB Compass with the same connection string:
```
mongodb+srv://thujee_db:rnowmM7gbFn6XbSw@topupdb.puesjra.mongodb.net/topup_db
```

If Compass connects successfully, the issue is specific to Java/Spring Boot configuration.

## Testing the Fix

After applying DNS fixes, test connection:

```powershell
# Test DNS resolution
nslookup topupdb.puesjra.mongodb.net 8.8.8.8
nslookup ac-2vlmdhx-shard-00-00.puesjra.mongodb.net 8.8.8.8
nslookup ac-2vlmdhx-shard-00-01.puesjra.mongodb.net 8.8.8.8
nslookup ac-2vlmdhx-shard-00-02.puesjra.mongodb.net 8.8.8.8

# Clear DNS cache
ipconfig /flushdns

# Start backend
cd "topup backend"
mvn spring-boot:run
```

## Quick Workaround (No Admin Required)

If you cannot change DNS settings, try using a VPN or mobile hotspot temporarily - they often use different DNS servers that can resolve MongoDB Atlas domains properly.

## Verify Backend is Working

Once backend starts, check:
1. Backend logs show "Started TopupbackendApplication"
2. No MongoDB connection errors in console
3. Admin dashboard loads without "Database connection error"
4. Navigate to: http://localhost:3000/admin

## Current Status

- ✅ MongoDB connection string optimized
- ✅ IPv4 forced, IPv6 disabled  
- ✅ Connection timeouts increased to 30 seconds
- ✅ Connection pooling configured
- ⚠️ DNS server configuration requires administrator access
- ⚠️ Alternative: Use VPN or different network

## Next Steps

**Immediate (Without Admin):**
1. Try using mobile hotspot or different WiFi network
2. Test if MongoDB Compass can connect
3. Restart your computer to clear network cache

**Permanent (With Admin):**
1. Run PowerShell as Administrator
2. Execute DNS configuration commands above
3. Restart computer
4. Start backend application

**Alternative:**
Consider deploying to a cloud server (Azure, AWS, Vercel) where DNS resolution works reliably.
