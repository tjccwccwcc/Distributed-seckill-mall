package cn.wolfcode.common.exception;

import cn.wolfcode.common.web.CodeMsg;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by wolfcode-lanxw
 */
@Setter
@Getter
public class BusinessException extends RuntimeException {
    //状态码，可以知道那个服务失效，比如返回错误信息中状态码为-1，则代表登录信息过期
    private CodeMsg codeMsg;
    public BusinessException(CodeMsg codeMsg){
        this.codeMsg = codeMsg;
    }
}
