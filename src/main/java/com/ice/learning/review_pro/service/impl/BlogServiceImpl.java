package com.ice.learning.review_pro.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Blog;
import com.ice.learning.review_pro.entity.User;
import com.ice.learning.review_pro.mapper.BlogMapper;
import com.ice.learning.review_pro.service.IBlogService;
import com.ice.learning.review_pro.utils.SystemConstants;
import com.ice.learning.review_pro.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;


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
        Long userId = UserHolder.getUser().getId();
        // 判断是否已经点赞
        String key = "blog:liked:" + blog.getId();
        Boolean isMember = template.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        // 判断是否已经点赞
        String key = "blog:liked:" + id;
        Boolean isMember = template.opsForSet().isMember(key, userId.toString());
        if (BooleanUtil.isFalse(isMember)){
            // 点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            template.opsForSet().add(key, userId.toString());
        }else{
            // 如果已经点赞，取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            template.opsForSet().remove(key, userId.toString());
        }
        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
