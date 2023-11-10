package com.ordinalssync.orccash.vote.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投票表
 */
@Data
@TableName("ordinals_sync_orc20_vote")
public class VoteDO{
    @TableField("id")
    private Long id;
    /** 代币名称 */
    @TableField("ticker")
    private String ticker;
    /** 代币Id */
    @TableField("token_id")
    private Long tokenId;
    /** 提议版本 */
    @TableField("propose_version")
    private Integer proposeVersion;
    /** 投票代币数量 */
    @TableField("amount")
    private BigDecimal amount;
    /** 投票内容 */
    @TableField("vote")
    private String vote;
    /** 投票信息 */
    @TableField("msg")
    private String msg;
    /** 提议Id */
    @TableField("propose_id")
    private String proposeId;
    /** 投票状态，0：失败、1：成功*/
    @TableField("status")
    private Integer status;
    /** 铭文id */
    @TableField("inscription_id")
    private String inscriptionId;
    /** 代币Id */
    @TableField("token_inscription_id")
    private String tokenInscriptionId;
    /** 上链时间 */
    @TableField("chain_time")
    private LocalDateTime chainTime;
    /** 投票地址 */
    @TableField("vote_address")
    private String voteAddress;
}
