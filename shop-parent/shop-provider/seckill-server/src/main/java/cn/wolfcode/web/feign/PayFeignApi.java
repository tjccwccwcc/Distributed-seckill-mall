package cn.wolfcode.web.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.web.feign.fallback.PayFeignFallback;
import cn.wolfcode.web.feign.fallback.ProductFeignFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Service
@FeignClient(name = "pay-service", fallback = PayFeignFallback.class)
public interface PayFeignApi {
    @RequestMapping("/alipay/payOnline")
    Result<String> payOnline(@RequestBody PayVo vo);
}
