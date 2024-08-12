package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Description: 自己定义的Redis锁
 * @Author: dong
 * @Date: 2024/8/11
 */
@Slf4j
public class SimpleRedisLock implements ILock{

    private final String name;
    private static final String KEY_PREFIX = "lock:";
    /* ID_PREFIX用于区分不同的JVM */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    private final StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取锁
        String threadId = ID_PREFIX + "-" + Thread.currentThread().getId();
        Boolean isSuccess = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isSuccess);
    }

    @Override
    public void unLock() {
        // 使用lua脚本实现释放锁, 满足原子性
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + "-" + Thread.currentThread().getId());
    }


//    @Override
//    public void unLock() {
//        String threadId = ID_PREFIX + "-" + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        // 判断标识是否一致
//        if (threadId.equals(id)) {
//            // 判断标识是否一致 与 释放锁 不是同一原子操作
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }

}
