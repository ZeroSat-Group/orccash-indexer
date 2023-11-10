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
public class DealDracTask {

    private static final String SYNC_LOCK_KEY = "ordinalsSync-cash:dealDRAC";


    private final InscriptionDealDataTask inscriptionDealDataTask;


    //TODO rpc地址
    public String ordiUrl = "";

    public DealDracTask(InscriptionDealDataTask inscriptionDealDataTask
    ) {
        this.inscriptionDealDataTask = inscriptionDealDataTask;
    }

    @Scheduled(fixedDelay = 50)
    public void dealData() {
        //788668
        final HashMap<String, Set<Long>> filterMap = new HashMap<>();
        final Set<Long> set = new HashSet<>();
        set.add(1L);
        filterMap.put(DRAC, set);
        Set<String> filterInscriptions = new HashSet<>();
        filterInscriptions.add(DRAC_INSCRIPTION_ID);
        inscriptionDealDataTask.inscriptionDealData(SYNC_LOCK_KEY,
                filterMap,
                false,
                DRAC_INSCRIPTION_ID,
                filterInscriptions,
                ordiUrl);
    }


}
