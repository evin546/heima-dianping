package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.constant.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.constant.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号格式是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式非法");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        /*已使用Redis代替
        //验证码保存到session
        session.setAttribute("code", code);*/

        //验证码保存到redis当中，并设置过期时间为5分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //模拟发送验证码
        log.info("{}登录验证码：{}", LocalDateTime.now(), code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String inputCode = loginForm.getCode();
        String phone = loginForm.getPhone();

        /*已使用Redis代替
        //校验验证码
        String codeInSession = (String) session.getAttribute("code");
        if(codeInSession == null ||!inputCode.equals(codeInSession)){
            return Result.fail("验证码错误！");
        }*/

        //验证码校验
        String codeInRedis = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if(codeInRedis == null || !inputCode.equals(codeInRedis)){
            return Result.fail("验证码错误");
        }
        //判断用户是否已注册
        User user = query().eq("phone", phone).one();
        //未注册则创建新用户
        if(user == null){
            user = createNewUser(phone);
        }
        /*已使用Redis代替
        //保存用户到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/

        //生成Token
        String token = UUID.randomUUID().toString(true);
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //UserDTO转焕为Map
        Map<String, String> userDTOMap = new HashMap<>();
        userDTOMap.put("id", userDTO.getId().toString());
        userDTOMap.put("nickName", userDTO.getNickName());
        userDTOMap.put("icon", userDTO.getIcon());
        //用户信息保存到redis
        stringRedisTemplate.opsForHash().putAll(tokenKey, userDTOMap);
        //设置token有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //用户信息token回传
        return Result.ok(token);
    }

    private User createNewUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public Result signIn() {
        //获取当前登录用户
        Long currentUserId = UserHolder.getUser().getId();
        //获取当天日期
        LocalDateTime now = LocalDateTime.now();

        String key = RedisConstants.USER_SIGN_KEY + currentUserId + now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //获取当天是当月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //第n天的签到数据存在 n - 1索引上
        stringRedisTemplate.opsForValue().setBit(key, (long)dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前用户
        Long currentUserId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        //获取当前是本月第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取签到数据
        List<Long> signInDataList = stringRedisTemplate.opsForValue().bitField(
                RedisConstants.USER_SIGN_KEY + currentUserId + now.format(DateTimeFormatter.ofPattern(":yyyyMM")),
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(signInDataList == null || signInDataList.isEmpty()){
            return Result.ok(0);
        }
        Long signInData = signInDataList.get(0);
        if(signInData == null || signInData == 0){
            return Result.ok(0);
        }
        //判断连续签到天数
        int count = 0;
        while(true){
            if((signInData & 1) == 0){
                break;
            } else {
                count++;
                //signInData = signInData >>> 1
                signInData >>>= 1;
            }
        }
        return Result.ok(count);
    }
}
