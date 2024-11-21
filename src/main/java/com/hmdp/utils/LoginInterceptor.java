package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 用户登录校验拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //从请求中取出用户信息
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");
        //请求中无用户信息，拦截请求
        if(user == null){
            response.setStatus(401);
            return false;
        }
        //请求带有用户信息：
        //保存用户信息到ThreadLocal
        UserHolder.saveUser(BeanUtil.copyProperties(user, UserDTO.class));
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
