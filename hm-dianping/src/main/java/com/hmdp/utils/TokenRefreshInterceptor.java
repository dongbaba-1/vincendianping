package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class TokenRefreshInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、从请求中获取session——》从请求头中获取authorization字段，拿到token
        //HttpSession session = request.getSession();
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){//用户未登录拦截——》放行

            return true;
        }
        //2、从session获取用户信息——》从redis中获取
        //Object user = session.getAttribute(SystemConstants.SESSION_USER);
        Map<Object, Object> user = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        //3、校验用户信息是否存在，不存在则拦截——》不存在直接放行，让登录校验拦截器处理
        if(user.isEmpty()) {
            return true;
        }

        //4、用户信息保存到ThreadLocal中方便后面的各个Controller获取，把map转化为原本的UserDTO对象类型
        UserDTO userDTO = BeanUtil.fillBeanWithMap(user, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        
        //刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //5、放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
