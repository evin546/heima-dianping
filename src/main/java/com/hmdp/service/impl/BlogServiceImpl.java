package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.constant.RedisConstants;
import com.hmdp.utils.constant.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import java.util.ArrayList;
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
    private IFollowService followService;

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

        /* bug：使用listByIds会破坏原来在SortedSet中的顺序：数据库查询结果未必按传入的ID顺序排列
        List<UserDTO> top5UserDTOs = userService.listByIds(userIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());*/
        String idStr = StrUtil.join(",", userIds);
        //通过 ORDER BY FIELD 来确保查询结果的顺序与传入的 ID 列表一致
        List<UserDTO> top5UserDTOs = userService.query()
                .in("id", userIds)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
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


    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        Long currentUserId = UserHolder.getUser().getId();
        blog.setUserId(currentUserId);
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("发布笔记失败！");
        }

        //推送博文信息给粉丝:
        //获取推送时间
        long blogPublishTime = System.currentTimeMillis();
        List<Follow> followInfo = followService.query()
                .eq("follow_user_id", currentUserId)
                .list();

        if(followInfo == null || followInfo.isEmpty()){
            return Result.ok(blog.getId());
        }

        for(Follow follow: followInfo){
            //获取关注者UserId
            Long followerUserId = follow.getUserId();
            //推送信息到“收件箱”
            stringRedisTemplate.opsForZSet().add(
                    RedisConstants.FEED_KEY + followerUserId,
                    blog.getId().toString(),
                    /*每循环一次重新获取时间，造成同一篇笔记的score不一致，不严谨，应统一
                    System.currentTimeMillis()*/
                    blogPublishTime
            );

        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long lastMinTimeStamp, Long offset) {
        //获取当前用户ID
        Long currentUserId = UserHolder.getUser().getId();
        //查询收件箱
        //这里需求是按时间戳降序排列，要使用reverse调转顺序:
        //ZREVRANGEBYSCORE key Max Min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> blogIdTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                RedisConstants.FEED_KEY + currentUserId,
                0, lastMinTimeStamp,
                offset,
                2
        );
        if(blogIdTuples == null || blogIdTuples.isEmpty()){
            return Result.ok();
        }

        //存储查询到的blogId
        List<Long> blogIds = new ArrayList<>(blogIdTuples.size());
        //遍历查询结果，获取blogId，当前最小时间戳，并计算下次查询的偏移量
        long minTimestamp = System.currentTimeMillis();
        int newOffset = 0;
        for(ZSetOperations.TypedTuple<String> tuple : blogIdTuples){
            //获取博客id
            blogIds.add(Long.valueOf(tuple.getValue()));
            //获取最小时间戳
            long tupleTimestamp = tuple.getScore().longValue();
            minTimestamp = 0;
            if(tupleTimestamp == minTimestamp){
                newOffset++;
            } else {
                minTimestamp = tupleTimestamp;
                newOffset = 1;
            }
        }

        //根据id查Blog信息
        String ids = StrUtil.join(",", blogIds);
        List<Blog> blogs = query().in("id", blogIds)
                .last("ORDER BY FIELD (id, " + ids + ")")
                .list();

        //填充博客发布用户，点赞状态等信息
        for(Blog blog: blogs){
            setUserInfo(blog);
            blog.setIsLike(getCurrentUserLikedStatus(blog.getId()));
        }

        //组装返回数据
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setMinTime(minTimestamp);
        result.setOffset(newOffset);

        return Result.ok(result);
    }


}
