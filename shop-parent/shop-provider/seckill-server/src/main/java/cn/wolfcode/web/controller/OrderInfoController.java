package cn.wolfcode.web.controller;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.DateUtil;
import cn.wolfcode.util.UserUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by lanxw
 */
@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    /**
     * 线程 500，循环 10
     * QPS : 789/sec
     * QPS : 1301/sec
     * @param time
     * @param seckillId
     * @param request
     * @return
     */

    @RequestMapping("/doSeckill")
    @RequireLogin
    public Result<String> doSeckill(Integer time, Long seckillId, HttpServletRequest request){
        //1、判断是否处于抢购时间
//        SeckillProductVo seckillProductVo = seckillProductService.find(time, seckillId);
        SeckillProductVo seckillProductVo = seckillProductService.findFromCache(time, seckillId);
/*        boolean legalTime = DateUtil.isLegalTime(
                seckillProductVo.getStartDate(), seckillProductVo.getTime());
        if (!legalTime){
            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);
        }*/
        //2、一个用户只能抢购一个商品
        //获取token信息以查到手机号,@RequireLogin能进来表明token已经不为空
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        //根据token从redis中获取手机号
        String phone = UserUtil.getUserPhone(redisTemplate, token);
        String orderSetKey = SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(String.valueOf(seckillId));
        if (redisTemplate.opsForSet().isMember(orderSetKey, phone)){
            //提示重复下单
            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);
        }
/*        OrderInfo orderInfo = orderInfoService.findByPhoneAndSeckillId(phone, seckillId);
        if (orderInfo != null){
            //提示重复下单
            return Result.error(SeckillCodeMsg.REPEAT_SECKILL);
        }*/
/*        //3、保证库存数量是足够的
        if (seckillProductVo.getCurrentCount() <= 0){
            //提示库存不足
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }*/
        //使用redis控制秒杀请求人数
        String seckillStockCountKey =
                SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(String.valueOf(time));
        Long remainCount = redisTemplate.
                opsForHash().increment(seckillStockCountKey, String.valueOf(seckillId), -1);
        if (remainCount < 0){
            return Result.error(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //使用MQ方式异步下单
        //发送MQ消息
        //只是发送消息同步，但整体的流程被异步解构了（业务是异步的）
        OrderMessage message = new OrderMessage(time, seckillId, token, Long.parseLong(phone));
        rocketMQTemplate.syncSend(MQConstant.ORDER_PEDDING_TOPIC, message);
        return Result.success("成功进入秒杀队列，请耐心等待结果");
//        OrderInfo orderInfo = orderInfoService.doSeckill(phone, seckillProductVo);//保证原子性，写在一个方法里
//        //4、创建秒杀订单（保证原子性）
//        //5、扣减数据库库存（保证原子性）
//        return Result.success(orderInfo.getOrderNo());
    }
    @RequestMapping("/find")
    @RequireLogin
    public Result<OrderInfo> find(String orderNo, HttpServletRequest request){
        //只能自己订单
        OrderInfo orderInfo = orderInfoService.findByOrderNo(orderNo);
        String token = request.getHeader(CommonConstants.TOKEN_NAME);
        String phone = UserUtil.getUserPhone(redisTemplate, token);
        if (!phone.equals(String.valueOf(orderInfo.getUserId()))){
            return Result.error(CommonCodeMsg.ILLEGAL_OPERATION);
        }
        return Result.success(orderInfo);
    }
}