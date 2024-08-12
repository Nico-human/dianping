package com.hmdp.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import static com.hmdp.config.RabbitMQTopicConfig.*;

/**
 * @Description: 消息生产者
 * @Author: dong
 * @Date: 2024/8/12
 */
@Slf4j
@Component
public class MqSender {

    private static final String MQ_ROUTING_KEY = "seckill.do";
    private final RabbitTemplate rabbitTemplate;

    public MqSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendSeckillMessage(String msg) {
        log.info("发送消息: " + msg);
        rabbitTemplate.convertAndSend(EXCHANGE, MQ_ROUTING_KEY, msg);
    }

}
