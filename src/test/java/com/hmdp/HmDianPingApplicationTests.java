package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.apache.ibatis.javassist.bytecode.analysis.Executor;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Resource
    private RedissonClient redissonClient;



    @Test
    public void testRedisson() throws InterruptedException {
        RLock lock = redissonClient.getLock("test:lock");

        System.out.println(lock.tryLock(1, 10, TimeUnit.SECONDS));


    }

}
