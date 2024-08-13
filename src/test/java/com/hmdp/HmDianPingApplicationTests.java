package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private IShopService shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;

    private final ExecutorService executorService = Executors.newFixedThreadPool(500);

    @Test
    void RefreshRedisStock() {
        stringRedisTemplate.delete(RedisConstants.SECKILL_STOCK_KEY + "11");
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + "11", "100");
        stringRedisTemplate.delete("seckill:order:11");
    }

    @Test
    void storeUser2Redis() {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(User::getId, User::getNickName);
        List<User> userList = userMapper.selectList(queryWrapper);

        // 文件路径，可以根据需要更改路径和文件名
        String filePath = "user_tokens.txt";

        // 使用try-with-resources语句，确保文件流在操作后关闭
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            userList.stream().forEach(user -> {
                String token = generateUUID();

                UserDTO userDTO = new UserDTO();
                BeanUtils.copyProperties(user, userDTO);

                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                        new HashMap<>(),
                        CopyOptions.create().setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) ->
                                        fieldValue != null ? fieldValue.toString() : null));

                stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);

                // 将token写入文件，每个token占一行
                try {
                    writer.write(token);
                    writer.newLine();
                } catch (IOException e) {
                    e.printStackTrace();  // 记录写入错误
                }
            });
        } catch (IOException e) {
            e.printStackTrace();  // 记录文件操作错误
        }

    }

    private String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

//    @Test
//    void storeUser2Redis() {
//        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
//        queryWrapper.select(User::getId, User::getNickName, User::getPhone);
//        List<User> userList = userMapper.selectList(queryWrapper);
//
//        userList.stream().forEach((user) -> {
//            String token = UUID.randomUUID().toString().replaceAll("-", "");
//            UserDTO userDTO = new UserDTO();
//            BeanUtils.copyProperties(user, userDTO);
//
//            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
//                    new HashMap<>(),
//                    CopyOptions.create().setIgnoreNullValue(true)
//                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
//
//            stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token, userMap);
//        });
//    }


    @Test
    void testSaveShop() {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("Time: " + (end - begin));
    }

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }


}
