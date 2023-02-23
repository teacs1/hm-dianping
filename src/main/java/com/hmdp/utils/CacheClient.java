package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 缓存工具封装
 *  1.将任意对象序列化为json字符串，存入redisstring类型中
 *  2.将任意对象序列化为json字符串，存入redisstring类型中，并可以设置逻辑过期时间
 *  3.根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
 *  4.根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 存入redis的String类型
     * @param key
     * @param value
     * @param ttl   过期时间
     * @param unit  时间类型
     */
    public void set(String key, Object value, Long ttl, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttl, unit);
    }

    /**
     * 存入redis的String类型,并设置逻辑过期
     * @param key
     * @param value
     * @param ttl   过期时间
     * @param unit  时间类型
     */
    public void setWithLogicalExpire(String key, Object value, Long ttl, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttl)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 从redis中取出string类型数据,并反序列化转回指定类型对象返回
     * 缓存穿透
     * @param keyPrefix     redis中key的前缀
     * @param id            数据库id
     * @param type          数据类型
     * @param dbFallBack    数据库查询函数
     * @param ttl           过期时间
     * @param unit          时间单元类型
     * @return              目标R类型对象
     * @param <R>           目标类型
     * @param <ID>          数据库ID类型
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long ttl, TimeUnit unit
    ) {
        // 1.尝试从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断redis中是否存在
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断在redis中命中的是不是空值
        if (json != null) {
            return null;
        }
        // 4. 不存在,根据id查询数据库
        R r = dbFallBack.apply(id);
        // 5.不存在,返回错误
        if (r == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 6.存在,写入redis, 超时剔除
        int i = RandomUtil.randomInt(1,7);  //随机时间,防止同时过期删除
        set(key, r, ttl + i, unit);
        // 7. 返回商铺信息
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 缓存击穿 逻辑过期

    /**
     * 从redis中查询逻辑过期数据,并反序列化转回指定类型对象返回
     * 缓存击穿
     * @param keyPrefix     redis中key的前缀
     * @param id            数据库id
     * @param type          数据类型
     * @param dbFallBack    数据库查询函数
     * @param ttl           过期时间
     * @param unit          时间单元类型
     * @return              目标R类型对象
     * @param <R>           目标类型
     * @param <ID>          数据库ID类型
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long ttl, TimeUnit unit
    ) {
        // 1.尝试从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断redis中是否存在
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. 命中,把json反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 判断是否逻辑过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.未过期,返回redis缓存信息
            return r;
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
                    // 查询数据库
                    R r1 = dbFallBack.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key, r1, ttl, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    uncLock(lockKey);
                }
            });
        }
        // 6.5. 返回过期商铺信息
        return r;
    }

    // 加锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);    //有可能为空
    }
    // 解锁
    private void uncLock(String key){
        stringRedisTemplate.delete(key);
    }

}
