package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.feign.ProductFeignApi;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLOutput;
import java.util.*;

/**
 * Created by lanxw
 */
@Service
public class SeckillProductServiceImpl implements ISeckillProductService {
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private ProductFeignApi productFeignApi;

    @Override
    public List<SeckillProductVo> queryByTime(Integer time) {
        //1、查询秒杀商品集合数据（场次查询当天数据）
        List<SeckillProduct> seckillProductList =
                seckillProductMapper.queryCurrentlySeckillProduct(time);
        if (seckillProductList.size() == 0){
            return Collections.EMPTY_LIST;
        }
        //2、遍历秒杀商品集合
        List<Long> productId = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProductList) {
            productId.add(seckillProduct.getProductId());
        }
//        System.out.println("当前的productId个数为：" + productId.size() + "___________________________");
//        System.out.println("当前的productId第一个为：" + productId.get(0));
        //3、远程调用，获取商品集合
        Result<List<Product>> result = productFeignApi.queryByIds(productId);
//        System.out.println("当前result是否出错：" + result.hasError() + "___________________________");
//        System.out.println(result.getData());
        if (result == null || result.hasError()){
            throw new BusinessException(SeckillCodeMsg.PRODUCT_SERVER_ERROR);
        }
        List<Product> productList = result.getData();
        //定义ProductId和product的映射关系
        Map<Long,Product> productMap = new HashMap<>();
        for (Product product : productList) {
            productMap.put(product.getId(), product);
        }
        //4、将商品和秒杀数据集合，封装Vo并返回
        List<SeckillProductVo> seckillProductVoList = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProductList) {
            SeckillProductVo vo = new SeckillProductVo();
            //把SeckillProduct数据和Product数据封装到vo中
            Product product = productMap.get(seckillProduct.getProductId());
            //将数据拷贝到vo对象中，product得在seckillProduct之前添加，保证vo中id是seckillProduct的id
            BeanUtils.copyProperties(product, vo);
            BeanUtils.copyProperties(seckillProduct, vo);
            vo.setCurrentCount(seckillProduct.getStockCount());//当前数量默认等于库存数量
            seckillProductVoList.add(vo);
        }
        return seckillProductVoList;
    }

    @Override
    public SeckillProductVo find(Integer time, Long seckillId) {
        //查询秒杀商品对象
        SeckillProduct seckillProduct = seckillProductMapper.find(seckillId);
        //根据id查询商品对象
        List<Long> productId = new ArrayList<>();
        productId.add(seckillProduct.getProductId());
        Result<List<Product>> result = productFeignApi.queryByIds(productId);
        if (result == null || result.hasError()){
            throw new BusinessException(SeckillCodeMsg.PRODUCT_SERVER_ERROR);
        }
        Product product = result.getData().get(0);
        //将数据封装成商品对象
        SeckillProductVo vo = new SeckillProductVo();
        BeanUtils.copyProperties(product, vo);
        BeanUtils.copyProperties(seckillProduct, vo);
        vo.setCurrentCount(seckillProduct.getStockCount());
        return vo;
    }

    @Override
    public int decrStockCount(Long seckillId) {
        return seckillProductMapper.decrStock(seckillId);
    }

    @Override
    public List<SeckillProductVo> queryByTimeFromCache(Integer time) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        List<Object> objectList = redisTemplate.opsForHash().values(key);
        List<SeckillProductVo> seckillProductVoList = new ArrayList<>();
        for (Object objStr : objectList) {
            seckillProductVoList.add(JSON.parseObject((String) objStr, SeckillProductVo.class));
        }
//        System.out.println("Key为：" + key);
//        System.out.println("值为：" + redisTemplate.opsForHash().values(key));
//        System.out.println("列表大小为：" + objectList.size());
//        System.out.println("列表2大小为：" + seckillProductVoList.size());
        return seckillProductVoList;
    }

    @Override
    public SeckillProductVo findFromCache(Integer time, Long seckillId) {
        String key = SeckillRedisKey.SECKILL_PRODUCT_HASH.getRealKey(String.valueOf(time));
        Object strObj = redisTemplate.opsForHash().get(key, String.valueOf(seckillId));
        SeckillProductVo vo = JSON.parseObject((String) strObj, SeckillProductVo.class);
        return vo;
    }
}
