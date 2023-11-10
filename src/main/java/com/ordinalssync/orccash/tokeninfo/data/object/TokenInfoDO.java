package com.ordinalssync.orccash.tokeninfo.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Brc20 Token 详情表
 */
@Data
@TableName("ordinals_sync_ordinals_token_info")
public class TokenInfoDO{
    @TableField("id")
    private Long id;
    @TableField("version")
    private Long version;
    /**
     * ticker
     */
    @TableField("ticker")
    private String ticker;
    /**
     * 最大数
     */
    @TableField("max_number")
    private BigDecimal maxNumber;
    /**
     * 限制数
     */
    @TableField("limit_number")
    private BigDecimal limitNumber;
    /**
     * mint总量
     */
    @TableField("total_minted")
    private BigDecimal totalMinted;
    /**
     * 小数位
     */
    @TableField("token_decimal")
    private Integer tokenDecimal;
    /**
     * 交易hash
     */
    @TableField("tx_id")
    private String txId;
    /**
     * 铭文id
     */
    @TableField("inscription_id")
    private String inscriptionID;
    /**
     * 铭文number
     */
    @TableField("inscription_number")
    private Long inscriptionNumber;
    /**
     * vout
     */
    @TableField("vout")
    private Integer vout;
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
    /**
     * 部署人
     */
    @TableField("deployer")
    private String deployer;
    /**
     * createIdxKey
     */
    @TableField("create_idx_key")
    private String createIdxKey;
    /**
     * 部署高度
     */
    @TableField("deploy_height")
    private Integer deployHeight;
    /**
     * 部署时间
     */
    @TableField("deploy_times")
    private LocalDateTime deployTimes;
    /**
     * 完成高度
     */
    @TableField("complete_height")
    private Integer completeHeight;
    /**
     * 完成高度完成时间
     */
    @TableField("complete_block_time")
    private LocalDateTime completeBlockTime;
    /**
     * 上链时间
     */
    @TableField("inscription_number_end")
    private Long inscriptionNumberEnd;

    @TableField("holders")
    private Integer holders;

    @TableField("transactions")
    private Integer transactions;

    @TableField("upgradable")
    private Boolean upgradable;

    @TableField("name")
    private String name;

    @TableField("migration_wrapper")
    private Boolean migrationWrapper;

    @TableField("token_version")
    private String tokenVersion;

    @TableField("message")
    private String message;

    @TableField("orc20_id")
    private Long orc20Id;

    //todo
    @TableField("progress")
    private BigDecimal progress;

    @TableField("protocol")
    private String protocol;

    @TableField(exist = false)
    private Boolean insert;

    @TableField("is_self")
    private Boolean isSelf;
}
