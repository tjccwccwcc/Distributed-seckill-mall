package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;

/**
 * Created by lanxw
 */
public interface IUsableIntegralService {
    /**
     * 进行积分扣减
     * @param vo
     */
    void decrIntegral(OperateIntergralVo vo);

    /**
     * 进行积分增加
     * @param vo
     */
    void incrIntegral(OperateIntergralVo vo);
}
