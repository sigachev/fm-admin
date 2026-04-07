---
name: Token Discovery & Kraken Integration Verification
description: Verification of /api/admin/tokens/sources/discover endpoint and Kraken REST API integration
type: project
---

# Token Discovery & Kraken API Integration

## Summary

✅ **All components verified and properly configured**

The token discovery flow is fully implemented across the stack with proper Kraken REST API integration.

---

## Endpoint Chain

### 1. Admin Frontend Entry
- **Endpoint**: `POST /api/admin/tokens/discovery/add` (in AdminTokenController)
- **Location**: fm-admin `AdminTokenController.java:118-127`
- **What it does**: Adds a discovered token to the system and triggers aggregator reload

### 2. FM-Admin API Layer
- **Service**: `TokenDiscoveryService` 
- **Location**: fm-admin `TokenDiscoveryService.java`
- **Flow**:
  1. Calls `AggregatorClient.getAvailableTokensPerSource()` 
  2. Calls `AggregatorClient.getAllAvailableTokens()`
  3. Saves discovered tokens to `crypto_aggregator.asset` table
  4. Calls `AggregatorClient.reloadAssets()` to notify aggregator

### 3. FM-Crypto-Aggregator Discovery Service
- **Controller**: `DiscoveryController` 
- **Location**: fm-crypto-aggregator `DiscoveryController.java`
- **Endpoints**:
  - `GET /internal/v1/discovery/tokens` — available tokens per source
  - `POST /internal/v1/discovery/seed` — full discovery from exchanges
  - `GET /internal/v1/discovery/available?sourceId=kraken` — tokens from specific source

- **Service**: `DiscoveryService`
- **Location**: fm-crypto-aggregator `DiscoveryService.java`
- **Logic**:
  1. Iterates through all `ITokenDiscoverable` sources (Kraken, Coinbase, OKX, Gemini, Hyperliquid)
  2. Calls `source.discoverTokens()` on each
  3. Deduplicates and marks which tokens already exist in asset table
  4. Returns `SourceTokensDto` (per-source) or `AvailableTokenDto` (aggregated)

---

## Kraken REST API Integration

### Implementation
- **Class**: `KrakenSource.java`
- **Location**: fm-crypto-aggregator `source/kraken/KrakenSource.java`
- **Method**: `discoverTokens()` (lines 241-262)

### API Call Details
```
GET https://api.kraken.com/0/public/AssetPairs
└─ Returns all trading pairs with metadata
   - wsname: WebSocket pair format (e.g., "XBT/USD")
   - altname: REST API symbol (e.g., "XBTUSD")
   - quote: Asset pair denominator (filters for USD only)
```

### Token Normalization
```java
KRAKEN_NORMALIZE = Map.of(
    "XBT", "BTC",   // Kraken uses XBT, we normalize to BTC
    "XDG", "DOGE"   // Kraken uses XDG, we normalize to DOGE
)
```

### Parsed Response Example
```
Input:  {"result": {"XBTUSD": {"wsname": "XBT/USD", "altname": "XBTUSD", "quote": "ZUSD"}}}
Output: DiscoveredToken(sourceId="kraken", symbol="BTC", wsname="BTC/USD", altname="XBTUSD")
```

---

## Configuration Status

### FM-Crypto-Aggregator
**File**: `application.yml` (lines 127-130)
```yaml
aggregator:
  sources:
    kraken:
      enabled: true
      ws-url: wss://ws.kraken.com/v2
      rest-url: https://api.kraken.com/0/public
```

✅ **Status**: ENABLED
✅ **REST URL**: Configured and public (no auth required)
✅ **Circuit Breaker**: Configured with 50% failure threshold, 30s open state

### API Key Requirements
- **Kraken**: ✅ No API key needed (public `/AssetPairs` endpoint)
- **Coinbase**: ❌ Requires `COINBASE_API_KEY` environment variable (optional for public endpoints)
- **OKX**: ✅ No API key needed (public endpoints)
- **Gemini**: ✅ No API key needed (public endpoints)
- **Hyperliquid**: ✅ No API key needed (public WebSocket)

---

## Resilience & Error Handling

### Circuit Breaker (Resilience4j)
```yaml
kraken-rest:
  sliding-window-size: 10
  failure-rate-threshold: 50%
  wait-duration-in-open-state: 30s
  permitted-calls-in-half-open-state: 3
  slow-call-duration-threshold: 5s
```

### Rate Limiting
```yaml
kraken-rest:
  limit-for-period: 50 requests
  limit-refresh-period: 1 minute
```

### Error Handling Pattern
```java
// In DiscoveryService:
discoverTokens()
  .onErrorResume(e -> {
      log.warn("[kraken] Token discovery failed: {}", e.getMessage());
      return Flux.empty();  // Graceful degradation
  })
```

---

## Testing the Flow

### 1. Verify Discovery Service is Responsive
```bash
curl -s http://localhost:8088/internal/v1/discovery/tokens \
  -H "X-Service-Name: fm-admin" | jq .
```

Expected response:
```json
[
  {
    "sourceId": "kraken",
    "sourceName": "Kraken",
    "priority": 2,
    "connected": true,
    "tokens": ["BTC", "ETH", "SOL", ...]
  },
  ...
]
```

### 2. Query Available Tokens from Admin
```bash
curl -s "http://localhost:8090/api/admin/tokens/discovery/available" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### 3. Trigger Full Discovery
```bash
curl -X POST http://localhost:8088/internal/v1/discovery/seed \
  -H "X-Service-Name: fm-admin" | jq .
```

Response: `{"kraken": 150, "coinbase": 200, ...}` (count of newly inserted tickers)

---

## Database Tables

### FM-Crypto-Aggregator Database

**`asset`** - Available tokens
- Seeded with: BTC, ETH, SOL, ARB, AVAX, LINK, DOGE, MATIC, OP, SUI
- Populated by discovery process

**`source_ticker_config`** - Exchange-specific token metadata
- `source_id`: kraken, coinbase, okx, gemini, hyperliquid
- `symbol`: Canonical symbol (BTC, ETH)
- `exchange_symbol`: Exchange-specific format (XBTUSD for Kraken)
- `rest_symbol`: REST API pair format (XBTUSD)
- `is_enabled`: Whether to subscribe to WebSocket updates

---

## Known Limitations & Notes

1. **Kraken Uses XBT not BTC**: The integration normalizes Kraken's "XBT" to "BTC" for consistency
2. **Public API Only**: Kraken discovery uses public `/AssetPairs` endpoint (no rate-limiting issues)
3. **Circuit Breaker Isolation**: If Kraken REST fails repeatedly, circuit breaker opens after 50% failure rate over 10 calls
4. **Token List**: Discovery only returns USD pairs (`quote == "ZUSD"`)
5. **Async Processing**: Discovery runs asynchronously via Reactor Flux - ideal for high throughput

---

## Related Files

| Component | File |
|-----------|------|
| Admin Discovery Service | fm-admin/service/TokenDiscoveryService.java |
| Aggregator Client | fm-admin/service/AggregatorClient.java |
| Discovery Controller | fm-crypto-aggregator/controller/DiscoveryController.java |
| Discovery Service | fm-crypto-aggregator/service/DiscoveryService.java |
| Kraken Source | fm-crypto-aggregator/source/kraken/KrakenSource.java |
| Configuration | fm-crypto-aggregator/application.yml |
