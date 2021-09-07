package com.bixin.ido.server.runner;

import com.bixin.ido.server.config.StarConfig;
import com.bixin.ido.server.core.factory.NamedThreadFactory;
import com.bixin.ido.server.core.queue.SwapEventBlockingQueue;
import com.bixin.ido.server.core.redis.RedisCache;
import com.bixin.ido.server.enums.StarSwapEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.stereotype.Component;
import org.starcoin.api.StarcoinSubscriber;
import org.starcoin.bean.EventFilter;
import org.starcoin.bean.EventNotification;
import org.starcoin.bean.EventNotificationResult;
import org.web3j.protocol.websocket.WebSocketService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * @author zhangcheng
 * create          2021-08-24 4:24 下午
 */
@Slf4j
@Component
public class SwapEventSubscriberRunner implements ApplicationRunner {

    @Resource
    StarConfig idoStarConfig;
    @Resource
    RedisCache redisCache;

    AtomicLong atomicSum = new AtomicLong(0);
    static final long initTime = 2000L;
    static final long initIntervalTime = 5000L;
    static final long maxIntervalTime = 60 * 1000L;
    //滤重过期时间 默认20分钟
    static final long duplicateExpiredTime = 20 * 60 * 1000;

    static final String separator = "::";
    ObjectMapper mapper = new ObjectMapper();

    ThreadPoolExecutor poolExecutor;

    @PostConstruct
    public void init() {
        poolExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), new NamedThreadFactory("SwapEventSubscriber-", true));
    }

    @PreDestroy
    public void destroy() {
        try {
            if (Objects.isNull(poolExecutor)) {
                return;
            }
            poolExecutor.shutdown();
            poolExecutor.awaitTermination(1, TimeUnit.SECONDS);
            log.info("SwapEventSubscriberRunner ThreadPoolExecutor stopped");
        } catch (InterruptedException ex) {
            log.error("SwapEventSubscriberRunner InterruptedException: ", ex);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        poolExecutor.execute(() -> process(args));
    }

    public void process(ApplicationArguments args) {
        String[] sourceArgs = 0 == args.getSourceArgs().length ? new String[]{""} : args.getSourceArgs();
        log.info("IdoSwapEventRunner start running [{}]", sourceArgs);
        try {
            WebSocketService service = new WebSocketService("ws://" + idoStarConfig.getSwap().getWebsocketHost() + ":" + idoStarConfig.getSwap().getWebsocketPort(), true);
            service.connect();
            StarcoinSubscriber subscriber = new StarcoinSubscriber(service);
            EventFilter eventFilter = new EventFilter(0, idoStarConfig.getSwap().getWebsocketContractAddress());
            Flowable<EventNotification> flowableTxns = subscriber.newTxnSendRecvEventNotifications(eventFilter);

            Map<StarSwapEventType, LinkedBlockingQueue<JsonNode>> queueMap = SwapEventBlockingQueue.queueMap;

            flowableTxns.blockingIterable().forEach(b -> {
                EventNotificationResult eventResult = b.getParams().getResult();
                StarSwapEventType eventType = StarSwapEventType.of(getEventName(eventResult.getTypeTag()));
                JsonNode data = eventResult.getData();

                // FIXME: 2021/8/30 debug
                try {
                    log.info("SwapEventSubscriberRunner infos: {}", mapper.writeValueAsString(eventResult));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }

                if (Objects.isNull(eventType) || Objects.isNull(data)) {
                    return;
                }
                if (!duplicateEvent(eventResult)) {
                    log.info("SwapEventSubscriberRunner duplicate event data {}", eventResult);
                    return;
                }
                queueMap.get(eventType).offer(data);
            });

        } catch (Throwable e) {
            long duration = initTime + (atomicSum.incrementAndGet() - 1) * initIntervalTime;
            duration = Math.min(duration, maxIntervalTime);
            log.error("IdoSwapEventRunner run exception count {}, next retry {}, params {}",
                    atomicSum.get(), duration, idoStarConfig.getSwap(), e);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(duration));
            DefaultApplicationArguments applicationArguments = new DefaultApplicationArguments("retry " + atomicSum.get());
            this.process(applicationArguments);
        }
    }

    private String getEventName(String typeTag) {
        return typeTag.split(separator)[2];
    }

    /**
     * true 不存在
     * false 已存在
     *
     * @param eventResult
     * @return
     */
    public boolean duplicateEvent(EventNotificationResult eventResult) {
        String typeTag = eventResult.getTypeTag();
        String seqNumber = eventResult.getEventSeqNumber();
        return redisCache.tryGetDistributedLock(typeTag, seqNumber, duplicateExpiredTime);
    }

}
