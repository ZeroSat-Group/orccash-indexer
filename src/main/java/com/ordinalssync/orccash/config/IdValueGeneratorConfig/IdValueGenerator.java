package com.ordinalssync.orccash.config.IdValueGeneratorConfig;

public interface IdValueGenerator {
    IdValueGenerator INSTANCE = (IdValueGenerator)InstanceFactory.create(IdValueGenerator.class);

    long nextLong();

    String nextString();

    String nextUUID();
}
