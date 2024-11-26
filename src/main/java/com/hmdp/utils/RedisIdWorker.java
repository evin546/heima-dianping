package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    public long nextId(String keyPrefix){
        LocalDateTime now = LocalDateTime.now();

        //获取当前时间时间戳
        long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        //获取系统时间戳
        long sysTimestamp = nowTimestamp - BEGIN_TIMESTAMP;

        String nowDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        //利用Redis进行订单计数
        Long count = stringRedisTemplate.opsForValue().increment(
                RedisConstants.INCREASE_ID_PREFIX + keyPrefix + ":" + nowDate);


        return sysTimestamp << COUNT_BITS | count;
    }


}
