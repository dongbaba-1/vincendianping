package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

//        // 4.保存验证码到 session——》保存验证码到redis
//        session.setAttribute(SESSION_CODE,code);
        stringRedisTemplate.opsForValue()
                .set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        // 1.校验手机号
        String phone = loginForm.getPhone();

        String codeKey = LOGIN_CODE_KEY + phone;
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.从session中获取验证码校验——》从redis中获取验证码
        //Object cacheCode = session.getAttribute(SESSION_CODE);
        String redisCode = stringRedisTemplate.opsForValue().get(codeKey);
        String code = loginForm.getCode();//获取用户输入的验证码
        if(redisCode == null || !redisCode.equals(code)){
            if(redisCode == null)
                return Result.fail("验证码已失效，请重新获取");
            else
                //3.不一致，报错
                return Result.fail("验证码错误");
        }
        //一致，根据手机号查询用户,query()是mybatisplus提供的方法
        User user = query().eq("phone", phone).one();

        //5.判断用户是否存在
        if(user == null){
            //6.不存在，则创建
            user =  createUserWithPhone(phone);
        }
        //7.保存用户信息到session中——》使用hash结构保存到redis中
//        UserDTO userDTO = new UserDTO();
//        BeanUtils.copyProperties(user,userDTO);
//        session.setAttribute(SESSION_USER,userDTO);
        //随机生成UUID作为key
        String token = UUID.randomUUID().toString();
        String userToken = LOGIN_USER_KEY + token;
        //信息脱敏，不含密码
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        //将用户信息字段存进一个map中,使用工具将对象转成map,同时将userDTO的所有字段都设置为String类型
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .ignoreNullValue()
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        //用户信息存进redis
        stringRedisTemplate.opsForHash().putAll(userToken,userMap);

        //设置用户信息过期时间
        //要使用拦截器，只要拦截到，就证明用户在使用，就刷新token有效期
        stringRedisTemplate.expire(userToken,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }


}
