package cn.wolfcode.mq;

import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(consumerGroup = "pendingGroup", topic = MQConstant.ORDER_PEDDING_TOPIC)
public class OrderPendingQueueListener implements RocketMQListener<OrderMessage> {
    @Autowired
    private IOrderInfoService iOrderInfoService;
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Override
    public void onMessage(OrderMessage orderMessage) {
        OrderMQResult result = new OrderMQResult();
        result.setToken(orderMessage.getToken());
        String tag;
        try{
            SeckillProductVo vo = seckillProductService.findFromCache
                    (orderMessage.getTime(), orderMessage.getSeckillId());
            OrderInfo orderInfo = iOrderInfoService.doSeckill
                    (String.valueOf(orderMessage.getUserPhone()), vo);
            result.setOrderNo(orderInfo.getOrderNo());
            tag = MQConstant.ORDER_RESULT_SUCCESS_TAG;
            //发送延时消息
            Message<OrderMQResult> message = MessageBuilder.withPayload(result).build();
            rocketMQTemplate.syncSend(
                    MQConstant.ORDER_PAY_TIMEOUT_TOPIC,
                    message,
                    //timeout：消息发送超时时间（如：timeout=3000，就表示如果3秒消息还没发出那么就会抛异常）
                    3000,
                    //延迟级别：13 (10min)
                    MQConstant.ORDER_PAY_TIMEOUT_DELAY_LEVEL
            );
        }catch (Exception e){
            e.printStackTrace();
            result.setCode(SeckillCodeMsg.SECKILL_ERROR.getCode());
            result.setMsg(SeckillCodeMsg.SECKILL_ERROR.getMsg());
            result.setTime(orderMessage.getTime());
            result.setSeckillId(orderMessage.getSeckillId());
            tag = MQConstant.ORDER_RESULT_FAIL_TAG;
        }
        rocketMQTemplate.syncSend(MQConstant.ORDER_RESULT_TOPIC + ":" + tag, result);
    }
}
