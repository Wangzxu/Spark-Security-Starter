package com.spark.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;
    
    private String password;
    
    private String nickname;
    
    private Integer status; // 1: 正常, 0: 禁用
    
    private Long pv; // 密码版本号
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
}