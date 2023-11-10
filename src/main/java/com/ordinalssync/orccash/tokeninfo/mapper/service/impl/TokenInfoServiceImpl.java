package com.ordinalssync.orccash.tokeninfo.mapper.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ordinalssync.orccash.tokeninfo.data.object.TokenInfoDO;
import com.ordinalssync.orccash.tokeninfo.mapper.mapper.TokenInfoMapper;
import com.ordinalssync.orccash.tokeninfo.mapper.service.TokenInfoService;
import org.springframework.stereotype.Service;

@Service
public class TokenInfoServiceImpl extends ServiceImpl<TokenInfoMapper, TokenInfoDO>
        implements TokenInfoService {
}
