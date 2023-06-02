package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.*;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.feign.IntegralFeignApi;
import cn.wolfcode.web.feign.PayFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by wolfcode-lanxw
 */
@Service
//@RestController
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayFeignApi payFeignApi;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Autowired
    private IntegralFeignApi integralFeignApi;

    @Override
    public OrderInfo findByPhoneAndSeckillId(String phone, Long seckillId) {
        return orderInfoMapper.findByPhoneAndSeckillId(phone, seckillId);
    }

    @Override
    @Transactional//保证原子性
    public OrderInfo doSeckill(String phone, SeckillProductVo seckillProductVo) {
//        int i = 1/0;//模拟订单创建失败
        //4、扣减数据库库存
        int effectCount = seckillProductService.decrStockCount(seckillProductVo.getId());
        if (effectCount == 0){
            //表明数据库中 update 语句执行结果影响的行数为 0，stock_count > 0 条件不满足，即库存不足，抛出异常。
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //5、创建秒杀订单
        OrderInfo orderInfo = createOrderInfo(phone, seckillProductVo);
        //放到canal中做
//        //在 Redis 设置 Set 集合，存储抢到秒杀商品的用户的手机号
//        //seckillOrderSet:12 ===> [13088889999,13066668888]
//        String orderSetKey = SeckillRedisKey.SECKILL_ORDER_SET.
//                getRealKey(String.valueOf(seckillProductVo.getId()));
//        redisTemplate.opsForSet().add(orderSetKey, phone);
        return orderInfo;
    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        //从redis中查询
        String orderHashKey = SeckillRedisKey.SECKILL_ORDER_HASH.getRealKey("");
        String objStr = (String) redisTemplate.opsForHash().get(orderHashKey, orderNo);
        return JSON.parseObject(objStr, OrderInfo.class);
//        return orderInfoMapper.find(orderNo);
    }


    private OrderInfo createOrderInfo(String phone, SeckillProductVo seckillProductVo) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(seckillProductVo, orderInfo);
        orderInfo.setUserId(Long.parseLong(phone));//用户ID
        orderInfo.setCreateDate(new Date());//订单创建时间
        orderInfo.setDeliveryAddrId(1L);//订单收货地址
        orderInfo.setSeckillDate(seckillProductVo.getStartDate());//秒杀商品的日期
        orderInfo.setSeckillTime(seckillProductVo.getTime());//秒杀商品场次
        orderInfo.setOrderNo(String.valueOf(IdGenerateUtil.get().nextId()));//订单编号
        orderInfo.setSeckillId(seckillProductVo.getId());//秒杀的Id
        orderInfoMapper.insert(orderInfo);
        return orderInfo;
    }

    @Override
    @Transactional//原子性
    public void cancelOrder(String orderNo) {
        System.out.println("超时取消订单逻辑开始");
        OrderInfo orderInfo = orderInfoMapper.find(orderNo);
        //判断订单是否处于未付款状态
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            //修改订单状态
            int effectCount = orderInfoMapper.updateCancelStatus(orderNo, OrderInfo.STATUS_TIMEOUT);
            if (effectCount == 0){
                return;//代表修改订单冲突了（比如支付线程成功支付），修改失败，直接返回
            }
            //真实库存回补
            seckillProductService.incrStockCount(orderInfo.getSeckillId());
            //预库存回补
            seckillProductService.syncStockToRedis(orderInfo.getSeckillTime(), orderInfo.getSeckillId());
        }
        System.out.println("超时取消订单逻辑结束");
    }

    @Value("${pay.returnUrl}")
    private String returnUrl;
    @Value("${pay.notifyUrl}")
    private String notifyUrl;
    @Override
    public Result<String> payOnline(String orderNo) {
        //根据订单号查询订单对象
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            PayVo vo = new PayVo();
            vo.setOutTradeNo(orderNo);//订单编号
            vo.setTotalAmount(String.valueOf(orderInfo.getSeckillPrice()));//金额
            vo.setSubject(orderInfo.getProductName());//主题
            vo.setBody(orderInfo.getProductName());//详情
            vo.setReturnUrl(returnUrl);
            vo.setNotifyUrl(notifyUrl);
            Result<String> result = payFeignApi.payOnline(vo);
            return result;
        }
        return Result.error(SeckillCodeMsg.PAY_STATUS_CHANGE);
    }

    @Override
    public int changePayStatue(String orderNo, Integer status, int payType) {
        return orderInfoMapper.changePayStatus(orderNo, status, payType);
    }

    @Override
    public void refundOnline(OrderInfo orderInfo) {
        RefundVo vo = new RefundVo();
        vo.setOutTradeNo(orderInfo.getOrderNo());
        vo.setRefundAmount(String.valueOf(orderInfo.getSeckillPrice()));
        vo.setRefundReason("不想要了");
        Result<Boolean> result = payFeignApi.refund(vo);
        if (result == null || result.hasError() || !result.getData()){
            throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
        }
        orderInfoMapper.changeRefundStatus(orderInfo.getOrderNo(), OrderInfo.STATUS_REFUND);
    }

    @Override
    @GlobalTransactional
    public void payIntegral(String orderNo) {
        OrderInfo orderInfo = this.findByOrderNo(orderNo);
        if (OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            //处于未支付状态
            //插入支付日志记录
            PayLog log = new PayLog();
            log.setOrderNo(orderNo);
            log.setPayTime(new Date());
            log.setTotalAmount(String.valueOf(orderInfo.getIntergral()));
            log.setPayType(OrderInfo.PAYTYPE_INTERGRAL);
            payLogMapper.insert(log);
            //远程调用积分服务完成积分扣减
            OperateIntergralVo vo = new OperateIntergralVo();
            vo.setUserId(orderInfo.getUserId());
            vo.setValue(orderInfo.getIntergral());
            //调用积分服务
            Result result = integralFeignApi.decrIntegral(vo);
            if(result == null || result.hasError()){
                throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
            }
            //修改订单状态
            int effectCount = orderInfoMapper.changePayStatus(orderNo,
                    OrderInfo.STATUS_ACCOUNT_PAID,
                    OrderInfo.PAYTYPE_INTERGRAL);
            if (effectCount == 0){//订单修改失败
                throw new BusinessException(SeckillCodeMsg.PAY_ERROR);
            }
//            int i = 1/0;//测试异常
        }
        else throw new BusinessException(SeckillCodeMsg.PAY_STATUS_CHANGE);
    }

    @Override
    @GlobalTransactional
    public void refundIntegral(OrderInfo orderInfo) {
        if (OrderInfo.STATUS_ACCOUNT_PAID.equals(orderInfo.getStatus())){
            //添加退款记录
            RefundLog log = new RefundLog();
            log.setOrderNo(orderInfo.getOrderNo());
            log.setRefundAmount(orderInfo.getIntergral());
            log.setRefundReason("不要了");
            log.setRefundTime(new Date());
            log.setRefundType(OrderInfo.PAYTYPE_INTERGRAL);
            refundLogMapper.insert(log);
            //远程调用服务增加积分
            OperateIntergralVo vo = new OperateIntergralVo();
            vo.setUserId(orderInfo.getUserId());
            vo.setValue(orderInfo.getIntergral());
            //调用积分服务
            Result result = integralFeignApi.incrIntegral(vo);
            if(result == null || result.hasError()){
                throw new BusinessException(SeckillCodeMsg.INTERGRAL_SERVER_ERROR);
            }
            //修改订单状态
            int effectCount = orderInfoMapper.changeRefundStatus(
                    orderInfo.getOrderNo(),
                    OrderInfo.STATUS_REFUND);
            if (effectCount == 0){//订单修改失败
                throw new BusinessException(SeckillCodeMsg.REFUND_ERROR);
            }
//            int i = 1/0;//测试异常情况
            //undo_log测试
//            try {
//                TimeUnit.SECONDS.sleep(30);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        }
    }
}
