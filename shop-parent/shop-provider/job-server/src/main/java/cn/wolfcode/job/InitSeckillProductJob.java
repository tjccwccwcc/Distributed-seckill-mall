package cn.wolfcode.job;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.redis.JobRedisKey;
import cn.wolfcode.web.feign.SeckillProductFeignAPI;
import com.alibaba.fastjson.JSON;
import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认id：首字母小写
 */
@Component
@Setter
@Getter
public class InitSeckillProductJob implements SimpleJob {
    @Value("${jobCron.initSeckillProduct}")
    private String cron;
    @Autowired
    private SeckillProductFeignAPI seckillProductFeignAPI;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public void execute(ShardingContext shardingContext) {
        //远程调用秒杀服务获取秒杀列表集合
        String time = shardingContext.getShardingParameter();
        Result<List<SeckillProductVo>> result =
                seckillProductFeignAPI.queryByTimeForJob(Integer.parseInt(time));
        if (result == null || result.hasError()){
            //通知管理员
            return;
        }
        List<SeckillProductVo> seckillProductVoList = result.getData();
        //删除之前的数据
        //seckillProductHash:10(time)
        String key = JobRedisKey.SECKILL_PRODUCT_HASH.getRealKey(time);
        //库存数量key
        String seckillStockCountKey = JobRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time);
        redisTemplate.delete(key);
        redisTemplate.delete(seckillStockCountKey);
        //存储集合到redis中
        for (SeckillProductVo vo : seckillProductVoList) {
            redisTemplate.opsForHash().put(key, String.valueOf(vo.getId()), JSON.toJSONString(vo));
            //将数据同步到redis
            redisTemplate.opsForHash().put
                    (seckillStockCountKey, String.valueOf(vo.getId()), String.valueOf(vo.getStockCount()));
        }
    }
}
