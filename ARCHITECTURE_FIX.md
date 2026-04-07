# Architecture Fix: Exposing Aggregator Discovery Endpoints

## Problem
fm-admin was trying to call **internal-only** endpoints on fm-crypto-aggregator:
```
❌ fm-admin → /internal/v1/discovery/* → fm-crypto-aggregator (NOT allowed)
```

fm-crypto-aggregator is a data **collection service**, not a public API. Its `/internal/v1/` endpoints are meant for service-to-service communication only (via X-Service-Name header).

## Solution: Expose Public Discovery Endpoints

Created **public admin endpoints** alongside the existing internal ones:

### New Public Endpoints (fm-crypto-aggregator)

```
GET    /api/v1/discovery/available?sourceId=kraken&onlyNew=false
GET    /api/v1/discovery/tokens
GET    /api/v1/discovery/tokens/all
POST   /api/v1/discovery/seed
POST   /api/v1/discovery/reload-assets
GET    /api/v1/prices/sources/status  (already existed)
```

### Endpoint Organization

```
/internal/v1/discovery/*     ← Internal service-to-service calls (requires X-Service-Name header)
/api/v1/discovery/*          ← Public admin API (for fm-admin service)
/api/v1/prices/sources/status ← Already public, returns source health
```

### Architecture Now Correct

```
✅ fm-admin → /api/v1/discovery/* → fm-crypto-aggregator (public endpoints)
✅ fm-crypto → /internal/v1/discovery/* → fm-crypto-aggregator (internal endpoints)
✅ fm-crypto-data → /internal/v1/* → fm-crypto-aggregator (internal endpoints)
```

---

## Changes Made

### 1. fm-crypto-aggregator (DiscoveryController.java)

**Before:**
```java
@RestController
@RequestMapping("/internal/v1/discovery")
public class DiscoveryController {
    @GetMapping("/available")
    public Mono<List<SourceAvailableTokenDto>> getAvailableTokens(...)
}
```

**After:**
```java
@RestController
public class DiscoveryController {
    // Internal endpoints (service-to-service)
    @GetMapping("/internal/v1/discovery/available")
    public Mono<List<SourceAvailableTokenDto>> getAvailableTokensInternal(...)
    
    // Public admin endpoints (for fm-admin)
    @GetMapping("/api/v1/discovery/available")
    public Mono<List<SourceAvailableTokenDto>> getAvailableTokensPublic(...)
}
```

**Summary:**
- Kept all `/internal/v1/` endpoints (unchanged functionality)
- Added new `/api/v1/` endpoints with identical logic
- Clearly separated internal vs public API contracts

### 2. fm-admin (AggregatorClient.java)

**Before:**
```java
String url = aggregatorUrl + "/internal/v1/discovery/available?sourceId=" + sourceId;
```

**After:**
```java
String url = aggregatorUrl + "/api/v1/discovery/available?sourceId=" + sourceId;
```

**Updated Endpoints:**
- `/internal/v1/discovery/tokens` → `/api/v1/discovery/tokens`
- `/internal/v1/discovery/tokens/all` → `/api/v1/discovery/tokens/all`
- `/internal/v1/discovery/seed` → `/api/v1/discovery/seed`
- `/internal/v1/discovery/reload-assets` → `/api/v1/discovery/reload-assets`
- `/internal/v1/health/sources` → `/api/v1/prices/sources/status` (corrected)

---

## Compliance with Architecture

### Before (Violated)
```
Layer Violation:
┌─────────────────────┐
│   fm-admin (admin)  │
│   "Should not call  │
│    internal APIs"   │
└──────────┬──────────┘
           │
           ↓ ❌ WRONG
┌─────────────────────────────┐
│  fm-crypto-aggregator       │
│  (internal data ingestion)  │
│  /internal/v1/*             │
└─────────────────────────────┘
```

### After (Correct)
```
Proper Layering:
┌─────────────────────┐
│   fm-admin (admin)  │
│   Public Admin API  │
└──────────┬──────────┘
           │
           ↓ ✅ CORRECT
┌─────────────────────────────┐
│  fm-crypto-aggregator       │
│  Public Admin Endpoints      │
│  /api/v1/discovery/*        │
│  /api/v1/prices/*           │
└─────────────────────────────┘
           ↑
           │
┌──────────┴──────────────────────────────────────────┐
│  Other Internal Services (fm-crypto, fm-crypto-data)│
│  /internal/v1/* (X-Service-Name header)             │
└────────────────────────────────────────────────────┘
```

---

## Key Benefits

1. **Proper Separation of Concerns**
   - Internal endpoints (`/internal/v1/`) for service-to-service
   - Public endpoints (`/api/v1/`) for admin clients

2. **No Breaking Changes**
   - Internal endpoints (`/internal/v1/`) still exist and unchanged
   - All existing service-to-service calls continue to work

3. **Clear API Contract**
   - `/api/` prefix = public admin API
   - `/internal/` prefix = internal service-to-service only

4. **Scalable Architecture**
   - Other admin services can now safely call discovery endpoints
   - Easy to add role-based access control later if needed

---

## Testing

### Test the New Public Endpoints

```bash
# Test available tokens discovery (with cache)
curl http://localhost:8088/api/v1/discovery/available?sourceId=kraken&onlyNew=false

# Test source status
curl http://localhost:8088/api/v1/prices/sources/status

# Test token list
curl http://localhost:8088/api/v1/discovery/tokens
```

### Verify fm-admin Integration

1. Start fm-crypto-aggregator on port 8088
2. Start fm-admin on port 8090
3. Open http://localhost:3000/admin/tokens/browse
4. Select "Kraken" source
5. Click "Browse Available Tokens"
6. First request: ~10-15 seconds (fetches from Kraken, caches result)
7. Subsequent requests: <10ms (cache hit)

---

## Future Improvements

1. **Add Authentication/Authorization**
   - Require JWT token or ADMIN role for public endpoints
   - Or restrict to localhost for now

2. **API Versioning**
   - `/api/v1/` is already version 1
   - Easy to add `/api/v2/` later if needed

3. **Rate Limiting**
   - Protect discovery endpoints from abuse
   - Cache discovery results to reduce load

4. **Documentation**
   - Add Swagger/OpenAPI docs for public endpoints
   - Mark endpoints as admin-only in documentation

---

## Files Modified

1. **fm-crypto-aggregator/src/main/java/com/finmates/aggregator/controller/DiscoveryController.java**
   - Added public `/api/v1/discovery/*` endpoints
   - Kept internal `/internal/v1/discovery/*` endpoints unchanged
   - Added clear documentation comments

2. **fm-admin/src/main/java/com/finmates/admin/service/AggregatorClient.java**
   - Updated all endpoints to call `/api/v1/*` instead of `/internal/v1/*`
   - Corrected source health endpoint to `/api/v1/prices/sources/status`
   - Updated JavaDoc comments

---

## Related Fixes

This fix works together with:
1. **fm-admin timeout fix** — RestTemplate now has 5s connection + 10s read timeout
2. **fm-crypto-aggregator caching** — Discovery responses cached for 5 minutes
3. **Better error handling** — ResourceAccessException explicitly handled

See `PERFORMANCE_ANALYSIS.md` for details on those fixes.
