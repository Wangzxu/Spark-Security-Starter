package com.spark.security.controller;

import com.spark.security.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试控制器
 * 提供给前端测试联通性以及 Token 有效性
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestController {

    /**
     * 公共接口，不需要 Token 即可访问
     */
    @GetMapping("/public")
    public Result<String> publicPing() {
        log.info("前端调用了公共测试接口");
        return Result.success("Hello! 这是来自后端的公共信息，不需要 Token 即可访问。");
    }

    /**
     * 受保护接口，需要请求头中携带有效的 Access Token 才能访问
     */
    @GetMapping("/protected")
    public Result<String> protectedPing() {
        log.info("前端调用了受保护测试接口");
        return Result.success("Hello! 恭喜你，你的 Token 是有效的，这是受保护的信息。");
    }
}
