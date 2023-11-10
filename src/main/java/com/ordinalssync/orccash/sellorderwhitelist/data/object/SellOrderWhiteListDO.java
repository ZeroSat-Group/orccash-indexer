package com.ordinalssync.orccash.sellorderwhitelist.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 白名单表
 */
@Data
@TableName("ordinalssync_orc20_sell_order_white_list")
public class SellOrderWhiteListDO{
    @TableField("id")
    private Long id;
    /** 卖单ID */
    @TableField("sell_order_id")
    private Long sellOrderId;
    /** 地址 */
    @TableField("address")
    private String address;
    /** 限额 */
    @TableField("lim")
    private BigDecimal lim;
    /** 百分比 */
    @TableField("amount_filled")
    private BigDecimal amountFilled;
}
