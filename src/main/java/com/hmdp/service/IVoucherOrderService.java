package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 执行秒杀操作
     * @param voucherId 优惠券Id
     * @return orderId 订单Id
     */
    Long seckillVoucher(Long voucherId);

    Long createVoucherOrder(Long voucherId);
}
