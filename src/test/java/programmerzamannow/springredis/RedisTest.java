package programmerzamannow.springredis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.*;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.support.collections.DefaultRedisMap;
import org.springframework.data.redis.support.collections.RedisList;
import org.springframework.data.redis.support.collections.RedisSet;
import org.springframework.data.redis.support.collections.RedisZSet;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RedisTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void redisTemplate() {
        assertNotNull(redisTemplate);
    }

    @Test
    void string() throws InterruptedException {
        ValueOperations<String, String> operations = redisTemplate.opsForValue();

        operations.set("name", "Aidil", Duration.ofSeconds(2));
        assertEquals("Aidil", operations.get("name"));

        Thread.sleep(Duration.ofSeconds(3).toMillis());
        assertNull(operations.get("name"));
    }

    @Test
    void list() {
        ListOperations<String, String> operations = redisTemplate.opsForList();

        operations.rightPush("names", "Aidil");
        operations.rightPush("names", "Adam");
        operations.rightPush("names", "Baik Hati");

        assertEquals("Aidil", operations.leftPop("names"));
        assertEquals("Adam", operations.leftPop("names"));
        assertEquals("Baik Hati", operations.leftPop("names"));
    }

    @Test
    void set() {
        SetOperations<String, String> operations = redisTemplate.opsForSet();

        operations.add("students", "Aidil");
        operations.add("students", "Aidil");
        operations.add("students", "Adam");
        operations.add("students", "Adam");
        operations.add("students", "Baik Hati");
        operations.add("students", "Baik Hati");

        Set<String> students = operations.members("students");
        assertEquals(3, students.size());
        assertThat(students, hasItems("Aidil", "Adam", "Baik Hati"));
    }

    @Test
    void zSet() {
        ZSetOperations<String, String> operations = redisTemplate.opsForZSet();

        operations.add("score", "Aidil", 100);
        operations.add("score", "Budi", 85);
        operations.add("score", "Joko", 90);

        assertEquals("Aidil", operations.popMax("score").getValue());
        assertEquals("Joko", operations.popMax("score").getValue());
        assertEquals("Budi", operations.popMax("score").getValue());
    }

    @Test
    void hash() {
        HashOperations<String, Object, Object> operations = redisTemplate.opsForHash();

//    operations.put("user:1", "id", "1");
//    operations.put("user:1", "name", "Eko");
//    operations.put("user:1", "email", "eko@example.com");

        Map<Object, Object> map = new HashMap<>();
        map.put("id", "1");
        map.put("name", "Aidil");
        map.put("email", "aidil@example.com");

        operations.putAll("user:1", map);

        assertEquals("1", operations.get("user:1", "id"));
        assertEquals("Aidil", operations.get("user:1", "name"));
        assertEquals("aidil@example.com", operations.get("user:1", "email"));

        redisTemplate.delete("user:1");
    }

    @Test
    void geo() {
        GeoOperations<String, String> operations = redisTemplate.opsForGeo();

        operations.add("sellers", new Point(106.822695, -6.177456), "Toko A");
        operations.add("sellers", new Point(106.821016, -6.174598), "Toko B");

        Distance distance = operations.distance("sellers", "Toko A", "Toko B", Metrics.KILOMETERS);
        assertEquals(0.3682, distance.getValue());

        GeoResults<RedisGeoCommands.GeoLocation<String>> sellers =
                operations.search("sellers", new Circle(
                        new Point(106.821922, -6.175491),
                        new Distance(5, Metrics.KILOMETERS)
                ));

        assertEquals(2, sellers.getContent().size());
        assertEquals("Toko A", sellers.getContent().get(0).getContent().getName());
        assertEquals("Toko B", sellers.getContent().get(1).getContent().getName());
    }
    @Test
    void hyperLogLog() {
        HyperLogLogOperations<String, String> operations = redisTemplate.opsForHyperLogLog();

        operations.add("traffics", "aidil", "adam", "BaikHati");
        operations.add("traffics", "aidil", "budi", "joko");
        operations.add("traffics", "budi", "joko", "rully");

        assertEquals(6L, operations.size("traffics"));
    }

    @Test
    void transaction() {
        redisTemplate.execute(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();

                operations.opsForValue().set("test1", "Aidil", Duration.ofSeconds(2));
                operations.opsForValue().set("test2", "Budi", Duration.ofSeconds(2));

                operations.exec();
                return null;
            }
        });
        assertEquals("Aidil", redisTemplate.opsForValue().get("test1"));
        assertEquals("Budi", redisTemplate.opsForValue().get("test2"));
    }

    @Test
    void pipeline() {
        List<Object> statuses = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.opsForValue().set("test1", "Aidil", Duration.ofSeconds(2));
                operations.opsForValue().set("test2", "Aidil", Duration.ofSeconds(2));
                operations.opsForValue().set("test3", "Aidil", Duration.ofSeconds(2));
                operations.opsForValue().set("test4", "Aidil", Duration.ofSeconds(2));
                return null;
            }
        });

        assertThat(statuses, hasSize(4));
        assertThat(statuses, hasItem(true));
        assertThat(statuses, not(hasItem(false)));
    }

    @Test
    void publishStream() {
        StreamOperations<String, Object, Object> operations = redisTemplate.opsForStream();

        MapRecord<String, String, String> record = MapRecord.create("stream-1", Map.of(
                "name", "Budi Adam",
                "address", "Indonesia"
        ));

        for (int i = 0; i < 10; i++) {
            operations.add(record);
        }
    }

    @Test
    void subscribeStream() {
        StreamOperations<String, Object, Object> operations = redisTemplate.opsForStream();

        try {
            operations.createGroup("stream-1", "sample-group");
        } catch (RedisSystemException exception) {
            // group sudah ada
        }

        List<MapRecord<String, Object, Object>> records = operations.read(Consumer.from("sample-group", "sample-1"),
                StreamOffset.create("stream-1", ReadOffset.lastConsumed()));

        for (MapRecord<String, Object, Object> record : records) {
            System.out.println(record);
        }
    }

    @Test
    void pubSub() {
        redisTemplate.getConnectionFactory().getConnection().subscribe(new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String event = new String(message.getBody());
                System.out.println("Receive message : " + event);
            }
        }, "my-channel".getBytes());

        for (int i = 0; i < 10; i++) {
            redisTemplate.convertAndSend("my-channel", "Hello World : " + i);
        }
    }

    @Test
    void redisList() {
        List<String> list = RedisList.create("names", redisTemplate);
        list.add("Aidil");
        list.add("Adam");
        list.add("Baik Hati");
        assertThat(list, hasItems("Aidil", "Adam", "Baik Hati"));

        List<String> result = redisTemplate.opsForList().range("names", 0, -1);
        assertThat(result, hasItems("Aidil", "Adam", "Baik Hati"));
    }

    @Test
    void redisSet() {
        Set<String> set = RedisSet.create("traffic", redisTemplate);
        set.addAll(Set.of("aidil", "adam", "baik hati"));
        set.addAll(Set.of("aidil", "budi", "rully"));
        set.addAll(Set.of("joko", "budi", "rully"));
        assertThat(set, hasItems("aidil", "adam", "budi", "rully", "joko"));

        Set<String> members = redisTemplate.opsForSet().members("traffic");
        assertThat(members, hasItems("aidil", "adam", "budi", "rully", "joko"));
    }

    @Test
    void redisZSet() {
        RedisZSet<String> set = RedisZSet.create("winner", redisTemplate);
        set.add("Aidil", 100);
        set.add("Budi", 85);
        set.add("Joko", 90);
        assertThat(set, hasItems("Aidil", "Budi", "Joko"));

        Set<String> winner = redisTemplate.opsForZSet().range("winner", 0, -1);
        assertThat(winner, hasItems("Aidil", "Budi", "Joko"));

        assertEquals("Aidil", set.popLast());
        assertEquals("Joko", set.popLast());
        assertEquals("Budi", set.popLast());
    }

    @Test
    void redisMap() {
        Map<String, String> map = new DefaultRedisMap<>("user:1", redisTemplate);
        map.put("name", "Aidil");
        map.put("address", "Indonesia");
        assertThat(map, hasEntry("name", "Aidil"));
        assertThat(map, hasEntry("address", "Indonesia"));

        Map<Object, Object> entries = redisTemplate.opsForHash().entries("user:1");
        assertThat(entries, hasEntry("name", "Aidil"));
        assertThat(entries, hasEntry("address", "Indonesia"));
    }

    @Test
    void repository() {
        Product product = Product.builder()
                .id("1")
                .name("Mie Ayam Goreng")
                .price(20_000L)
                .build();
        productRepository.save(product);

        Product product2 = productRepository.findById("1").get();
        assertEquals(product, product2);

        // kalau mau ngambil secara manual
        Map<Object, Object> map = redisTemplate.opsForHash().entries("products:1");
        assertEquals(product.getId(), map.get("id"));
        assertEquals(product.getName(), map.get("name"));
        assertEquals(product.getPrice().toString(), map.get("price"));
    }

    @Test
    void ttl() throws InterruptedException {
        Product product = Product.builder()
                .id("1")
                .name("Mie Ayam Goreng")
                .price(20_000L)
                .ttl(3L)
                .build();
        productRepository.save(product);

        assertTrue(productRepository.findById("1").isPresent());

        Thread.sleep(Duration.ofSeconds(5).toMillis());
        assertFalse(productRepository.findById("1").isPresent());
    }

    @Test
    void cache() {
        Cache cache = cacheManager.getCache("scores");
        cache.put("Aidil", 100);
        cache.put("Budi", 95);

        // kalau mau ambil datanya
        assertEquals(100, cache.get("Aidil", Integer.class));
        assertEquals(95, cache.get("Budi", Integer.class));

        // kalau mau hapus
        cache.evict("Aidil");
        cache.evict("Budi");

        assertNull(cache.get("Aidil"));
        assertNull(cache.get("Budi"));
    }

    @Autowired
    private ProductService productService;

    @Test
    void cacheable() {
        Product product = productService.getProduct("001");
        assertEquals("001", product.getId());

        Product product2 = productService.getProduct("001");
        assertEquals(product, product2);

        Product product3 = productService.getProduct("002");
        assertEquals(product, product2);
    }

    @Test
    void cachePut() {
        Product product = Product.builder().id("P002").name("asal").price(100L).build();
        productService.save(product);

        Product product2 = productService.getProduct("P002");
        assertEquals(product, product2);
    }

    @Test
    void cacheEvict() {
        Product product = productService.getProduct("003");
        assertEquals("003", product.getId());

        productService.remove("003");

        Product product2 = productService.getProduct("003");
        assertEquals(product, product2);
    }
}
