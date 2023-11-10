package com.ordinalssync.orccash.operationhistory.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.operationhistory.data.object.OperationHistoryDO;
import com.ordinalssync.orccash.operationhistory.mapper.mapper.OperationHistoryMapper;
import com.ordinalssync.orccash.operationhistory.mapper.service.OperationHistoryService;
import org.springframework.stereotype.Service;

@Service
public class OperationHistoryServiceImpl
        extends ServiceImpl<OperationHistoryMapper, OperationHistoryDO>
        implements OperationHistoryService {
}
