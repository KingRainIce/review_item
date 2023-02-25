package com.ice.learning.review_pro.service.impl;

import com.ice.learning.review_pro.entity.Blog;
import com.ice.learning.review_pro.mapper.BlogMapper;
import com.ice.learning.review_pro.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
