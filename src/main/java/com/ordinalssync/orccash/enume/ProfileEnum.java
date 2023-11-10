package com.ordinalssync.orccash.enume;

import cn.hutool.extra.spring.SpringUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public enum ProfileEnum {
    LOCAL("local"),
    TEST("test"),
    PROD("prod");

    private static List<String> ACTIVE_PROFILES = (List)Optional.ofNullable(SpringUtil.getActiveProfiles()).map(Arrays::asList).orElse(new ArrayList());
    private final String profile;

    public boolean isActive() {
        if (ACTIVE_PROFILES.isEmpty()) {
            ACTIVE_PROFILES = (List)Optional.ofNullable(SpringUtil.getActiveProfiles()).map(Arrays::asList).orElse(new ArrayList());
        }

        return ACTIVE_PROFILES.contains(this.profile);
    }

    public String getProfile() {
        return this.profile;
    }

    private ProfileEnum(final String profile) {
        this.profile = profile;
    }
}

