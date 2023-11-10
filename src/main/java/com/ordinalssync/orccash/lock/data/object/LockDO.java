package com.ordinalssync.orccash.lock.data.object;


import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("ordinals_sync_orc20_lock")
public class LockDO{
    @TableField("id")
    private Long id;
    /** 代币名称 */
    @TableField("ticker")
    private String ticker;
    /** orc20Id */
    @TableField("token_id")
    private Long tokenId;
    /** 冻结代币数量 */
    @TableField("amount")
    private BigDecimal amount;
    /** 过期时间 */
    @TableField("expire")
    private String expire;
    /** 转账地址 */
    @TableField("to_address")
    private String toAddress;
    /** 冻结状态 */
    @TableField("status")
    private Integer status;
    /** 是否激活 */
    @TableField("is_activated")
    private Integer isActivated;
    /** 初始铭刻地址 */
    @TableField("initial_address")
    private String initialAddress;
    /** 激活地址 */
    @TableField("active_height")
    private Integer activeHeight;
    /** inscriptionId */
    @TableField("inscription_id")
    private String inscriptionId;
    /** 代币ID */
    @TableField("token_inscription_id")
    private String tokenInscriptionId;
}
