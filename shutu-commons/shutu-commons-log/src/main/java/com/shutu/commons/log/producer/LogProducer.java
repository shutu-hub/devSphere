package com.shutu.commons.log.producer;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.shutu.commons.tools.redis.RedisKeys;
import com.shutu.commons.tools.redis.RedisUtils;
import com.shutu.commons.log.BaseLog;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/**
 * 日志通过redis队列，异步保存到数据库
 
 */
@Component
public class LogProducer {
    @Resource
    private RedisUtils redisUtils;
    ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNamePrefix("log-producer-pool").build();
    ExecutorService pool = new ThreadPoolExecutor(5, 200, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1024), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy());

    /**
     * 保存Log到Redis消息队列
     */
    public void saveLog(BaseLog log) {
        String key = RedisKeys.getSysLogKey();

        //异步保存到队列
        pool.execute(() -> redisUtils.leftPush(key, log, RedisUtils.NOT_EXPIRE));
    }
}