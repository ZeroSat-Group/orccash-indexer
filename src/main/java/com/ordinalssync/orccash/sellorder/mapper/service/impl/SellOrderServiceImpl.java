package com.ordinalssync.orccash.sellorder.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.sellorder.data.object.SellOrderDO;
import com.ordinalssync.orccash.sellorder.mapper.mapper.SellOrderMapper;
import com.ordinalssync.orccash.sellorder.mapper.service.SellOrderService;
import org.springframework.stereotype.Service;


@Service
public class SellOrderServiceImpl extends ServiceImpl<SellOrderMapper, SellOrderDO>
        implements SellOrderService {

}
