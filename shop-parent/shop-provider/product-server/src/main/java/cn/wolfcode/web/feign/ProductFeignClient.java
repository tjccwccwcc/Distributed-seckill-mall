package cn.wolfcode.web.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.service.IProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductFeignClient {
    @Autowired
    private IProductService iProductService;
    @RequestMapping("/queryByIds")
    public Result<List<Product>> queryByIds(@RequestParam List<Long> productIds) {
//        System.out.println("远程调用productIds为：" + productIds + "___________________");
        return Result.success(iProductService.queryByIds(productIds));
    }
}
