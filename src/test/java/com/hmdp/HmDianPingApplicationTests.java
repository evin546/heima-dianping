package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.apache.ibatis.javassist.bytecode.analysis.Executor;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 正式运行环境中指定了环境变量，测试环境也要指定
 */
@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    public void save2RedisTest() {
        shopService.saveShop2Redis(1L, 30L);
    }

    @Test
    public void nextIdTest() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                System.out.println(redisIdWorker.nextId("test"));
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long time = System.currentTimeMillis() - begin;
        System.out.println(time);
    }

}
