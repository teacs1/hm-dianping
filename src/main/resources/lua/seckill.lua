-- 参数列表
-- 1.1.优惠卷id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]

-- 2.数据key
-- 2.1.库存key .. 为拼接符号
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1.判断库存是否大于0
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足,返回1
    return 1
end
-- 3.2. 判断用户是否下单
if(redis.call('sismember', orderKey, userId) == 1) then
    --已经下过单了,返回2
    return 2
end
-- 3.3. 扣库存 incrby stockKey -1(库存加上-1)
redis.call('incrby', stockKey, -1)
-- 3.4. 下单 sadd orderKey userId(保存用户id到当前优惠卷set集合)
redis.call('sadd', orderKey, userId)

return 0