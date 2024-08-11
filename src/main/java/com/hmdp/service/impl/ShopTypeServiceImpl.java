package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private final ShopTypeMapper shopTypeMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public ShopTypeServiceImpl(ShopTypeMapper shopTypeMapper, StringRedisTemplate stringRedisTemplate) {
        this.shopTypeMapper = shopTypeMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<ShopType> queryTypeList() {
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_TYPE_KEY, 0, -1);

        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            stringRedisTemplate.expire(RedisConstants.CACHE_TYPE_KEY, RedisConstants.CACHE_TYPE_TTL, TimeUnit.HOURS);
            return shopTypeList.stream()
                    .map(shopType -> JSONUtil.toBean(shopType, ShopType.class))
                    .collect(Collectors.toList());
        }

        List<ShopType> shopTypeList2 = shopTypeMapper.selectList(new QueryWrapper<>());

        if (shopTypeList2 == null || shopTypeList2.isEmpty()) {
            return null;
        }

        shopTypeList2 = shopTypeList2.stream()
                .sorted(Comparator.comparing(ShopType::getSort))
                .collect(Collectors.toList());

        shopTypeList = shopTypeList2.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());

        stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_TYPE_KEY, shopTypeList);
        stringRedisTemplate.expire(RedisConstants.CACHE_TYPE_KEY, RedisConstants.CACHE_TYPE_TTL, TimeUnit.HOURS);

        return shopTypeList2;
    }
}
