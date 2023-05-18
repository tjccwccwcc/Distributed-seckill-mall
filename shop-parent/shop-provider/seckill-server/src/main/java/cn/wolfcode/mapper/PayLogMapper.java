package cn.wolfcode.mapper;

import cn.wolfcode.domain.PayLog;
import org.springframework.stereotype.Service;

/**
 * Created by wolfcode-lanxw
 */
@Service
public interface PayLogMapper {
    /**
     * 插入支付日志，用于幂等性控制
     * @param payLog
     * @return
     */
    int insert(PayLog payLog);
}
