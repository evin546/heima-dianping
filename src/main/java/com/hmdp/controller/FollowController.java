package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result followUser(@PathVariable("id") Long targetUserId, @PathVariable("isFollow") Boolean isFollow){
        return followService.followUser(targetUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result getIsFollow(@PathVariable("id") Long targetUserId){
        return followService.getIsFollow(targetUserId);
    }

    /**
     * 获取当前登录用户与target用户共同关注的用户
     * @return
     */
    @GetMapping("/common/{id}")
    public Result getCommonFollowers(@PathVariable("id") Long targetUserId){
        return followService.getCommonFollowers(targetUserId);
    }


}
