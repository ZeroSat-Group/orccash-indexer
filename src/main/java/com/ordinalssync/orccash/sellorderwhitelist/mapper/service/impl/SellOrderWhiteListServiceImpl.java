package com.ordinalssync.orccash.sellorderwhitelist.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.sellorderwhitelist.data.object.SellOrderWhiteListDO;
import com.ordinalssync.orccash.sellorderwhitelist.mapper.mapper.SellOrderWhiteListMapper;
import com.ordinalssync.orccash.sellorderwhitelist.mapper.service.SellOrderWhiteListService;
import org.springframework.stereotype.Service;

@Service
public class SellOrderWhiteListServiceImpl
        extends ServiceImpl<SellOrderWhiteListMapper, SellOrderWhiteListDO>
        implements SellOrderWhiteListService {}
