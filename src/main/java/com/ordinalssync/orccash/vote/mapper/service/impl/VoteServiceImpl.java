package com.ordinalssync.orccash.vote.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.vote.data.object.VoteDO;
import com.ordinalssync.orccash.vote.mapper.mapper.VoteMapper;
import com.ordinalssync.orccash.vote.mapper.service.VoteService;
import org.springframework.stereotype.Service;

@Service
public class VoteServiceImpl extends ServiceImpl<VoteMapper, VoteDO> implements VoteService {}
