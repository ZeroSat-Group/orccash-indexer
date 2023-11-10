package com.ordinalssync.orccash.propose.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.propose.data.object.ProposeDO;
import com.ordinalssync.orccash.propose.mapper.mapper.ProposeMapper;
import com.ordinalssync.orccash.propose.mapper.service.ProposeService;
import org.springframework.stereotype.Service;

@Service
public class ProposeServiceImpl extends ServiceImpl<ProposeMapper, ProposeDO>
        implements ProposeService {}
