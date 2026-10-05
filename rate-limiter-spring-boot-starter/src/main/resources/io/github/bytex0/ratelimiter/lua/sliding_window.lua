local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local permits = tonumber(ARGV[5])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
if redis.call('ZCARD', key) + permits > limit then return 0 end
for i = 1, permits do redis.call('ZADD', key, now, ARGV[6] .. ':' .. i) end
redis.call('PEXPIRE', key, window)
return 1
