package com.hmdp.utils.lock;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeoutSeconds 过期时间（单位：秒）
     * @return 是否获取成功
     */
    public boolean tryLock(long timeoutSeconds);

    /**
     * 释放锁
     */
    public void unlock();


}
