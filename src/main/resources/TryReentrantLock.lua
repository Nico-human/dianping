---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by Jo.
--- DateTime: 2024/8/12 9:45
--- redisson源码
--- 获取锁:
local key = KEYS[1]; -- 锁的key
local threadId = ARGV[1] -- 线程唯一标识
local releaseTime = ARGV[2] -- 锁的自动释放时间
-- 判断是否存在
if (redis.call('exists', key) == 0) then
    -- 不存在, 获取锁
    redis.call('hset', key, threadId, '1');
    -- 设置有效期
    redis.call('expire', key, releaseTime);
    return 1; -- 返回结果
end
--  锁已经存在, 判断是否是自己的
if (redis.call('hexists', key, threadId) == 1) then
    -- 是自己的, 重入次数加一
    redis.call('hincrby', key, threadId, '1');
    -- 设置有效期
    redis.call('expire', key, releaseTime);
    return 1; -- 返回结果
end
return 0; -- 获取锁的不是自己, 获取锁失败