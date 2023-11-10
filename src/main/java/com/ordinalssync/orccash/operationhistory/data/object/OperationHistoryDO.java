package com.ordinalssync.orccash.operationhistory.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ordinalssync.orccash.enume.OperationType;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ordinals_sync_ordinals_operation_history")
public class OperationHistoryDO{
    @TableField("id")
    private Long id;
    /**
     * 类型
     */
    @TableField("type")
    private OperationType type;
    /**
     * 交易hash
     */
    @TableField("tx_hash")
    private String txHash;
    /**
     * 铭文Number
     */
    @TableField("number")
    private Long number;
    /**
     * 铭文Number
     */
    @TableField("token_inscription_number")
    private Long tokenInscriptionNumber;
    /**
     * 铭文id
     */
    @TableField("inscription_id")
    private String inscriptionID;
    /**
     * Ticker
     */
    @TableField("ticker")
    private String ticker;
    /**
     * 数量
     */
    @TableField("amount")
    private BigDecimal amount;
    /**
     * Vout
     */
    @TableField("vout")
    private Integer vout;
    /**
     * CreateIdxKey
     */
    @TableField("create_idx_key")
    private String createIdxKey;
    /**
     * pkScriptFrom
     */
    @TableField("pk_script_from")
    private String pkScriptFrom;
    /**
     * pkScriptTo
     */
    @TableField("pk_script_to")
    private String pkScriptTo;
    /**
     * fromAddress
     */
    @TableField("from_address")
    private String fromAddress;
    /**
     * toAddress
     */
    @TableField("to_address")
    private String toAddress;
    /**
     * satoshi
     */
    @TableField("satoshi")
    private String satoshi;
    /**
     * 块时间
     */
    @TableField("block_time")
    private LocalDateTime blockTime;
    /**
     * 高度
     */
    @TableField("height")
    private Integer height;
    /**
     * 是否合法 0:非法 1:合法 2:待确认
     */
    @TableField("status")
    private Integer status;

    @TableField("orc20_id")
    private Long orc20Id;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("nonce")
    private String nonce;

    @TableField("transferable")
    private Boolean transferable;

    @TableField("holder_address")
    private String holderAddress;

    @TableField("location")
    private String location;
}
