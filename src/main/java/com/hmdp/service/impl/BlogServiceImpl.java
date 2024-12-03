package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.constant.RedisConstants;
import com.hmdp.utils.constant.SystemConstants;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryBlogById(Long blogId) {
        //查询博客信息
        Blog blog = getById(blogId);
        //附加用户信息
        setUserInfo(blog);
        blog.setIsLike(getCurrentUserLikedStatus(blog.getId()));
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            setUserInfo(blog);
            blog.setIsLike(getCurrentUserLikedStatus(blog.getId()));
        });
        return Result.ok(records);
    }

    private void setUserInfo(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long blogId) {
        //判断当前用户是否已点过赞
        Long userId = UserHolder.getUser().getId();
        Boolean isLike = getCurrentUserLikedStatus(blogId);
        if (BooleanUtil.isTrue(isLike)){
            //已点过赞：
            //取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1")
                    .eq("id", blogId)
                    .update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + blogId, userId.toString());
            }
        }
        else {
            //未点赞：
            //点赞
            boolean isSuccess = update().setSql("liked = liked + 1")
                    .eq("id", blogId)
                    .update();
            if (isSuccess) {
                //score设置为时间戳其实有问题（同一毫秒点赞无法区分先后）
                stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + blogId, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result getTop5LikedUsers(Long blogId) {
        //获取前5个点赞的用户id
        Set<String> userIdsS = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + blogId, 0, 4);
        //获取不到，返回空集合
        if(userIdsS == null || userIdsS.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }
        List<Long> userIds = userIdsS.stream().map(Long::valueOf).collect(Collectors.toList());

        List<UserDTO> top5UserDTOs = userService.listByIds(userIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(top5UserDTOs);
    }

    /**
     * 获取当前登录用户对某博文的点赞状态
     * @param blogId
     * @return
     */
    private Boolean getCurrentUserLikedStatus(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blogId, userId.toString());
        return score != null;
    }


}
