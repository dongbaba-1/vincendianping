package com.hmdp.service.impl;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
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
        //先查redis
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(shopKey);
        //判断shopKey是不是空
        if(StrUtil.isNotBlank(json)){
            //不为空，将redis获取的json字符串转换为Shop对象并返回
            Shop shop = JSONUtil.toBean(json, Shop.class);
            return Result.ok(shop);
        }
        if("".equals(json)){
            return Result.fail("商铺不存在");
        }
        //缓存为空则查询数据库
        Shop shop = getById(id);
        if(shop == null){//商铺不存在
            //redis写入空值防止缓存穿透
            stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("商铺不存在");
        }
        //将查询到的对象写进redis，设置过期时间TODO
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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
