package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.constant.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result followUser(Long targetUserId, Boolean isFollow) {
        Long currentUserId = UserHolder.getUser().getId();
        if(BooleanUtil.isTrue(isFollow)){
            //关注：
            Follow follow = new Follow();
            follow.setUserId(currentUserId);
            follow.setFollowUserId(targetUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOWERS_KEY_PREFIX + currentUserId, targetUserId.toString());
            }

        }
        else {
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", currentUserId)
                    .eq("follow_user_id", targetUserId));

            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOWERS_KEY_PREFIX + currentUserId, targetUserId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result getIsFollow(Long targetUserId) {
        Long currentUserId = UserHolder.getUser().getId();
        /*Integer count = query().eq("user_id", currentUserId)
                .eq("follow_user_id", targetUserId)
                .count();*/
        Boolean isFollow = stringRedisTemplate.opsForSet().isMember(RedisConstants.FOLLOWERS_KEY_PREFIX + currentUserId, targetUserId.toString());

        return Result.ok(BooleanUtil.isTrue(isFollow));
    }

    @Override
    public Result getCommonFollowers(Long targetUserId) {
        Long currentUserId = UserHolder.getUser().getId();
        Set<String> commonFollowers = stringRedisTemplate.opsForSet().intersect(
                RedisConstants.FOLLOWERS_KEY_PREFIX + currentUserId,
                RedisConstants.FOLLOWERS_KEY_PREFIX + targetUserId
        );
        if(commonFollowers == null || commonFollowers.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }

        List<Long> commonFollowerIds = commonFollowers.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOs = userService.listByIds(commonFollowerIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOs);
    }

}
