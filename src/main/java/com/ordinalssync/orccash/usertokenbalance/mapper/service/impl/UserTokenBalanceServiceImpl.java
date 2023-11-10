package com.ordinalssync.orccash.usertokenbalance.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.usertokenbalance.data.object.UserTokenBalanceDO;
import com.ordinalssync.orccash.usertokenbalance.mapper.mapper.UserTokenBalanceMapper;
import com.ordinalssync.orccash.usertokenbalance.mapper.service.UserTokenBalanceService;
import org.springframework.stereotype.Service;

@Service
public class UserTokenBalanceServiceImpl
        extends ServiceImpl<UserTokenBalanceMapper, UserTokenBalanceDO>
        implements UserTokenBalanceService {
}
