package cn.wolfcode.mq;

import cn.wolfcode.service.ISeckillProductService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        consumerGroup = "OrderResultFailGroup",
        topic = MQConstant.ORDER_RESULT_TOPIC,
        selectorExpression = MQConstant.ORDER_RESULT_FAIL_TAG)
public class OrderResultFailQueueListener implements RocketMQListener<OrderMQResult> {
    @Autowired
    private ISeckillProductService iSeckillProductService;
    @Override
    public void onMessage(OrderMQResult orderMQResult) {
        System.out.println("失败之后进行预库存回补");
        iSeckillProductService.syncStockToRedis(orderMQResult.getTime(), orderMQResult.getSeckillId());
    }
}
