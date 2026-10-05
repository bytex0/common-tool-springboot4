-- Accept the original (capacity, rate, client timestamp) arguments.
if #ARGV == 3 then
    local capacity, rate = ARGV[1], ARGV[2]
    ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5], ARGV[6] = 1000, capacity, capacity, rate, 1, ''
end
local key = KEYS[1]
local capacity = tonumber(ARGV[3])
local rate = tonumber(ARGV[4])
local permits = tonumber(ARGV[5])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local state = redis.call('HMGET', key, 'tokens', 'time')
local tokens = tonumber(state[1]) or capacity
local previous = tonumber(state[2]) or now
tokens = math.min(capacity, tokens + math.max(0, now - previous) * rate / 1000)
local allowed = 0
if tokens >= permits then tokens = tokens - permits; allowed = 1 end
redis.call('HSET', key, 'tokens', tokens, 'time', math.max(now, previous))
redis.call('PEXPIRE', key, math.max(1000, math.ceil(capacity / rate * 1000)))
return allowed
