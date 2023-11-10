package com.ordinalssync.orccash.inscriptiondata.constant;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class InscriptionDataConstant {
    public static final String INSCRIPTION_DATA_NAME_REGEX = "^[A-Za-z\\u4E00-\\u9FA5\\s]{1,29}$";
    public static final Pattern INSCRIPTION_DATA_NAME_REGEX_PATTERN =
            Pattern.compile(INSCRIPTION_DATA_NAME_REGEX);

    public static final String ORC20_OP_DEPLOY = "deploy";
    public static final String ORC20_OP_MINT = "mint";
    public static final String ORC20_OP_CANCEL = "cancel";
    public static final String ORC20_OP_UPGRADE = "upgrade";
    public static final String ORC20_OP_AIRDROP = "airdrop";
    public static final String ORC20_OP_SELL= "sell";
    public static final String ORC20_OP_PROPOSE= "propose";
    public static final String ORC20_OP_VOTE= "vote";
    public static final String ORC20_OP_LOCK= "lock";
    public static final String ORC20_OP_BURN= "burn";
    public static final String ORC20_OP_SWAP= "swap";
    public static final List<String> ORC20_OP_TRANSFER = Arrays.asList("transfer", "send");

}