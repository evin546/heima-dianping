package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号格式是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式非法");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //验证码保存到session
        session.setAttribute("code", code);
        //模拟发送验证码
        log.info("{}登录验证码：{}", LocalDateTime.now(), code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String inputCode = loginForm.getCode();
        //校验验证码
        String codeInSession = (String) session.getAttribute("code");
        if(codeInSession == null ||!inputCode.equals(codeInSession)){
            return Result.fail("验证码错误！");
        }

        String phone = loginForm.getPhone();

        User user = query().eq("phone", phone).one();
        //未注册则创建新用户
        if(user == null){
            user = createNewUser(phone);
        }
        //保存用户到session
        session.setAttribute("user", user);
        return Result.ok();
    }

    private User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }


}
