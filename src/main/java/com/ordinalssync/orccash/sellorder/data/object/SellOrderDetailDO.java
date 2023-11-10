package com.ordinalssync.orccash.sellorder.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 卖单明细表
 */
@Data
@TableName("ordinalssync_orc20_sell_order_detail")
public class SellOrderDetailDO{
    @TableField("id")
    private Long id;
    /** 卖单ID */
    @TableField("sell_order_id")
    private Long sellOrderId;
    /** btc数量 */
    @TableField("btc_amount")
    private BigDecimal btcAmount;
    /** 代币数量 */
    @TableField("token_amount")
    private BigDecimal tokenAmount;
    /** 上链时间 */
    @TableField("chain_time")
    private LocalDateTime chainTime;
    /** 状态 */
    @TableField("status")
    private Integer status;
    /** 地址 */
    @TableField("address")
    private String address;
    /** 交易ID */
    @TableField("tx_id")
    private String txId;
}
