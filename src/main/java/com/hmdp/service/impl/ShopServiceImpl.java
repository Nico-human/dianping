package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Shop queryShopById(Long id) {
        // 1.Redis中取
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if (StringUtils.hasText(shopJson)) {
            // 3.存在则直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //4.不存在则查数据库
        Shop shop = this.getById(id);
        // 5.判断是否存在
        if (shop == null) {
            // 6.不存在返回null
            return null;
        }
        // 7.存在则缓存在Redis中, 设置过期时间，并返回
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,
                                                  JSONUtil.toJsonStr(shop),
                                                  RedisConstants.CACHE_SHOP_TTL,
                                                  TimeUnit.MINUTES);
        return shop;
    }

    @Transactional
    @Override
    public Boolean updateShop(Shop shop) {
        if (shop.getId() == null) {
            return false;
        }
        //1. 更新数据库
        boolean isUpdated = this.updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return isUpdated;
    }
}
