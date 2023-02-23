package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = cacheClient
        //        .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 缓存击穿 互斥锁解决
        //Shop shop = queryWithMutex(id);
        // 缓存击穿 逻辑过期
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20l, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        // 返回
        return Result.ok(shop);
    }
/*    // 缓存穿透
    public Shop queryWithPassThrough(Long id) {
        // 1.尝试从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断redis中是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // StrUtil.isNotBlank(null) false
            // StrUtil.isNotBlank("")  false
            // StrUtil.isNotBlank(" \t\n") false
            // StrUtil.isNotBlank("abc")  true
            // 3.存在返回商铺信息
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断在redis中命中的是不是空值
        if (shopJson != null) {
            return null;
        }
        // 4. 不存在,根据id查询数据库
        Shop shop = getById(id);
        // 5.不存在,返回错误
        if (shop == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 6.存在,写入redis, 超时剔除
        int i = RandomUtil.randomInt(1,7);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + i, TimeUnit.MINUTES );
        // 7. 返回商铺信息
        return shop;
    }*/

    /*// 缓存击穿 互斥锁
    public Shop queryWithMutex(Long id) {
        // 1.尝试从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断redis中是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断在redis中命中的是不是空值 //缓存穿透在redis存的空值 ""
        if (shopJson != null) {
            return null;
        }
        // 4. 互斥锁实现缓存重建
        // 4.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id; // lock:shop:
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2 判断是否获取成功
            if (!isLock) {
                // 4.3 获取失败,休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 模拟重建的延迟
            Thread.sleep(200);
            // 4.4 成功,根据id查询数据库
            shop = getById(id);
            // 5.不存在,返回错误
            if (shop == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在,写入redis, 超时剔除
            int i = RandomUtil.randomInt(1,7);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + i, TimeUnit.MINUTES );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            uncLock(lockKey);
        }
        // 7. 返回商铺信息
        return shop;
    }*/

    /*private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 缓存击穿 逻辑过期
    public Shop queryWithLogicalExpire(Long id) {
        // 1.尝试从redis中查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断redis中是否存在
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        // 3. 命中,把json反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.未过期,返回商铺信息
            return shop;
        }
        // 6.过期,需要重建缓存
        // 6.1尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        // 6.2.判断是否获取到锁
        if (tryLock(lockKey)) {
            // 6.3.获取到锁
            // 6.4.开启独立线程,重建缓存
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    saveShop2Redis(id, 20l);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    uncLock(lockKey);
                }
            });
        }
        // 6.5. 返回过期商铺信息
        return shop;
    }*/

    /*// 加锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);    //有可能为空
    }
    // 解锁
    private void uncLock(String key){
        stringRedisTemplate.delete(key);
    }*/

/*    // 封装过期时间
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
