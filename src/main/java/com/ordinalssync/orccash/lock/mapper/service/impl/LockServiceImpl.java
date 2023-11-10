package com.ordinalssync.orccash.lock.mapper.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.lock.data.object.LockDO;
import com.ordinalssync.orccash.lock.mapper.mapper.LockMapper;
import com.ordinalssync.orccash.lock.mapper.service.LockService;
import org.springframework.stereotype.Service;

@Service
public class LockServiceImpl extends ServiceImpl<LockMapper, LockDO> implements LockService {}
