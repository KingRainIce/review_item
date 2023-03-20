package com.ice.learning.review_pro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.DTO.UserDTO;
import com.ice.learning.review_pro.entity.Blog;
import com.ice.learning.review_pro.entity.User;
import com.ice.learning.review_pro.mapper.BlogMapper;
import com.ice.learning.review_pro.service.IBlogService;
import com.ice.learning.review_pro.utils.SystemConstants;
import com.ice.learning.review_pro.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ice.learning.review_pro.utils.RedisConstants.BLOG_LIKED_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private UserServiceImpl userService;

    @Resource
    private StringRedisTemplate template;

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
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        // 查询博文
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博文不存在");
        }
        // 查询用户
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        if (UserHolder.getUser() == null) {
            return;
        }
        Long userId = UserHolder.getUser().getId();
        // 判断是否已经点赞
        String key = "blog:liked:" + blog.getId();
        Double score = template.opsForZSet().score(key, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(score != null));
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 判断是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = template.opsForZSet().score(key, userId.toString());
        if (score == null){
            // 点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess){
                template.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }else{
            // 如果已经点赞，取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess){
                template.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY+ id;
        // 获取前5名
        Set<String> top5 = template.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //解析用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        //返回查询用户
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("order by field(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //返回
        return Result.ok(userDTOS);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
