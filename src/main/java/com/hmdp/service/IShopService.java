package com.hmdp.service;

import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return Shop 商铺详情数据
     */
    Shop queryShopById(Long id);

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    Boolean updateShop(Shop shop);

}
