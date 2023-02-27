package com.ice.learning.review_pro.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Shop;
import com.ice.learning.review_pro.mapper.ShopMapper;
import com.ice.learning.review_pro.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = "cache:shop:" + id;
        // 1. 先从redis查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在则直接返回
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        // 4. 不存在则查询数据库
        Shop shop = getById(id);
        // 5. 不存在返回错误
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        // 6. 存在则写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),30, TimeUnit.MINUTES);
        // 7. 返回数据
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id==null) {
            return Result.fail("商铺id不能为空");
        }
        // 1. 先更新数据库
        updateById(shop);
        // 2. 删除redis中的缓存
        stringRedisTemplate.delete("cache:shop:" + id);
        return Result.ok();
    }
}
