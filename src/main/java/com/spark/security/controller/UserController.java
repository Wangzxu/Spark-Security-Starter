package com.spark.security.controller;

import com.spark.security.common.Result;
import com.spark.security.dto.ChangePasswordRequest;
import com.spark.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户相关操作接口
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 修改密码接口
     * @param request 修改密码请求信息
     * @return 修改结果
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        log.info("收到用户修改密码请求");
        try {
            userService.changePassword(request);
            return Result.success();
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }
}
