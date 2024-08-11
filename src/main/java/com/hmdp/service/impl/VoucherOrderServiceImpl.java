package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final SeckillVoucherMapper seckillVoucherMapper;
    private final RedisIdWorker redisIdWorker;
    private final VoucherOrderMapper voucherOrderMapper;

    public VoucherOrderServiceImpl(SeckillVoucherMapper seckillVoucherMapper, RedisIdWorker redisIdWorker, VoucherOrderMapper voucherOrderMapper) {
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.redisIdWorker = redisIdWorker;
        this.voucherOrderMapper = voucherOrderMapper;
    }

    @Override
    public Long seckillVoucher(Long voucherId) {

        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        // 查看当前时间是否符合抢购时间
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return null;
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return null;
        }
        // 查看库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return null;
        }

        /**
        // 扣减库存 错误示例
        // 原因: 先查库存, 再减库存
        // 高并发时查到的是旧库存, 旧库存覆盖新库存导致超卖
        // LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
        // wrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        // wrapper.gt(SeckillVoucher::getStock, 0);
        // seckillVoucher.setStock(seckillVoucher.getStock() - 1);
        // int rows = seckillVoucherMapper.update(seckillVoucher, wrapper);
        // if (rows != 1) {
        //     return null;
        // }

          一人一单逻辑
               方法一: 下面这种做法: 在代码层面判断VoucherOrder表中是否存在同一用户的订单
               方法二: 在数据库层面添加联合唯一索引(user_id与voucher_id)
         */
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) { // 通过字符串的 intern 方法来确保同一用户 ID 的字符串在 JVM 中是唯一的
            // 获取代理对象 (事务), 直接调用this.createVoucherOrder会造成Spring事务失效
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Transactional
    @Override
    public Long createVoucherOrder(Long voucherId) {

        Long userId = UserHolder.getUser().getId();
        // 查询同一个用户的订单是否已经存在
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherOrder::getUserId, userId);
        wrapper.eq(VoucherOrder::getVoucherId, voucherId);
        int count = voucherOrderMapper.selectCount(wrapper);
        if (count > 0) {
            return null;
        }

        // 执行秒杀, 扣减库存
        int rows = seckillVoucherMapper.seckill(voucherId);
        if (rows != 1) {
            return null;
        }

        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        this.save(voucherOrder);

        // 返回代金券订单id
        return voucherOrder.getId();
    }


}
