package com.hmdp.utils;

/**
 * @Description: 自己定义的Redis分布式锁接口
 * @Author: dong
 * @Date: 2024/8/11
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的自动过期时间TTL
     * @return 是否获取成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();

}
