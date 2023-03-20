package com.ice.learning.review_pro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Follow;
import com.ice.learning.review_pro.mapper.FollowMapper;
import com.ice.learning.review_pro.service.IFollowService;
import com.ice.learning.review_pro.utils.UserHolder;
import org.springframework.stereotype.Service;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            // 1.关注则新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            save(follow);
        } else {
            // 2.取关则删除数据
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        // 查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }
}
