package com.ordinalssync.orccash.usertokenbalance.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户代币余额表
 */
@Data
@TableName("ordinals_sync_ordinals_user_token_balance")
public class UserTokenBalanceDO{
    @TableField("id")
    private Long id;
    /**
     * ticker
     */
    @TableField("ticker")
    private String ticker;
    /**
     * 地址
     */
    @TableField("address")
    private String address;
    /**
     * 可用余额
     */
    @TableField("available")
    private BigDecimal available;
    /**
     * 总余额
     */
    @TableField("balance")
    private BigDecimal balance;
    /**
     * 可转账余额
     */
    @TableField("transferable_balance")
    private BigDecimal transferableBalance;

    @TableField("orc20_id")
    private Long orc20Id;

    @TableField("inscription_number")
    private Long inscriptionNumber;

    @TableField("is_deployer")
    private Boolean isDeployer;

    @TableField("lock_credits")
    private BigDecimal lockCredits;

    @TableField(exist = false)
    private Boolean insert;

}
