package cn.wolfcode.service;

import cn.wolfcode.domain.OperateIntergralVo;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * Created by lanxw
 */
@LocalTCC
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

    /**
     * TCC的try方法
     * @param vo
     */
    @TwoPhaseBusinessAction(name = "decrIntegralTry", commitMethod = "decrIntegralCommit", rollbackMethod = "decrIntegralRollback")
    void decrIntegralTry(
            @BusinessActionContextParameter(paramName = "vo") OperateIntergralVo vo,
            BusinessActionContext context//包含全局事务XID，和分支事务ID
    );
    void decrIntegralCommit(BusinessActionContext context);
    void decrIntegralRollback(BusinessActionContext context);
}
