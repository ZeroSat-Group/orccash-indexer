package com.ordinalssync.orccash.enume;

import lombok.Getter;


@Getter
public enum OperationType {
    INSCRIBE_DEPLOY("inscribe-deploy"),
    INSCRIBE_MINT("inscribe-mint"),
    INSCRIBE_CANCEL("inscribe-cancel"),
    INSCRIBE_SEND("inscribe-send"),
    INSCRIBE_REMAINING_BALANCE("inscribe-remaining-balance"),
    INSCRIBE_UPGRADE("inscribe-upgrade"),
    INSCRIBE_AIRDROP("inscribe-airdrop"),
    INSCRIBE_SELL("inscribe-sell"),
    INSCRIBE_PROPOSE("inscribe-propose"),
    INSCRIBE_VOTE("inscribe-vote"),
    INSCRIBE_LOCK("inscribe-lock"),
    INSCRIBE_BURN("inscribe-burn"),
    INSCRIBE_SWAP("inscribe-swap"),
    TRANSFER("transfer"),
    UPGRADE("upgrade"),
    AIRDROP("airdrop"),
    ;
    public final String name;

    OperationType(String name) {
        this.name = name;
    }

    public static OperationType valueOfName(String name) {
        final OperationType[] operationTypes = OperationType.values();
        for (OperationType type : operationTypes) {
            if (type.getName().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
