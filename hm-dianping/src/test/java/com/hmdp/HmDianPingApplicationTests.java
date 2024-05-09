package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Test
    public void testRedisListSize(){
        String typeListKey = "cache:typeList";
        Long size = stringRedisTemplate.opsForList().size(typeListKey);
        System.out.println("size="+ size);
    }


}
