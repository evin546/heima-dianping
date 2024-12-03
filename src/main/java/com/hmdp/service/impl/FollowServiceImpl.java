package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

    @Override
    public Result followUser(Long targetUserId, Boolean isFollow) {
        Long currentUserId = UserHolder.getUser().getId();
        if(BooleanUtil.isTrue(isFollow)){
            //关注：
            Follow follow = new Follow();
            follow.setUserId(currentUserId);
            follow.setFollowUserId(targetUserId);
            save(follow);
        }
        else {
            //取关
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", currentUserId)
                    .eq("follow_user_id", targetUserId));
        }
        return Result.ok();
    }

    @Override
    public Result getIsFollow(Long targetUserId) {
        Long currentUserId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", currentUserId)
                .eq("follow_user_id", targetUserId)
                .count();

        return Result.ok(count > 0);
    }

}
