package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //首先查redis中的数据
        String typeListKey = "cache:typeList";
        Long size = stringRedisTemplate.opsForList().size(typeListKey);
        if (size > 0) {
            //说明缓存里有数据
            List<String> shopTypes = stringRedisTemplate.opsForList().range(typeListKey, 0, size - 1);
            return Result.ok(shopTypes);
        }
        //缓存中没有数据，就去数据库里查
        List<ShopType> shopList = query().orderByAsc("sort").list();
        //查到后数据放入缓存
        List<String> stringShopList = new ArrayList<>(shopList.size());
        for (int i = 0; i < shopList.size(); i++) {
            stringShopList.add(i,shopList.get(i).toString());
        }
        stringRedisTemplate.opsForList().leftPushAll(typeListKey,stringShopList);

        return Result.ok(shopList);
    }
}
