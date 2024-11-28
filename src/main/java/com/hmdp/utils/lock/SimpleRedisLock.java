package com.hmdp.utils.lock;

import cn.hutool.core.util.StrUtil;
import com.hmdp.utils.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private final StringRedisTemplate stringRedisTemplate;

    private final String lockKey;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String lockName){
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockKey = RedisConstants.LOCK_KEY_PREFIX + lockName;
    }



    @Override
    public boolean tryLock(long timeoutSeconds) {
        long threadId = Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, String.valueOf(threadId), timeoutSeconds, TimeUnit.SECONDS);

        //上面返回的是包装类Boolean，直接return会自动拆箱，当success == null时会报空指针异常，不能直接返回
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(lockKey);
    }
}
