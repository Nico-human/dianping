package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private final CacheClient cacheClient;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate, CacheClient cacheClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheClient = cacheClient;
    }

    @Override
    public Shop queryShopById(Long id) {

        // 解决缓存穿透
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

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
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return isUpdated;
    }



    private Shop queryWithPassThrough(Long id) {
        // 1.Redis中取
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if (StringUtils.hasText(shopJson)) {
            // 3.存在则直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            // 为"" ,则直接返回null;
            return null;
        }
        //4.不存在则查数据库
        Shop shop = this.getById(id);
        // 5.判断是否存在
        if (shop == null) {
            // 防止缓存穿透(缓存null值, 也就是空字符串""), ps: 另一种方法: 布隆过滤器
            // 6.不存在则将null值("")缓存到Redis中, 返回null
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,
                    "",
                    RedisConstants.CACHE_NULL_TTL,
                    TimeUnit.MINUTES);
            return null;
        }
        // 7.存在则缓存在Redis中, 设置过期时间(TTL加上随机数, 防止缓存雪崩)，并返回
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,
                JSONUtil.toJsonStr(shop),
                CACHE_SHOP_TTL + new Random().nextInt(5),
                TimeUnit.MINUTES);
        return shop;
    }

    private Shop queryWithMutex(Long id) {
        // 1.Redis中取
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.判断是否存在
        if (StringUtils.hasText(shopJson)) {
            // 3.存在则直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            // 为"" ,则直接返回null;
            return null;
        }

        // 4.实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            // 4.2 判断是否获取成功
            boolean isLock = this.tryLock(lockKey);
            if (!isLock) {
                // 4.3 失败, 则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //4.4 成功, 根据id查数据库
            shop = this.getById(id);
            // 5.判断是否存在
            if (shop == null) {
                // 防止缓存穿透(缓存null值, 也就是空字符串""), ps: 另一种方法: 布隆过滤器
                // 6.不存在则将null值("")缓存到Redis中, 返回null
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,
                        "",
                        RedisConstants.CACHE_NULL_TTL,
                        TimeUnit.MINUTES);
                return null;
            }
            // 7.存在则缓存在Redis中, 设置过期时间(TTL加上随机数, 防止缓存雪崩)，并返回
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,
                    JSONUtil.toJsonStr(shop),
                    CACHE_SHOP_TTL + new Random().nextInt(5),
                    TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            this.unlock(lockKey);
        }

        return shop;
    }

    private Shop queryWithLogicExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1.Redis中取
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (!StringUtils.hasText(shopJson)) {
            // 3.不存在, 直接返回
            return null;
        }
        //4. 存在, 需要先把Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //5. 判断是否逻辑过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期, 直接返回
            return shop;
        }
        // 已过期, 需要缓存重建
        //6. 缓存重建
        //6.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = this.tryLock(lockKey);
        //6.2 判断是否获取锁成功
        if (isLock) {
            //doubleCheck缓存是否过期
            shopJson = stringRedisTemplate.opsForValue().get(key);
            expireTime = JSONUtil.toBean(shopJson, RedisData.class).getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期, 直接返回
                return shop;
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    this.unlock(lockKey);
                }
            });
        }
        // 若未获取到锁, 直接返回已过期的数据
        return shop;
    }

    @Override
    public void saveShop2Redis(Long id, Long expireTime) {
        RedisData redisData = new RedisData();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        redisData.setData(this.getById(id));
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock (String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "12306", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock (String key) {
        stringRedisTemplate.delete(key);
    }

}
