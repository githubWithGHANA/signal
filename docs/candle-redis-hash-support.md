# Candle Redis Compatibility (List + Hash/ZSet)

## Problem Statement
Candle keys are currently mixed across Redis data types, not a single schema:

- many `candle:*` keys are `list`
- some `candle:*` keys are `zset`
- matching `candle:*:data` keys are `hash`

Because of this, readers that assume only one datatype can throw `WRONGTYPE` errors.

## Current Read/Write Reality
### Writer behavior in this app
`CandleRedisService.pushCandle(...)` / `pushCandlesBatch(...)` write using legacy schema:

- `candle:{symbol}:{timeframe}` as `zset` (epoch indexed)
- `candle:{symbol}:{timeframe}:data` as `hash` (epoch -> JSON)

### Reader behavior (updated)
`CandleRedisService.getCandles(...)` is now **list-first** with fallback:

1. Try list read from `candle:{symbol}:{timeframe}`.
2. If Redis returns `WRONGTYPE`, fallback to:
- zset + hash read (`reverseRange` + `multiGet`), or
- hash-only fallback (sort hash fields by epoch desc, take latest N).

This avoids hard failure in mixed-schema environments and reduces extra calls vs pre-checking key type with `TYPE`.

## Why list-first
Market-feed behavior indicates active candle writes are primarily list-based. So reading list first is lower latency for the most common path and avoids extra `TYPE` requests per call.

## Service Methods and Impact
Updated file:

- `signal/src/main/java/com/quantlab/signal/service/redisService/CandleRedisService.java`

Important methods:

- `getCandles(...)`: list-first + legacy fallback
- `pushCandle(...)`: writes zset + hash
- `pushCandlesBatch(...)`: batch writes zset + hash
- `getCandlesByRange(...)`: zset/hash path
- `getLatestCandle(...)`: uses `getCandles(..., 1)`
- `deleteOldCandles(...)`: deletes zset/hash entries
- `getCandleCount(...)`: counts zset entries

## Strategy Impact
Strategies using `getCandles(...)` should continue to work under mixed Redis schemas because read-path is now tolerant:

- list keys read directly
- legacy zset/hash keys read via fallback

If a key is malformed or partially missing (for example, zset exists but hash payload missing), result may be empty for that symbol/timeframe.

## Known Drawbacks
1. Mixed-schema support increases code complexity.
2. Fallback after `WRONGTYPE` uses exception path for non-list keys.
3. Write schema and dominant read schema are currently not unified.

## Recommended Next Step (Cleanup)
Run a one-time migration to unify all candle keys to a single schema (preferably list if market-feed is canonical), then simplify service:

1. migrate legacy zset/hash keys -> list
2. remove legacy fallback logic
3. keep a single read/write model

This gives predictable performance and removes ongoing `WRONGTYPE` handling.

## Quick Redis Validation Commands
```bash
TYPE candle:NIFTY:72147:5m
TYPE candle:NIFTY:72147:5m:data
LRANGE candle:NIFTY:72147:5m 0 2
ZREVRANGE candle:NIFTY:72147:5m 0 2
HGETALL candle:NIFTY:72147:5m:data
```

Interpretation:
- if `TYPE candle:...` is `list`, primary path is used
- if `TYPE candle:...` is `zset`, fallback path reads zset/hash
- if only `:data` hash exists, hash-only fallback is used
