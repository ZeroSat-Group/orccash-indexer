package com.ordinalssync.orccash.inscriptiondata.rpc;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ordinalssync.orccash.operationhistory.data.object.OperationHistoryDO;
import com.ordinalssync.orccash.sellorder.data.object.SellOrderDetailDO;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Value
@Slf4j
public class OrdiRpc {
    String url;

    String auth;

    public OrdiRpc(String url, String auth) {
        this.url = url;
        this.auth = auth;
    }

    public JSONObject query(String method, Object... o) {

        String body = new JSONObject()
                .fluentPut("jsonrpc", "2.0")
                .fluentPut("id", 1)
                .fluentPut("method", method)
                .fluentPut("params", List.of(o))
                .toJSONString();

        try (var r = HttpRequest
                .post(url).body(body)
                .header("Authorization", auth)
                .timeout(3000000)
                .execute()
        ) {
            var rspJson = JSONObject.parseObject(r.body());
            if (!r.isOk()) {
                log.error("[{}] call error: [{}], [{}]-[{}]", method, body, r.getStatus(), rspJson);
                throw new RuntimeException();
            }
            if (rspJson.get("error") != null) {
                log.error("[{}] call error: [{}], [{}]", method, body, rspJson.getString("error"));
                throw new RuntimeException();
            }
            return rspJson;
        }
    }

    public JSONObject queryByList(String method, List list) {

        String body = new JSONObject()
                .fluentPut("jsonrpc", "2.0")
                .fluentPut("id", 1)
                .fluentPut("method", method)
                .fluentPut("params", list)
                .toJSONString();

        try (var r = HttpRequest
                .post(url).body(body)
                .header("Authorization", auth)
                .timeout(3000000)
                .execute()
        ) {
            var rspJson = JSONObject.parseObject(r.body());
            if (!r.isOk()) {
                log.error("[{}] call error: [{}], [{}]-[{}]", method, body, r.getStatus(), rspJson);
                throw new RuntimeException();
            }
            if (rspJson.get("error") != null) {
                log.error("[{}] call error: [{}], [{}]", method, body, rspJson.getString("error"));
                throw new RuntimeException();
            }
            return rspJson;
        }
    }

    public HashMap<String, List<SellOrderDetailDO>> getTx(Integer height, Set<String> sellAddressSets){
        JSONObject jsonObject = query("transactions_filter", height, sellAddressSets);
        Object result = jsonObject.get("result");
        System.out.println(result);
        JSONObject jsonObjectByAddress = JSON.parseObject(result.toString());
        HashMap<String, List<SellOrderDetailDO>> map = new HashMap<>();
        LocalDateTime blockTime = getBlockTime(height);
        for (String addressSet : sellAddressSets) {
            JSONArray jsonArray = jsonObjectByAddress.getJSONArray(addressSet);
            List<SellOrderDetailDO> sellOrderDetailDOList = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject tx = jsonArray.getJSONObject(i);
                String amount = tx.get("amount").toString();
                BigDecimal btcAmount = new BigDecimal(amount);
                if (!amount.isEmpty()){
                    if (btcAmount.equals(BigDecimal.ZERO)){
                        continue;
                    }
                }

                String txid = tx.get("txid").toString();
                String from = tx.get("from").toString();
                SellOrderDetailDO sellOrderDetailDO = new SellOrderDetailDO();
                sellOrderDetailDO.setTxId(txid);
                sellOrderDetailDO.setBtcAmount(new BigDecimal(amount));
                sellOrderDetailDO.setAddress(from);
                sellOrderDetailDO.setChainTime(blockTime);
                sellOrderDetailDOList.add(sellOrderDetailDO);

            }
            map.put(addressSet, sellOrderDetailDOList);
        }
        return map;
    }


    public Integer getBlockCount() {

        return query("blockheight").getInteger("result");
    }

    public JSONObject psbtExtractTx(String sellerPsbt) {

        return query("psbt_extract_tx", sellerPsbt).getJSONObject("result");
    }

    public LocalDateTime getBlockTime(Integer height) {
        Object o = query("block_time", height).get("result");
        String s = o.toString();
        Instant instant = Instant.ofEpochSecond(Long.valueOf(s));
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, zoneId);
        return localDateTime;

    }
}
