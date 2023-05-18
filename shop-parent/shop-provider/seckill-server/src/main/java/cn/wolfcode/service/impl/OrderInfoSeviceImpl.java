package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by wolfcode-lanxw
 */
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;

    @Override
    public OrderInfo findByPhoneAndSeckillId(String phone, Long seckillId) {
        return orderInfoMapper.findByPhoneAndSeckillId(phone, seckillId);
    }

    @Override
    @Transactional//保证原子性
    public OrderInfo doSeckill(String phone, SeckillProductVo seckillProductVo) {
        //4、扣减数据库库存
        int effectCount = seckillProductService.decrStockCount(seckillProductVo.getId());
        if (effectCount == 0){
            //表明数据库中 update 语句执行结果影响的行数为 0，stock_count > 0 条件不满足，即库存不足，抛出异常。
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //5、创建秒杀订单
        OrderInfo orderInfo = createOrderInfo(phone, seckillProductVo);
        //在 Redis 设置 Set 集合，存储抢到秒杀商品的用户的手机号
        //seckillOrderSet:12 ===> [13088889999,13066668888]
        String orderSetKey = SeckillRedisKey.SECKILL_ORDER_SET.
                getRealKey(String.valueOf(seckillProductVo.getId()));
        redisTemplate.opsForSet().add(orderSetKey, phone);
        return orderInfo;
    }

    @Override
    public OrderInfo findByOrderNo(String orderNo) {
        return orderInfoMapper.find(orderNo);
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
}