-- Accept the original (window seconds, limit) argument contract.
if #ARGV == 2 then
    local seconds, limit = ARGV[1], ARGV[2]
    ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5], ARGV[6] = tonumber(seconds) * 1000, limit, limit, 1, 1, ''
end
local key = KEYS[1]
local window = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local permits = tonumber(ARGV[5])
local count = tonumber(redis.call('GET', key)) or 0
if count + permits > limit then return 0 end
redis.call('INCRBY', key, permits)
if redis.call('PTTL', key) < 0 then redis.call('PEXPIRE', key, window) end
return 1
