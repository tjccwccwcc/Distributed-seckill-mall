package cn.wolfcode.service;


import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;

import java.util.Map;

/**
 * Created by wolfcode-lanxw
 */
public interface IOrderInfoService {
    /**
     * 根据用户手机号码和秒杀商品Id查询商品信息
     * @param phone
     * @param seckillId
     * @return
     */
    OrderInfo findByPhoneAndSeckillId(String phone, Long seckillId);

    /**
     * 创建秒杀订单
     * @param phone
     * @param seckillProductVo
     * @return
     */
    OrderInfo doSeckill(String phone, SeckillProductVo seckillProductVo);

    /**
     * 根据订单号查询订单对象
     * @param orderNo
     * @return
     */
    OrderInfo findByOrderNo(String orderNo);

    /**
     * 根据订单判断是否超时，超时取消订单
     * @param orderNo
     */
    void cancelOrder(String orderNo);
}
