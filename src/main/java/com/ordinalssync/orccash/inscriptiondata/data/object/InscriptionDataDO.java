package com.ordinalssync.orccash.inscriptiondata.data.object;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigInteger;
import java.time.LocalDateTime;

@Data
//@EqualsAndHashCode(callSuper = true)
@TableName("ordinals_sync_ordinals_inscription_data")
public class InscriptionDataDO{

    @TableField("id")
    private Long id;
    /**
     * 是否是交易
     */
    @TableField("is_transfer")
    private Boolean isTransfer;
    /**
     * 交易hash
     */
    @TableField("tx_hash")
    private String txHash;
    /**
     * vout
     */
    @TableField("vout")
    private Integer vout;
    @TableField("offset")
    private BigInteger offset;
    /**
     * satoshi
     */
    @TableField("satoshi")
    private String satoshi;
    /**
     * pkScript
     */
    @TableField("pk_script")
    private String pkScript;

    @TableField("from_address")
    private String fromAddress;

    @TableField("to_address")
    private String toAddress;

    /**
     * 铭文Number
     */
    @TableField("inscription_number")
    private Long inscriptionNumber;
    @TableField("inscription_id")
    private String inscriptionID;
    /**
     * 内容
     */
    @TableField("content_body")
    private String contentBody;
    /**
     * createIdxKey
     */
    @TableField("create_idx_key")
    private String createIdxKey;
    /**
     * 高度
     */
    @TableField("height")
    private Integer height;
    /**
     * 区块时间
     */
    @TableField("block_time")
    private LocalDateTime blockTime;
    @TableField("genesis_transaction")
    private String genesisTransaction;
    @TableField("txidx")
    private Integer txidx;
    @TableField("deal")
    private Boolean deal;
    @TableField("protocol")
    private String protocol;

    @TableField("location")
    private String location;
}
