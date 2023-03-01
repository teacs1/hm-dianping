package com.hmdp.utils;

public interface ILock {

    /**
     *尝试获取锁
     * @param timeoutSec    锁持有的时间,超时自动释放
     * @return true代表锁获取成功
     */
    boolean tryLock(long timeoutSec);

    /**
     *释放锁
     */
    void unlock();
}
