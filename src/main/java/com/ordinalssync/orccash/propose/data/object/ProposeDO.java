package com.ordinalssync.orccash.propose.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提议表
 */
@Data
@TableName("ordinals_sync_orc20_propose")
public class ProposeDO{
    @TableField("id")
    private Long id;
    /** 代币名称 */
    @TableField("ticker")
    private String ticker;
    /** 代币id */
    @TableField("token_id")
    private Long tokenId;
    /** 提议版本 */
    @TableField("propose_version")
    private Integer proposeVersion;
    /** 投票代币比例 */
    @TableField("quorum")
    private Integer quorum;
    /** 提议通过百分比 */
    @TableField("pass")
    private Integer pass;
    /** 过期高度 */
    @TableField("expire")
    private String expire;
    /** 提议内容 */
    @TableField("msg")
    private String msg;
    /** 激活地址 */
    @TableField("active_address")
    private String activeAddress;
    /** 是否激活 */
    @TableField("is_actived")
    private Integer isActived;
    /** 提议状态 */
    @TableField("status")
    private Integer status;
    /** 当前提议通过比例 */
    @TableField("rate")
    private BigDecimal rate;
    /** 赞成代币数量 */
    @TableField("vote_yes")
    private BigDecimal voteYes;
    /** 反对代币数量 */
    @TableField("vote_no")
    private BigDecimal voteNo;
    /** 赞成人数 */
    @TableField("vote_yes_num")
    private Integer voteYesNum;
    /** 反对人数 */
    @TableField("vote_no_num")
    private Integer voteNoNum;
    /** 激活高度 */
    @TableField("active_height")
    private Integer activeHeight;
    /** 已投票的代币数量*/
    @TableField("vote_amount")
    private BigDecimal voteAmount;
    /** 最低投票代币数量*/
    @TableField("total_amount")
    private BigDecimal totalAmount;
    /** 投票结果 */
    @TableField("result")
    private Integer result;
    /** 代币ID */
    @TableField("token_inscription_id")
    private String tokenInscriptionId;
    /** 上链时间 */
    @TableField("chain_time")
    private LocalDateTime chainTime;
    /** 提案铭文Id */
    @TableField("inscription_id")
    private String inscriptionId;
    /** 代币number */
    @TableField("token_inscription_number")
    private Long tokenInscriptionNumber;
}
