package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Autowired
    private RedisIdWorker redisIdWorker;

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
            es.submit(task);    //将任务执行300次
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
    void testString(){
        String str = "qwertyuiopq";
        System.out.println("str = " + str.indexOf("q"));
        System.out.println("str = " + str.charAt(2));
        System.out.println("str = " + str.replace("qwe", "asd"));
        System.out.println("str = " + str.toUpperCase());
    }
}
