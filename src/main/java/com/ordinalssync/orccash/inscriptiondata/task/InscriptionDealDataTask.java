package com.ordinalssync.orccash.inscriptiondata.task;


import cn.hutool.core.date.StopWatch;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ComparisonChain;
import com.ordinalssync.orccash.config.RedisDistributedLock;
import com.ordinalssync.orccash.inscriptiondata.InscriptionDataDealService;
import com.ordinalssync.orccash.inscriptiondata.data.object.DealHeightDO;
import com.ordinalssync.orccash.inscriptiondata.data.object.InscriptionDataDO;
import com.ordinalssync.orccash.inscriptiondata.mapper.service.DealHeightService;
import com.ordinalssync.orccash.inscriptiondata.rpc.OrdiRpc;
import com.ordinalssync.orccash.tokeninfo.mapper.service.TokenInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ordinalssync.orccash.inscriptiondata.constant.TokenInfoConstant.*;

@Slf4j
@Component
@EnableScheduling
public class InscriptionDealDataTask {

    private static final String SYNC_LOCK_KEY = "ordinalsSync-cash:inscriptionDataDeal";

    private final RedisTemplate<String, Object> redisTemplate;
    private final InscriptionDataDealService inscriptionDataDealService;
    private final TokenInfoService tokenInfoService;

    private final DealHeightService dealHeightService;

    //TODO rpc地址
    public String ordiUrl = "";

    public InscriptionDealDataTask(RedisTemplate<String, Object> redisTemplate,
                                   InscriptionDataDealService inscriptionDataDealService,
                                   DealHeightService dealHeightService,
                                   TokenInfoService tokenInfoService
    ) {
        this.redisTemplate = redisTemplate;
        this.inscriptionDataDealService = inscriptionDataDealService;
        this.dealHeightService = dealHeightService;
        this.tokenInfoService = tokenInfoService;

    }

    @Scheduled(fixedDelay = 50)
    public void dealData() {

        //need filer sats
        final HashMap<String, Set<Long>> filterMap = new HashMap<>();
        final Set<Long> set = new HashSet<>();
        set.add(1L);
        filterMap.put(SATS, set);
        filterMap.put(VMPX, set);
        filterMap.put(NYTO, set);
        filterMap.put(SDOG, set);
        filterMap.put(SWAP, set);
        filterMap.put(DRAC, set);
        filterMap.put(XCOM, set);

        Set<String> filterInscriptions = new HashSet<>();
        filterInscriptions.add(SATS_INSCRIPTION_ID);
        filterInscriptions.add(NYTO_INSCRIPTION_ID);
        filterInscriptions.add(VMPX_INSCRIPTION_ID);
        filterInscriptions.add(SDOG_INSCRIPTION_ID);
        filterInscriptions.add(SWAP_INSCRIPTION_ID);
        filterInscriptions.add(XCOM_INSCRIPTION_ID);
        filterInscriptions.add(DRAC_INSCRIPTION_ID);
        inscriptionDealData(SYNC_LOCK_KEY, filterMap, true, "1", filterInscriptions, ordiUrl);
    }

    public void inscriptionDealData(String lockKey, HashMap<String, Set<Long>> filterMap, Boolean filter,
                                    String inscriptionId, Set<String> filterInscriptions, String ordiUrl) {
        StopWatch stopWatch = new StopWatch();
        try {
            if (!tryDistributedLock(lockKey)) {
                log.warn("inscriptionDataDeal-cash:获取锁失败, 直接返回");
                return;
            }
            // 查数据库最新高度 779831
            stopWatch.start("查询代币最新高度:" + inscriptionId);
            final DealHeightDO one = dealHeightService.lambdaQuery()
                    .eq(DealHeightDO::getInscriptionId, inscriptionId)
                    .one();

            stopWatch.stop();
            Integer start;
            if (one == null) {
                return;
            } else {
                start = one.getHeight() + 1;
            }
            OrdiRpc ordiRpc = new OrdiRpc(ordiUrl, null);
            final Integer blockCount = ordiRpc.getBlockCount();

            if (blockCount < start) {
                return;
            }

            //txids_by_height
            stopWatch.start("txids_by_height:" + start);
            List<String> txIds = ordiRpc.query("txids_by_height", start, true)
                    .getJSONArray("result")
                    .toJavaList(String.class);
            stopWatch.stop();


            //get transactions
            stopWatch.start("get transactions:" + txIds.size());
            JSONObject transactions = ordiRpc.queryByList("transaction", txIds);
            stopWatch.stop();


            //resolve Inscription Data
            stopWatch.start("resolveInscriptionData");
            final List<JSONObject> result = transactions.getJSONArray("result").toJavaList(JSONObject.class);

            if (txIds.size() != result.size()) {
                log.error("txid size:{}, transactions size: {}", txIds.size(), transactions.size());
                return;
            }

            final List<InscriptionDataDO> inscriptions = result.parallelStream()
                    .map(tx -> InscriptionDataDealService.resolveInscriptionData(tx, txIds, filterMap,
                            filter))
                    .flatMap(Collection::stream)
                    .sorted((o1, o2) -> ComparisonChain
                            .start()
                            .compare(o1.getHeight(), o2.getHeight())
                            .compare(o1.getTxidx(), o2.getTxidx())
                            .compare(o1.getVout(), o2.getVout())
                            .compare(o1.getOffset(), o2.getOffset())
                            .result())
                    .collect(Collectors.toList());

            stopWatch.stop();

            //deal transactions
            stopWatch.start("deal inscriptions:" + inscriptions.size());
            inscriptionDataDealService.dealTxs(inscriptions, start, one, filter, filterInscriptions);
            stopWatch.stop();

            log.info(stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            log.error("铭文同步处理失败{}", e.getMessage(), e);
        } finally {
            releaseDistributedLock(lockKey);
        }
    }


    private boolean tryDistributedLock(String lockKey) {
        return RedisDistributedLock.lock(redisTemplate, lockKey,
                Duration.ofMinutes(1), 3, Duration.ofSeconds(1));
    }

    private void releaseDistributedLock(String lockKey) {
        RedisDistributedLock.releaseLock(redisTemplate, lockKey);
    }
}
