package com.ice.learning.review_pro.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ice.learning.review_pro.DTO.Result;
import com.ice.learning.review_pro.entity.Shop;
import com.ice.learning.review_pro.mapper.ShopMapper;
import com.ice.learning.review_pro.service.IShopService;
import com.ice.learning.review_pro.utils.CacheClient;
import com.ice.learning.review_pro.utils.RedisConstants;
import com.ice.learning.review_pro.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ice.learning.review_pro.utils.RedisConstants.SHOP_GEO_KEY;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * @Auth: Ice
     * @Date: 2023/2/27 21:00
     * @Desc: 逻辑删除解决缓存击穿
     */
    @Override
    public Result queryById(Long id) {
        // 解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 5L, TimeUnit.MINUTES);

        // 解决缓存击穿
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 5L, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // Determine whether you need to query based on coordinate
        if (x == null || y == null) {
            // Query by type
            Page<Shop> page = query().eq("type_id", typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // Calculate pagination parameters
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // Query redis, sort by distance, pagination
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        // Resolve the ID
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (results == null || content.size() == 0) {
            return Result.ok(Collections.emptyList());
        }
        // There is no next page, over
        if (content.size() <= start) {
            return Result.ok(Collections.emptyList());
        }

        ArrayList<Object> ids = new ArrayList<>(content.size());
        HashMap<String, Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(start).forEach(result -> {
            // Get the ShopID
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // Get the distance
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // Search for stores by ID
        String join = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids)
                .last("order by field(id," + join + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }

}
