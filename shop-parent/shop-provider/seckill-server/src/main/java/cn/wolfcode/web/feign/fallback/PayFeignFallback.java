package cn.wolfcode.web.feign.fallback;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.PayVo;
import cn.wolfcode.web.feign.PayFeignApi;
import org.springframework.stereotype.Component;

@Component
public class PayFeignFallback implements PayFeignApi {
    @Override
    public Result<String> payOnline(PayVo vo) {
        return null;
    }
}
