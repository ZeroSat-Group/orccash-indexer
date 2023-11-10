package com.ordinalssync.orccash.inscriptiondata.data.object;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;


@Data
//@EqualsAndHashCode(callSuper = true)
@TableName("ordinals_sync_ordinals_deal_height")
public class DealHeightDO{

    @TableField("id")
    private Long id;
    /**
     * 同步到的高度
     */
    @TableField("height")
    private Integer height;

    @TableField("inscription_id")
    private String inscriptionId;
}
