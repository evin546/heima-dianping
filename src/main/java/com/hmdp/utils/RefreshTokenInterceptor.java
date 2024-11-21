package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * token刷新拦截器
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //尝试从请求中获取token
        String token = request.getHeader("authorization");
        //token为空，直接放行
        if(StrUtil.isBlank(token)){
            return true;
        }

        //token不为空，通过redis校验token
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userDTOMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        //token非法或已过期，放行
        if(userDTOMap.isEmpty()){
            return true;
        }

        //token合法：
        //转化为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userDTOMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
