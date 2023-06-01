package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.OperateIntergralVo;
import cn.wolfcode.service.IUsableIntegralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by lanxw
 */
@RestController
@RequestMapping("/integral")
public class IntegralController {
    @Autowired
    private IUsableIntegralService usableIntegralService;
    @RequestMapping("/decrIntegral")
    public Result decrIntegral(@RequestBody OperateIntergralVo vo) {
//        usableIntegralService.decrIntegral(vo);
        usableIntegralService.decrIntegralTry(vo, null);//AOT会自动注入，因此设为null
        return Result.success();
    }
    @RequestMapping("/incrIntegral")
    public Result incrIntegral(@RequestBody OperateIntergralVo vo) {
        usableIntegralService.incrIntegral(vo);
        return Result.success();
    }
}
