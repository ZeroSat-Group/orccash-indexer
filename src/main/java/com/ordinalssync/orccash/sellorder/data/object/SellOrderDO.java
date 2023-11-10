package com.ordinalssync.orccash.sellorder.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售卖表
 */
@Data
@TableName("ordinalssync_orc20_sell_order")
public class SellOrderDO{
    @TableField("id")
    private Long id;
    /** 代币名称 */
    @TableField("ticker")
    private String ticker;
    /** 代币ID */
    @TableField("token_id")
    private Long tokenId;
    /** txID */
    @TableField("tx_id")
    private String txId;
    /** 高度 */
    @TableField("height")
    private Integer height;
    /** inscriptionNumber */
    @TableField("inscription_number")
    private Long inscriptionNumber;
    /** inscriptionId */
    @TableField("inscription_id")
    private String inscriptionId;
    /** tokenInscriptionNumber*/
    @TableField("token_inscription_number")
    private Long tokenInscriptionNumber;
    /** tokenInscriptionId */
    @TableField("token_inscription_id")
    private String tokenInscriptionId;
    /** 总数 */
    @TableField("amount")
    private BigDecimal amount;
    /** 单笔购买最大值 */
    @TableField("lim")
    private BigDecimal lim;
    /** 单价 */
    @TableField("price")
    private BigDecimal price;
    /** 过期高度 */
    @TableField("expire")
    private String expire;
    /** 卖家地址 */
    @TableField("seller")
    private String seller;
    /** 买家地址 */
    @TableField("buyer")
    private String buyer;
    /** 卖出数量 */
    @TableField("sold_amount")
    private BigDecimal soldAmount;
    /** 状态 */
    @TableField("status")
    private Integer status;
    /** 上链时间 */
    @TableField("chain_time")
    private LocalDateTime chainTime;
    /** 激活地址*/
    @TableField("activation_address")
    private String activationAddress;
}
