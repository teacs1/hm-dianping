package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for(int i = 0; i < 100; i++){
                long id = redisIdWorker.nextId("order");
                System.out.println(" id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for(int i = 0; i < 300; i++){
            es.execute(task);    //将任务执行300次
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("执行时间:" + (end - begin));
    }

    @Test
    void test() throws InterruptedException {
        Shop shop = shopService.getById(1L);

        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1l, shop, 10l, TimeUnit.SECONDS);
    }

    @Test
    void testAddRedisToken(){
        String filePath = "D:\\学习资源\\IT资源\\JAVA_黑马程序员\\Redis\\02-实战篇\\资料\\tokens.txt";
        int tokenNum = 1000;
        try {
            StringBuilder sb = new StringBuilder();
            FileWriter fout = new FileWriter(filePath);
            BufferedWriter bufferedWriter = new BufferedWriter(fout);
            for(int i = 0; i < tokenNum; i++){
                UserDTO userDTO = new UserDTO();
                userDTO.setIcon("");
                userDTO.setId(i + 1000l);
                userDTO.setNickName("user" + (i + 1000));
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create()
                                .setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
                String token = UUID.randomUUID().toString(true);
                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                stringRedisTemplate.expire(tokenKey, 3l, TimeUnit.MINUTES);
                bufferedWriter.write(token + "\n");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String token = UUID.randomUUID().toString(true);
    }
}
