package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户登录校验拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*已使用Redis代替
        //从请求中取出用户信息
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");*/

        //获取请求头中的token
        String token = request.getHeader("authorization");
        //请求无token，拦截
        if(StrUtil.isBlank(token)){
            response.setStatus(401);
            return false;
        }

        //Redis中获取token
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userDTOMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        //请求中无用户信息，拦截请求
        if(userDTOMap.isEmpty()){
            response.setStatus(401);
            return false;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userDTOMap, new UserDTO(), false);

        //请求带有用户信息：
        //保存用户信息到ThreadLocal
        UserHolder.saveUser(BeanUtil.copyProperties(userDTO, UserDTO.class));

        //更新token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
