package com.ordinalssync.orccash.inscriptiondata.task;


import cn.hutool.core.date.StopWatch;
import com.ordinalssync.orccash.config.RedisDistributedLock;
import com.ordinalssync.orccash.inscriptiondata.data.object.DealHeightDO;
import com.ordinalssync.orccash.inscriptiondata.mapper.service.DealHeightService;
import com.ordinalssync.orccash.propose.data.object.ProposeDO;
import com.ordinalssync.orccash.propose.mapper.service.ProposeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@EnableScheduling
public class InscriptionDealVoteTask {

    private static final String SYNC_LOCK_KEY = "ordinalsSync-cash:inscriptionDataDealVote";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DealHeightService dealHeightService;
    private final ProposeService proposeService;

    public InscriptionDealVoteTask(RedisTemplate<String, Object> redisTemplate,
                                   DealHeightService dealHeightService,
                                   ProposeService proposeService
    ) {
        this.redisTemplate = redisTemplate;
        this.dealHeightService = dealHeightService;
        this.proposeService = proposeService;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void dealVote(){
        StopWatch stopWatch = new StopWatch();
        stopWatch.start("查询propose数据");
        try {
            if (!tryDistributeLock()) {
                log.warn("inscriptionDataDealVote-cash:获取锁失败, 直接返回");
                return;
            }
            List<DealHeightDO> dealList = dealHeightService.list();

            dealList.forEach(one -> {


                List<ProposeDO> list;

                if ("1".equals(one.getInscriptionId())) {
                    List<String> ids = dealList.stream().map(DealHeightDO::getInscriptionId)
                            .filter(deal -> !"1".equals(deal))
                            .collect(Collectors.toList());
                    list = proposeService.lambdaQuery().
                            eq(ProposeDO::getIsActived, 0).
                            eq(ProposeDO::getStatus, 1).
                            ne(ProposeDO::getExpire, "never").
                            in(ProposeDO::getTokenInscriptionId, ids).
                            list();
                } else {
                    list = proposeService.lambdaQuery().
                            eq(ProposeDO::getIsActived, 0).
                            eq(ProposeDO::getStatus, 1).
                            ne(ProposeDO::getExpire, "never").
                            eq(ProposeDO::getTokenInscriptionId, one.getInscriptionId()).
                            list();
                }

                Integer height = one.getHeight();

                for (ProposeDO proposeDO : list) {
                    Integer expire = Integer.valueOf(proposeDO.getExpire());
                    if (expire + proposeDO.getActiveHeight() < height) {
                        proposeDO.setStatus(3);
                        proposeDO.setResult(2);
                    }
                }
                proposeService.updateBatchById(list);
            });

            stopWatch.stop();

            log.info(stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
        } catch (NumberFormatException e) {
            log.error("vote同步处理失败{}", e.getMessage(), e);
        } finally {
            releaseDistributedLock();
        }
    }

    public Boolean tryDistributeLock(){
        return RedisDistributedLock.lock(redisTemplate, SYNC_LOCK_KEY,
                Duration.ofMinutes(1), 3, Duration.ofSeconds(1));
    }

    private void releaseDistributedLock() {
        RedisDistributedLock.releaseLock(redisTemplate, SYNC_LOCK_KEY);
    }
}
