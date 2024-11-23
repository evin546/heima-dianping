package com.hmdp.service.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    private final StringRedisTemplate stringRedisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //添加互斥锁方案解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //添加逻辑过期机制解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);
        if(shop == null){
            return Result.fail("无对应店铺信息！");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);    /**
     * 基于逻辑过期机制获取店铺信息，解决缓存穿透、击穿
     * 执行此方法前默认已做缓存预热，在Redis中无法命中的店铺信息将直接返回null
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //缓存未命中，直接返回null
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        //缓存命中：
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //判断是否过期
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //未过期，直接返回
            return shop;
        }
        //过期，缓存重建：
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        if (tryToGetLock(lockKey)) {
            //成功获取锁:
            //再次判断是否过期
            if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
                //未过期，直接返回
                return shop;
            }
            //开启新线程完成缓存更新
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        //返回过期的商户信息
        return shop;
    }


    /**
     * 店铺信息缓存预热，用于提前把热点Key写入缓存中
     * @param id
     */
    public void saveShop2Redis(Long id, Long expireSeconds){
        Shop shop = getById(id);
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //模拟缓存更新延迟
        ThreadUtil.safeSleep(500);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 基于互斥锁查询店铺信息，解决缓存穿透、击穿问题
     * @param id
     * @return
     */
    private Shop queryWithMutex(Long id){
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;

        //从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //判断缓存是否命中
        if(!StrUtil.isBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的缓存是否是空对象
        if(shopJson != null){
            return null;
        }

        //缓存未命中：
        //尝试获取互斥锁
        Shop shop = null;
        String lockShopKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            if (tryToGetLock(lockShopKey)){
                //获取成功，开始更新缓存:
                //查询数据库数据
                shop = getById(id);
                if(shop == null){
                    stringRedisTemplate.opsForValue().set(shopKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                //更新缓存
                stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            } else {
                //互斥锁获取失败，休眠重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockShopKey);
        }
        return shop;
    }

    /**
     * 尝试获取自定义的互斥锁
     * @param key
     * @return
     */
    public boolean tryToGetLock(String key){
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(result);
    }

    /**
     * 释放互斥锁
     * @param key
     */
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 根据ID查询店铺信息，解决缓存穿透问题
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id) {
        //从redis缓存中查询商铺
        String shopCacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopCacheKey);
        //缓存命中，直接返回
        if(!StrUtil.isBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //缓存命中空值，直接报错
        if(shopJson != null){
            return null;
        }

        //缓存未命中：
        //查询数据库
        Shop shop = getById(id);
        //数据库中没有对应信息
        if(shop == null){
            //缓存写入空值
            stringRedisTemplate.opsForValue().set(shopCacheKey, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //更新缓存
        stringRedisTemplate.opsForValue().set(shopCacheKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回数据
        return shop;
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空！");
        }
        //操作数据库
        updateById(shop);
        //更新缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
