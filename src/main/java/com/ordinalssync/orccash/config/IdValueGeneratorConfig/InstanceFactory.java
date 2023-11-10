package com.ordinalssync.orccash.config.IdValueGeneratorConfig;

import cn.hutool.aop.ProxyUtil;
import cn.hutool.extra.spring.SpringUtil;

import java.lang.reflect.InvocationHandler;
import java.util.concurrent.atomic.AtomicReference;

public class InstanceFactory {
    public InstanceFactory() {
    }

    public static <T> T create(Class<T> clazz) {
        AtomicReference<T> bean = new AtomicReference();
        InvocationHandler handler = (proxy, method, args) -> {
            if (bean.get() == null) {
                bean.set(SpringUtil.getBean(clazz));
            }

            return method.invoke(bean.get(), args);
        };
        return ProxyUtil.newProxyInstance(handler, new Class[]{clazz});
    }
}
