package com.hmdp.service.impl;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryShopById(Long id) {
        //查询时防止缓存穿透
        //Shop shop = queryWithPassthrough(id);

        //查询时防止缓存击穿与缓存穿透
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("商铺不存在");
        }

        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        //先查redis
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(shopKey);
        //判断shopKey是不是空
        if(StrUtil.isNotBlank(json)){
            //不为空，将redis获取的json字符串转换为Shop对象并返回
            Shop shop = JSONUtil.toBean(json, Shop.class);
            return shop;
        }
        //空字符串判断
        if("".equals(json)){
            return null;
        }
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop;

        try {
            //缓存为空则查询数据库，查询数据库之前先获取锁
            //若没获取，则休眠并重新获取
            if(!tryLock(lockKey)){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //获取到锁，再查询缓存，做double check
            json = stringRedisTemplate.opsForValue().get(shopKey);
            //判断shopKey是不是空
            if(StrUtil.isNotBlank(json)){
                //不为空，将redis获取的json字符串转换为Shop对象并返回
                shop = JSONUtil.toBean(json, Shop.class);
                return shop;
            }

            //double check后缓存依旧为空，再查数据库
            shop = getById(id);
            //将查询到的对象写进redis，设置过期时间TODO
            //商铺不存在
            if(shop == null){
                //redis写入空值防止缓存穿透
                stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue()
                    .set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            releaseLock(lockKey);
        }
        return shop;
    }


    public Shop queryWithPassthrough(Long id){
        //先查redis
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(shopKey);
        //判断shopKey是不是空
        if(StrUtil.isNotBlank(json)){
            //不为空，将redis获取的json字符串转换为Shop对象并返回
            Shop shop = JSONUtil.toBean(json, Shop.class);
            return shop;
        }
        if("".equals(json)){
            return null;
        }
        //缓存为空则查询数据库
        Shop shop = getById(id);
        if(shop == null){//商铺不存在
            //redis写入空值防止缓存穿透
            stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //将查询到的对象写进redis，设置过期时间TODO
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;

    }

    //利用redis setnx实现互斥锁
     private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(key,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void releaseLock(String key){
        stringRedisTemplate.delete(key);
    }


    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long shopId = shop.getId();
        if(shopId == null){
            return Result.fail("店铺id不能为空");
        }
        //先更新数据库,mybatisplus提供方法
        updateById(shop);
        //再删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shopId);
        return Result.ok();
    }
}
