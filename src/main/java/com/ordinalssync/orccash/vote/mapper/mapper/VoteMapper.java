package com.ordinalssync.orccash.vote.mapper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ordinalssync.orccash.vote.data.object.VoteDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VoteMapper extends BaseMapper<VoteDO> {}
