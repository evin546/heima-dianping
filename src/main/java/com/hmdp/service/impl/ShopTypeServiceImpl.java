package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryTypeList() {
        //查询缓存
        String shopListJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_LIST_KEY);
        if(!StrUtil.isBlank(shopListJson)){
            List<ShopType> shopTypeList = JSONUtil.toList(shopListJson, ShopType.class);
            return Result.ok(shopTypeList);
        }

        //缓存未命中：查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if(shopTypeList.isEmpty()){
            return Result.fail("查询店铺类型失败！");
        }
        //写入缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_LIST_KEY, JSONUtil.toJsonStr(shopTypeList));

        return Result.ok(shopTypeList);
    }
}
