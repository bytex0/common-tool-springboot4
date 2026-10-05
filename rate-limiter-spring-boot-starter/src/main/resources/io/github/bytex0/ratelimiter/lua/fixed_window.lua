local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local permits = tonumber(ARGV[5])
local count = tonumber(redis.call('GET', key)) or 0
if count + permits > limit then return 0 end
redis.call('INCRBY', key, permits)
if redis.call('PTTL', key) < 0 then redis.call('PEXPIRE', key, window) end
return 1
