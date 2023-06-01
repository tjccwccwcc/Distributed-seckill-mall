package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.AccountTransaction;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.mapper.AccountTransactionMapper;
import cn.wolfcode.mapper.UsableIntegralMapper;
import cn.wolfcode.service.IUsableIntegralService;
import cn.wolfcode.web.msg.IntergralCodeMsg;
import com.alibaba.fastjson.JSON;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * Created by lanxw
 */
@Service
public class UsableIntegralServiceImpl implements IUsableIntegralService {
    @Autowired
    private UsableIntegralMapper usableIntegralMapper;
    @Autowired
    private AccountTransactionMapper accountTransactionMapper;

    @Override
    @Transactional
    public void decrIntegral(OperateIntergralVo vo) {
        int effectCount = usableIntegralMapper.decrIntergral(vo.getUserId(), vo.getValue());
        if (effectCount == 0){
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
    }

    @Override
    public void incrIntegral(OperateIntergralVo vo) {
        usableIntegralMapper.incrIntergral(vo.getUserId(), vo.getValue());
    }

    @Override
    @Transactional
    public void decrIntegralTry(OperateIntergralVo vo, BusinessActionContext context) {
        System.out.println("执行Try方法");
        //插入事务控制表
        AccountTransaction log = new AccountTransaction();
        log.setTxId(context.getXid());//全局事务ID
        log.setActionId(context.getBranchId());//分支事务ID
        Date now = new Date();
        log.setGmtCreated(now);
        log.setGmtModified(now);
        log.setUserId(vo.getUserId());
        log.setAmount(vo.getValue());
        accountTransactionMapper.insert(log);//log.setState()默认为STATE_TRY，故不用设置
        //执行业务逻辑-->减积分（预留积分）
        int effectCount = usableIntegralMapper.decrIntergral(vo.getUserId(), vo.getValue());
        if (effectCount == 0){
            throw new BusinessException(IntergralCodeMsg.INTERGRAL_NOT_ENOUGH);
        }
    }

    @Override
    @Transactional//可以不写因为只有一个操作
    public void decrIntegralCommit(BusinessActionContext context) {
        System.out.println("执行Commit方法");
        //查询事务记录表
        AccountTransaction accountTransaction =
                accountTransactionMapper.get(context.getXid(), context.getBranchId());
        if (accountTransaction != null){
            //如果不为空
            if (AccountTransaction.STATE_TRY == accountTransaction.getState()){
                //如果状态为TRY，执行COMMIT逻辑
                //更新日志状态 空操作
                int effectAccount = accountTransactionMapper.updateAccountTransactionState(
                        context.getXid(),
                        context.getBranchId(),
                        AccountTransaction.STATE_COMMIT,
                        AccountTransaction.STATE_TRY//从TRY状态变过来
                );//可以根据effectAccount状态抛出异常，但一般不会出现该问题，故不写
            }else if (AccountTransaction.STATE_COMMIT == accountTransaction.getState()){
                //如果状态为COMMIT，则不做事情
            }else {
                //如果状态为其它-->写MQ消息通知管理员
            }
        }else {
            //如果为空-->写MQ消息通知管理员（一般只有通过了try方法才能进入commit，故为空肯定有非业务逻辑异常）
        }
    }

    @Override
    @Transactional
    public void decrIntegralRollback(BusinessActionContext context) {
        System.out.println("执行Rollback方法");
        //查询事务记录表
        AccountTransaction accountTransaction =
                accountTransactionMapper.get(context.getXid(), context.getBranchId());
        if (accountTransaction != null){
            //存在日志记录
            if (AccountTransaction.STATE_TRY == accountTransaction.getState()){
                //处于TRY状态
                //将状态修改成Cancel状态
                accountTransactionMapper.updateAccountTransactionState(
                        context.getXid(),
                        context.getBranchId(),
                        AccountTransaction.STATE_CANCEL,
                        AccountTransaction.STATE_TRY
                );
                //执行Cancel业务逻辑，添加积分
                usableIntegralMapper.incrIntergral(
                        accountTransaction.getUserId(),
                        accountTransaction.getAmount()
                );
            } else if (AccountTransaction.STATE_CANCEL == accountTransaction.getState()){
                //之前已经执行Cancel操作，幂等处理
            } else {
                //如果状态为其它-->写MQ消息通知管理员
            }
        }else {
            //不存在日志记录
            String str = (String) context.getActionContext("vo");
            System.out.println("存储在上下文中的对象" + str);
            OperateIntergralVo vo = JSON.parseObject(str, OperateIntergralVo.class);
            //插入事务控制表
            AccountTransaction log = new AccountTransaction();
            log.setTxId(context.getXid());//全局事务ID
            log.setActionId(context.getBranchId());//分支事务ID
            Date now = new Date();
            log.setGmtCreated(now);
            log.setGmtModified(now);
            log.setUserId(vo.getUserId());
            log.setAmount(vo.getValue());
            log.setState(AccountTransaction.STATE_CANCEL);
            accountTransactionMapper.insert(log);//log.setState()默认为STATE_TRY，故不用设置
        }
    }

}
