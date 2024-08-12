package com.hmdp.mq;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.hmdp.config.RabbitMQTopicConfig.*;

/**
 * @Description: 消息消费者
 * @Author: dong
 * @Date: 2024/8/12
 */

@Slf4j
@Component
public class MqReceiver {

    private final VoucherOrderMapper voucherOrderMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;

    public MqReceiver(VoucherOrderMapper voucherOrderMapper, SeckillVoucherMapper seckillVoucherMapper) {
        this.voucherOrderMapper = voucherOrderMapper;
        this.seckillVoucherMapper = seckillVoucherMapper;
    }

    @Transactional
    @RabbitListener(queues = QUEUE)
    public void receiveSeckillMessage(String msg) {
        log.info("接受消息: " + msg);
        // JSON String -> voucherOrder
        VoucherOrder voucherOrder = JSON.parseObject(msg, VoucherOrder.class);

        Long orderId = voucherOrder.getId();
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();

        // 查库, 防止一人下多单, 数据库层面兜底
        LambdaQueryWrapper<VoucherOrder> voucherOrderWrapper = new LambdaQueryWrapper<>();
        voucherOrderWrapper.eq(VoucherOrder::getVoucherId, voucherId);
        voucherOrderWrapper.eq(VoucherOrder::getUserId, userId);
        Integer count = voucherOrderMapper.selectCount(voucherOrderWrapper);
        if (count > 0) {
            log.error("该用户已经购买过");
            return;
        }

        // 扣减库存, 防止超卖, 数据库层面兜底
        int rows = seckillVoucherMapper.seckill(voucherId);
        if (rows != 1) {
            log.error("库存不足");
            return;
        }
        // 保存订单
        voucherOrderMapper.insert(voucherOrder);
    }



}
