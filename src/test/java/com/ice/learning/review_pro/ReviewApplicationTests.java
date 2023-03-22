package com.ice.learning.review_pro;

import com.ice.learning.review_pro.entity.Shop;
import com.ice.learning.review_pro.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class ReviewApplicationTests {

    @Resource
    StringRedisTemplate template;

    @Resource
    IShopService shopService;

    @Test
    void loadShopData() {
        // Find store information
        List<Shop> list = shopService.list();
        // Group stores according to type ID, and put type ID into a collection
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // Write redis in batches
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // Get the type ID
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            // Get a collection of stores of the same type
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList(value.size());
            // Write redis
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }
            template.opsForGeo().add(key, locations);
        }

    }


}
