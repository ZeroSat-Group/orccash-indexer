package com.ordinalssync.orccash.operationhistory.mapper.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ordinalssync.orccash.operationhistory.data.object.OperationHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationHistoryMapper extends BaseMapper<OperationHistoryDO> {
}
