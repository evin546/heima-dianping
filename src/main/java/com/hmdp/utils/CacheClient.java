package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.utils.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 在Redis中插入新key，带TTL过期时间
     * @param key
     * @param t
     * @param ttl
     * @param timeUnit
     * @param <T>
     */
    public <T> void set(String key, T t, long ttl, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(t), ttl, timeUnit);
    }

    /**
     * 向Redis中插入新key，带逻辑过期时间
     * @param key
     * @param t
     * @param logicalTtl
     * @param timeUnit
     * @param <T>
     */
    public <T> void setWithLogicalExpire(String key, T t, long logicalTtl, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(t);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(logicalTtl)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询数据方法，使用缓存空值方法解决缓存穿透问题
     * @param redisKeyPrefix redis中存储的key值前缀
     * @param dbId 所查询数据在数据库中的id
     * @param entity 所查询数据在对应的实体类型
     * @param dbFallback 数据库查询处理函数
     * @param ttl
     * @param timeUnit
     * @return
     * @param <ID> 数据库id的数据类型
     * @param <R> 所查询数据对应的实体类型
     */
    public <ID,R> R queryWithPassThrough(
            String redisKeyPrefix, ID dbId, Class<R> entity, Function<ID, R> dbFallback, long ttl, TimeUnit timeUnit){
        String redisKey = redisKeyPrefix + dbId.toString();
        //查询Redis
        String resJson = stringRedisTemplate.opsForValue().get(redisKey);
        //缓存命中且非空值，直接返回
        if(!StrUtil.isBlank(resJson)){
            return JSONUtil.toBean(resJson, entity);
        }
        //缓存命中空值
        if(resJson != null){
            return null;
        }

        //缓存不命中：
        //查询数据库
        R r = dbFallback.apply(dbId);
        //数据库中无对应结果，缓存空值并返回
        if(r == null){
            stringRedisTemplate.opsForValue().set(redisKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            return null;
        }

        //有对应结果
        this.set(redisKey, r, ttl, timeUnit);
        return r;
    }

    /**
     * 查询数据方法，使用互斥锁+逻辑过期解决缓存击穿问题（需要缓存预热）
     * @param redisKeyPrefix
     * @param dbId
     * @param entity
     * @param dbFallback
     * @param ttl
     * @param timeUnit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(
            String redisKeyPrefix, ID dbId, Class<R> entity, Function<ID, R> dbFallback, long ttl, TimeUnit timeUnit){
        String redisKey = redisKeyPrefix + dbId.toString();
        String resJson = stringRedisTemplate.opsForValue().get(redisKey);
        //缓存未命中：
        if(StrUtil.isBlank(resJson)){
            return null;
        }
        //命中：
        //转换成Bean
        RedisData redisData = JSONUtil.toBean(resJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), entity);
        //判断是否过期：
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //未过期，返回数据
            return r;
        }
        //已过期,进行缓存重建：
        String lockKey = RedisConstants.LOCK_SHOP_KEY + dbId.toString();
        //尝试获取锁：
        if(tryLock(lockKey)){
            if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
                return r;
            }
            //获取成功，进行缓存重建
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    R r1 = dbFallback.apply(dbId);
                    this.setWithLogicalExpire(redisKey, r1, ttl, timeUnit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unlock(lockKey);
            }
        }
        //返回过期的信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }




}
