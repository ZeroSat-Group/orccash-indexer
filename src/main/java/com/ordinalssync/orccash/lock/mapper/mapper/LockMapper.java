package com.ordinalssync.orccash.lock.mapper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ordinalssync.orccash.lock.data.object.LockDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LockMapper extends BaseMapper<LockDO> {}
