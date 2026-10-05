-- Accept the original (capacity, rate, client timestamp, permits) arguments.
if #ARGV == 4 then
    local capacity, rate, permits = ARGV[1], ARGV[2], ARGV[4]
    ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5], ARGV[6] = 1000, capacity, capacity, rate, permits, ''
end
local key = KEYS[1]
local capacity = tonumber(ARGV[3])
local rate = tonumber(ARGV[4])
local permits = tonumber(ARGV[5])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local state = redis.call('HMGET', key, 'water', 'time')
local water = tonumber(state[1]) or 0
local previous = tonumber(state[2]) or now
water = math.max(0, water - math.max(0, now - previous) * rate / 1000)
local allowed = 0
if water + permits <= capacity then water = water + permits; allowed = 1 end
-- 拒绝请求也必须保存已经排出的水量，不能仅更新时间。
redis.call('HSET', key, 'water', water, 'time', math.max(now, previous))
redis.call('PEXPIRE', key, math.max(1000, math.ceil(capacity / rate * 1000)))
return allowed
