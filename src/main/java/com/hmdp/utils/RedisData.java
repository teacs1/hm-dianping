package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // 设置过期时间
    private LocalDateTime expireTime;
    // 需要设置逻辑过期的数据
    private Object data;
}
