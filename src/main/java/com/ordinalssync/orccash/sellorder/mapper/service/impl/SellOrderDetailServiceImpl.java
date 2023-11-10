package com.ordinalssync.orccash.sellorder.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.sellorder.data.object.SellOrderDetailDO;
import com.ordinalssync.orccash.sellorder.mapper.mapper.SellOrderDetailMapper;
import com.ordinalssync.orccash.sellorder.mapper.service.SellOrderDetailService;
import org.springframework.stereotype.Service;

@Service
public class SellOrderDetailServiceImpl
        extends ServiceImpl<SellOrderDetailMapper, SellOrderDetailDO>
        implements SellOrderDetailService {}
