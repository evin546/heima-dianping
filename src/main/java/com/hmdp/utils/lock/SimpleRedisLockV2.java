package com.hmdp.utils.lock;

import cn.hutool.core.lang.UUID;
import com.hmdp.utils.constant.RedisConstants;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 上锁时存入线程标识，释放锁前做校验，防止出现线程安全问题
 */
public class SimpleRedisLockV2 implements ILock{
    private final StringRedisTemplate stringRedisTemplate;

    private final String lockKey;

    //机器Id:每一台机器生成唯一的UUID，与机器内线程id配合，唯一标识集群内的每一个线程
    //machineId是static变量，随类加载而加载，在不同的对象中是一致的
    private static final String machineId = UUID.randomUUID().toString(true);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLockV2(StringRedisTemplate stringRedisTemplate, String lockName){
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockKey = RedisConstants.LOCK_KEY_PREFIX + lockName;
    }



    @Override
    public boolean tryLock(long timeoutSeconds) {
        String threadId = machineId + "-" + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey, threadId,
                timeoutSeconds, TimeUnit.SECONDS);

        //上面返回的是包装类Boolean，直接return会自动拆箱，当success == null时会报空指针异常，不能直接返回
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        String threadId = machineId + "-" + Thread.currentThread().getId();
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(lockKey),
                threadId
        );
    }

/*  @Override
    public void unlock() {
        //获取自身线程标识
        String threadId = machineId + "-" + Thread.currentThread().getId();
        //获取锁内线程标识
        String lockThreadId = stringRedisTemplate.opsForValue().get(lockKey);

        if(threadId.equals(lockThreadId)) {
            stringRedisTemplate.delete(lockKey);
        }
    }*/




}
