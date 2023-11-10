package com.ordinalssync.orccash.inscriptiondata.rpc;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSONObject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;


//import java.net.http.HttpRequest;
import java.util.List;

@Value
@Slf4j
public class BtcRpc {
    String url;

    String auth;

    public BtcRpc(String url, String auth) {
        this.url = url;
        this.auth = auth;
    }

    public <T> T query(Class<T> clazz, String method, Object... o) {

        String body = new JSONObject()
                .fluentPut("jsonrpc", "2.0")
                .fluentPut("id", 1)
                .fluentPut("method", method)
                .fluentPut("params", List.of(o))
                .toJSONString();

        try (var r = HttpRequest
                .post(url).body(body)
                .header("Authorization", auth)
                .timeout(10000)
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
            return rspJson.getObject("result", clazz);
        }
    }

    public String sendTransaction(String signedData) {
        return query(String.class, "sendrawtransaction", signedData);
    }

    public String getRawTransaction(String txHash) {
        return query(String.class, "getrawtransaction", txHash, false);
    }

    public JSONObject getTransaction(String txHash) {
        return query(JSONObject.class, "getrawtransaction", txHash, true);
    }

    public JSONObject getBlock(String blockHash) {

        return query(JSONObject.class, "getblock", blockHash, 2);
    }

    public Integer getBlockCount() {

        return query(Integer.class, "getblockcount");
    }


    public String getBlockHash(Integer height) {

        return query(String.class, "getblockhash", height);
    }


}
