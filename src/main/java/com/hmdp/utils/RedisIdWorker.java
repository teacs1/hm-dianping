package com.hmdp.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RedisIdWorker {

    public long nextId(String keyPrefix){
        // 1.生成时间戳

        // 2.生成序列号

        // 3.拼接并返回
        return 0l;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
        System.out.println("time = " + time);
    }
}
