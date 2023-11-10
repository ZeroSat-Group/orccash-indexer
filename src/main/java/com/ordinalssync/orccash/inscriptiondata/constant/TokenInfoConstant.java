package com.ordinalssync.orccash.inscriptiondata.constant;

import java.util.regex.Pattern;

public class TokenInfoConstant {
    public static final String TOKEN_INFO_NAME_REGEX = "^[A-Za-z\\u4E00-\\u9FA5\\s]{1,29}$";
    public static final String SATS = "sats";
    public static final String VMPX = "vmpx";
    public static final String NYTO = "nyto";
    public static final String DRAC = "drac";
    public static final String SDOG = "$dog";
    public static final String SWAP = "$wap";
    public static final String XCOM = "xcom";
    public static final String SATS_INSCRIPTION_ID =
            "9b664bdd6f5ed80d8d88957b63364c41f3ad4efb8eee11366aa16435974d9333i0";
    public static final String VMPX_INSCRIPTION_ID =
            "beafe671f13b86300454d787d31e2918442d396225098a9c12ae4bf4d077196fi0";
    public static final String NYTO_INSCRIPTION_ID =
            "50f0b4bb68b8c2e9958617131a4eb3d97daf7366c1c1d450ca5c21ec5d3c9650i0";
    //787528
    public static final String SWAP_INSCRIPTION_ID =
            "3855656ede7384be407c1951e0106c92174ef5c4bfcd9ffc58a5dbe3f0bfcc7bi0";
    //787001
    public static final String SDOG_INSCRIPTION_ID =
            "799e9136a191347575dafebf1dcd42536fa233cf7b3732757fc5acd5b9fd5406i0";
    //788569
    public static final String DRAC_INSCRIPTION_ID =
            "02e4fdb6da83b463236ba8c28ce6e3888ef6c0217f38d2e1a94062b2a3695d1ei0";
    //787445
    public static final String XCOM_INSCRIPTION_ID =
            "7d51f3f6e211cbb4c2a16bfae6ff792ad5a52f4237139eb99cccbad84f06207fi0";
    public static final Pattern TOKEN_INFO_NAME_REGEX_PATTERN =
            Pattern.compile(TOKEN_INFO_NAME_REGEX);
}
