-- Release can only remove its own still-valid token.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local expiry = redis.call('ZSCORE', KEYS[1], ARGV[1])
redis.call('ZREM', KEYS[1], ARGV[1])
if not expiry or tonumber(expiry) <= now then
    return 0
end
return 1
