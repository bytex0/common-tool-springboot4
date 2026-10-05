-- KEYS: permits sorted set, capacity; ARGV: unique token, capacity, lease milliseconds.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local capacity = redis.call('GET', KEYS[2])
if capacity and tonumber(capacity) ~= tonumber(ARGV[2]) then
    return -1
end
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then
    return 0
end
redis.call('SET', KEYS[2], ARGV[2], 'PX', 259200000)
redis.call('ZADD', KEYS[1], now + tonumber(ARGV[3]), ARGV[1])
redis.call('PEXPIRE', KEYS[1], 259200000)
return 1
