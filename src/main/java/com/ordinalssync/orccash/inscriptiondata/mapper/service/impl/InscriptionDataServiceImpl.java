package com.ordinalssync.orccash.inscriptiondata.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.inscriptiondata.data.object.InscriptionDataDO;
import com.ordinalssync.orccash.inscriptiondata.mapper.mapper.InscriptionDataMapper;
import com.ordinalssync.orccash.inscriptiondata.mapper.service.InscriptionDataService;
import org.springframework.stereotype.Service;

@Service
public class InscriptionDataServiceImpl
        extends ServiceImpl<InscriptionDataMapper, InscriptionDataDO>
        implements InscriptionDataService {
}
