package com.ordinalssync.orccash.config.IdValueGeneratorConfig;

import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Component;

@Component
public class IdValueGeneratorImpl implements IdValueGenerator {
    public IdValueGeneratorImpl() {
    }

    public long nextLong() {
        return IdUtil.getSnowflake().nextId();
    }

    public String nextString() {
        return IdUtil.getSnowflake().nextIdStr();
    }

    public String nextUUID() {
        return IdUtil.objectId();
    }
}
