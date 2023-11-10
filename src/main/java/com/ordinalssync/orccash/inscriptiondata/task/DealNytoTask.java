package com.ordinalssync.orccash.inscriptiondata.task;


import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static com.ordinalssync.orccash.inscriptiondata.constant.TokenInfoConstant.*;


@Slf4j
@Component
@EnableScheduling
public class DealNytoTask {

    private static final String SYNC_LOCK_KEY = "ordinalsSync-cash:dealNyto";


    private final InscriptionDealDataTask inscriptionDealDataTask;


    //TODO rpc地址
    public String ordiUrl = "";

    public DealNytoTask(InscriptionDealDataTask inscriptionDealDataTask
    ) {
        this.inscriptionDealDataTask = inscriptionDealDataTask;
    }
    @Scheduled(fixedDelay = 50)
    public void dealData() {
        //787916
        final HashMap<String, Set<Long>> filterMap = new HashMap<>();
        final Set<Long> set = new HashSet<>();
        set.add(1L);
        filterMap.put(NYTO, set);
        Set<String> filterInscriptions = new HashSet<>();
        filterInscriptions.add(NYTO_INSCRIPTION_ID);
        inscriptionDealDataTask.inscriptionDealData(SYNC_LOCK_KEY,
                filterMap,
                false,
                NYTO_INSCRIPTION_ID,
                filterInscriptions,
                ordiUrl);
    }


}
