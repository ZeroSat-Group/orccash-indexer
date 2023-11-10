package com.ordinalssync.orccash.inscriptiondata;

import cn.hutool.core.date.StopWatch;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordinalssync.orccash.config.IdValueGeneratorConfig.IdValueGenerator;
import com.ordinalssync.orccash.enume.OperationType;
import com.ordinalssync.orccash.enume.ProfileEnum;
import com.ordinalssync.orccash.inscriptiondata.data.object.DealHeightDO;
import com.ordinalssync.orccash.inscriptiondata.data.object.InscriptionDataDO;
import com.ordinalssync.orccash.inscriptiondata.mapper.service.DealHeightService;
import com.ordinalssync.orccash.inscriptiondata.mapper.service.InscriptionDataService;
import com.ordinalssync.orccash.inscriptiondata.model.InscriptionContent;
import com.ordinalssync.orccash.inscriptiondata.rpc.OrdiRpc;
import com.ordinalssync.orccash.lock.data.object.LockDO;
import com.ordinalssync.orccash.lock.mapper.service.LockService;
import com.ordinalssync.orccash.operationhistory.data.object.OperationHistoryDO;
import com.ordinalssync.orccash.operationhistory.mapper.service.OperationHistoryService;
import com.ordinalssync.orccash.propose.data.object.ProposeDO;
import com.ordinalssync.orccash.propose.mapper.service.ProposeService;
import com.ordinalssync.orccash.sellorder.data.object.SellOrderDO;
import com.ordinalssync.orccash.sellorder.data.object.SellOrderDetailDO;
import com.ordinalssync.orccash.sellorder.mapper.service.SellOrderDetailService;
import com.ordinalssync.orccash.sellorder.mapper.service.SellOrderService;
import com.ordinalssync.orccash.sellorderwhitelist.data.object.SellOrderWhiteListDO;
import com.ordinalssync.orccash.sellorderwhitelist.mapper.service.SellOrderWhiteListService;
import com.ordinalssync.orccash.tokeninfo.data.object.TokenInfoDO;
import com.ordinalssync.orccash.tokeninfo.mapper.service.TokenInfoService;
import com.ordinalssync.orccash.usertokenbalance.data.object.UserTokenBalanceDO;
import com.ordinalssync.orccash.usertokenbalance.mapper.service.UserTokenBalanceService;
import com.ordinalssync.orccash.vote.data.object.VoteDO;
import com.ordinalssync.orccash.vote.mapper.service.VoteService;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ordinalssync.orccash.enume.OperationType.*;
import static com.ordinalssync.orccash.inscriptiondata.constant.InscriptionDataConstant.*;

@Slf4j
@Service
public class InscriptionDataDealService {
    private static final Integer SELL_HEIGHT = 804467;

    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public String ordiUrl;

    public List<String> VALIDATION_ADDRESS;

    @Autowired
    private InscriptionDataService inscriptionDataService;
    @Autowired
    private TokenInfoService tokenInfoService;
    @Autowired
    private OperationHistoryService operationHistoryService;
    @Autowired
    private UserTokenBalanceService userTokenBalanceService;
    @Autowired
    private DealHeightService dealHeightService;
    @Autowired
    private SellOrderService sellOrderService;
    @Autowired
    private SellOrderWhiteListService sellOrderWhiteListService;
    @Autowired
    private SellOrderDetailService sellOrderDetailService;
    @Autowired
    private ProposeService proposeService;
    @Autowired
    private VoteService voteService;
    @Autowired
    private LockService lockService;

    InscriptionDataDealService() {
        VALIDATION_ADDRESS = Arrays.asList("1BitcoinEaterAddressDontSendf59kuE");
        //TODO rpc地址
        ordiUrl = "";
    }


    public static List<InscriptionDataDO> resolveInscriptionData(JSONObject result, List<String> txidxs,
                                                                 Map<String, Set<Long>> tickerMap,
                                                                 Boolean filter) {

        final Long longBlockTime = result.getLong("blocktime");
        final String hash = result.getString("txid");
        final JSONArray inscriptions = result.getJSONArray("inscriptions_ontx");

        final List<JSONObject> list = inscriptions.toJavaList(JSONObject.class);
        List<InscriptionDataDO> inscriptionData = new ArrayList<>();
        if (list.isEmpty()) {
            log.error("{}:没有铭文数据", hash);
            return inscriptionData;
        }

        for (JSONObject inscription : list) {
            InscriptionDataDO inscriptionDataDO = new InscriptionDataDO();
            final String contentType = inscription.getJSONObject("content").getString("content_type");
            if (!"text/plain;charset=utf-8".equals(contentType) && !"text/plain".equals(contentType)) {
                continue;
            }
            final Long number = inscription.getLong("number");
            if (number < 0) {
                continue;
            }
            inscriptionDataDO.setInscriptionNumber(number);

            inscriptionDataDO.setInscriptionID(inscription.getString("inscription_id"));

            inscriptionDataDO.setBlockTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(
                    longBlockTime, 0), ZoneId.systemDefault()));

            inscriptionDataDO.setId(IdValueGenerator.INSTANCE.nextLong());
            final String content = new String(Base64.getDecoder().decode(inscription.getJSONObject("content").getString(
                    "body")));

            try {
                final JSONObject jsonObject = JSONObject.parseObject(content);
                final String p = jsonObject.getString("p");

                //校验协议
                if (!"orc-cash".equals(p) && !"orc-20".equals(p) && !"orc20".equals(p) && !"brc-20".equals(p)) {
                    continue;
                }

                if ("orc-20".equals(p) || "orc20".equals(p)) {
                    inscriptionDataDO.setProtocol("orc-20");
                } else {
                    inscriptionDataDO.setProtocol(p);
                }
                String op = jsonObject.getString("op");

                //过滤brc20 transfer
                if ("brc-20".equals(p) && "transfer".equals(op)) {
                    continue;
                }

                //过滤代币
                final String tick = jsonObject.getString("tick");
                Long id;
                if (jsonObject.getString("id") == null &&
                        Arrays.asList(ORC20_OP_DEPLOY, ORC20_OP_MINT).contains(op)) {
                    id = 1L;
                } else {
                    id = jsonObject.getLong("id");
                }

                if (filter) {
                    //过滤掉map里的代币
                    if (tickerMap.containsKey(tick.toLowerCase(Locale.ROOT)) && tickerMap.get(tick).contains(id)) {
                        continue;
                    }
                } else {
                    //需要map里的代币
                    if (!tickerMap.containsKey(tick.toLowerCase(Locale.ROOT))) {
                        continue;
                    } else {
                        if (!tickerMap.get(tick.toLowerCase(Locale.ROOT)).contains(id)) {
                            continue;
                        }
                    }
                }

            } catch (Exception e) {
                continue;
            }


            inscriptionDataDO.setContentBody(content);
            inscriptionDataDO.setHeight(result.getInteger("height"));
            inscriptionDataDO.setTxHash(hash);
            inscriptionDataDO.setGenesisTransaction(inscription.getString("genesis_transaction"));

            inscriptionDataDO.setIsTransfer(!hash.equals(inscription.getString("genesis_transaction")));
            inscriptionDataDO.setOffset(inscription.getBigInteger("idx"));
            inscriptionDataDO.setVout(inscription.getInteger("vout"));
            inscriptionDataDO.setFromAddress(inscription.getString("from"));
            inscriptionDataDO.setToAddress(inscription.getString("to"));
            final int i = txidxs.indexOf(hash);
            if (i < 0) {
                throw new RuntimeException("transaction hash 顺序有错误");
            }
            inscriptionDataDO.setTxidx(i);
            String location = inscription.getString("sat_point_on_tx");
            if (location == null || location.isEmpty()) {
                location = String.format("%s:%s:%s", inscriptionDataDO.getTxHash(),
                        inscriptionDataDO.getVout(), inscriptionDataDO.getOffset());
            }
            inscriptionDataDO.setLocation(location);
            inscriptionData.add(inscriptionDataDO);
        }
        return inscriptionData;
    }

    public InscriptionDataDO dealInscriptionData(InscriptionDataDO inscriptionData, InscriptionContent content,
                                                 Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                                 Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                                                 List<OperationHistoryDO> latestHistoryCache) {

        if (content == null) return null;
        if (inscriptionData.getInscriptionNumber() < 0) return null;

        if (inscriptionData.getIsTransfer()) {
            if (content.getOp().equals("transfer") && content.getP().equals("brc-20")) {
                return null;
            }

            final List<String> operationTypes = new ArrayList<>(ORC20_OP_TRANSFER);
            operationTypes.add(ORC20_OP_MINT);
            OperationHistoryDO history;
            final List<OperationType> transferOps = Arrays.asList(INSCRIBE_SEND, INSCRIBE_REMAINING_BALANCE,
                    INSCRIBE_MINT);
            if (ORC20_OP_UPGRADE.equals(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, OperationType.INSCRIBE_UPGRADE).
                        one();

            } else if (ORC20_OP_DEPLOY.equals(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, INSCRIBE_DEPLOY).
                        one();
            } else if (ORC20_OP_AIRDROP.equals(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, OperationType.INSCRIBE_AIRDROP).
                        one();
            } else if (ORC20_OP_SELL.contains(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, INSCRIBE_SELL).
                        one();
            } else if (ORC20_OP_PROPOSE.contains(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, INSCRIBE_PROPOSE).
                        one();
            } else if (ORC20_OP_VOTE.contains(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, INSCRIBE_VOTE).
                        one();
            } else if (ORC20_OP_BURN.contains(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, INSCRIBE_BURN).
                        one();
            } else if (ORC20_OP_LOCK.contains(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        eq(OperationHistoryDO::getType, INSCRIBE_LOCK).
                        one();
            } else if (operationTypes.contains(content.getOp())) {
                history = operationHistoryService.lambdaQuery().
                        eq(OperationHistoryDO::getInscriptionID, inscriptionData.getInscriptionID()).
                        in(OperationHistoryDO::getType, transferOps).
                        one();
            } else {
                return null;
            }
            if (history == null) {
                //铭文格式错误没有保存历史记录,交易的时候查不到
                return null;
            }
            if (inscriptionData.getFromAddress() == null) {
                inscriptionData.setFromAddress(history.getHolderAddress());
            }

            history.setHolderAddress(inscriptionData.getToAddress());
            String location = inscriptionData.getLocation();
            if (location == null || location.isEmpty()) {
                location = String.format("%s:%s:%s", inscriptionData.getTxHash(),
                        inscriptionData.getVout(), inscriptionData.getOffset());
            }
            history.setLocation(location);
            operationHistoryService.updateById(history);
            //id ticker
            TokenInfoDO tokenInfo = getTokenInfoDO(history.getTicker(), history.getOrc20Id(), latestTokenCache);

            if (tokenInfo == null) {
                log.info("{} 代币信息未找到, hash: {}", history.getTicker(), inscriptionData.getTxHash());
                log.info("处理铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return null;
            }

            if (history.getStatus() == 0) {
                //insert history
                saveHistoryAndUpdateTokenTransactions(inscriptionData, ORC20_OP_UPGRADE.equals(content.getOp()) ?
                                OperationType.UPGRADE : OperationType.TRANSFER,
                        history.getAmount(), 0,
                        tokenInfo.getOrc20Id(), history.getTicker(), "inscription is invalid",
                        tokenInfo.getInscriptionNumber(), history.getNonce(), false, tokenInfo, false,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                return null;
            }

            if (history.getType().equals(OperationType.INSCRIBE_UPGRADE)) {
                if (dealUpgrade(inscriptionData, history, tokenInfo, content, latestHistoryCache)) return null;
            } else if (history.getType().equals(INSCRIBE_DEPLOY)) {
                if (dealDeploy(inscriptionData, history, tokenInfo, latestBalanceCache, latestHistoryCache)) return null;
            } else if (history.getType().equals(OperationType.INSCRIBE_AIRDROP)) {
                if (dealAirdrop(inscriptionData, history, tokenInfo, content, latestBalanceCache, latestHistoryCache)) return null;
            } else if (history.getType().equals(OperationType.INSCRIBE_SELL)) {
                if (dealSell(inscriptionData, history, tokenInfo, latestBalanceCache, latestHistoryCache)) return null;
            } else if (history.getType().equals(INSCRIBE_PROPOSE)) {
                if (dealPropose(inscriptionData, tokenInfo, latestBalanceCache, latestHistoryCache)) return null;
            } else if (history.getType().equals(INSCRIBE_VOTE)) {
                if (dealVote(inscriptionData, tokenInfo, latestBalanceCache, latestHistoryCache)) return null;
            } else if (history.getType().equals(INSCRIBE_LOCK)) {
                if (dealLock(inscriptionData, history, tokenInfo, latestBalanceCache, latestHistoryCache)) return null;
            } else if (history.getType().equals(INSCRIBE_BURN)) {
                if (dealBurn(inscriptionData, history, tokenInfo, latestBalanceCache, latestHistoryCache)) return null;
            } else if (transferOps.contains(history.getType())) {
                if (dealTransfer(inscriptionData, history, tokenInfo, latestBalanceCache, latestHistoryCache))
                    return null;
            } else {
                return null;
            }
        } else {
            if (ORC20_OP_DEPLOY.equals(content.getOp())) {
                //返回true表明铭文不合法
                if (dealDeployInscription(inscriptionData, content, latestTokenCache, latestBalanceCache,
                        latestHistoryCache))
                    return null;

            } else if (ORC20_OP_MINT.equals(content.getOp())) {
                if (dealMintInscription(inscriptionData, content, latestTokenCache, latestBalanceCache,
                        latestHistoryCache))
                    return null;

            } else if (ORC20_OP_TRANSFER.contains(content.getOp())) {
                if (content.getP().equals("brc-20")) {
                    return null;
                }
                if (dealTransferInscription(inscriptionData, content, latestTokenCache, latestBalanceCache,
                        latestHistoryCache))
                    return null;
            } else if (ORC20_OP_UPGRADE.equals(content.getOp())) {
                if (dealUpgradeInscription(inscriptionData, content, latestTokenCache, latestHistoryCache)) return null;
            } else if (ORC20_OP_AIRDROP.equals(content.getOp())) {
                if (dealAirdropInscription(inscriptionData, content, latestTokenCache, latestHistoryCache)) return null;
            } else if (ORC20_OP_SELL.equals(content.getOp())) {
                if (dealSellInscription(inscriptionData, content, latestTokenCache, latestHistoryCache)) return null;
            } else if (ORC20_OP_PROPOSE.equals(content.getOp())) {
                if (dealProposeInscription(inscriptionData, content, latestTokenCache, latestHistoryCache)) return null;
            } else if (ORC20_OP_VOTE.equals(content.getOp())) {
                if (dealVoteInscription(inscriptionData, content, latestTokenCache, latestHistoryCache)) return null;
            } else if (ORC20_OP_LOCK.equals(content.getOp())) {
                if (dealLockInscription(inscriptionData, content, latestTokenCache, latestHistoryCache)) return null;
            } else if (ORC20_OP_BURN.equals(content.getOp())) {
                if (dealBurnInscription(inscriptionData, content, latestTokenCache, latestHistoryCache)) return null;
            }
        }
        return inscriptionData;
    }

    private boolean dealDeploy(InscriptionDataDO inscriptionData, OperationHistoryDO history, TokenInfoDO tokenInfo, Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache, List<OperationHistoryDO> latestHistoryCache) {
        final InscriptionContent content = getInscriptionContent(inscriptionData.getContentBody());

        tokenInfo.setDeployer(inscriptionData.getToAddress());

        tokenInfoService.updateById(tokenInfo);
        saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 1, tokenInfo.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                content.getN(), false, tokenInfo, true,
                history.getToAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        return false;
    }

    private Boolean dealBurn(InscriptionDataDO inscriptionData, OperationHistoryDO history, TokenInfoDO tokenInfo,
                             Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                             List<OperationHistoryDO> latestHistoryCache) {

        final InscriptionContent content = getInscriptionContent(inscriptionData.getContentBody());

        final UserTokenBalanceDO fromUserBalance =
                getUserTokenBalanceByInsNumAndAddress(history.getToAddress(),
                        tokenInfo.getInscriptionNumber(), latestBalanceCache);

        if (fromUserBalance == null) {
            log.info(" fromUserBlance is not found ,tick: {}, hash: {}, address: {}", content.getTick(),
                    inscriptionData.getTxHash(), content.getSeller());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        if (content == null) {
            return true;
        }

        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }

        if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
            BigDecimal amt;
            String to = null;
            //amt
            if (content.getAmt() == null) {
                log.info("amt is not found, tick:{}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    amt = new BigDecimal(content.getAmt());
                } catch (Exception e) {
                    log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                    || amt.compareTo(fromUserBalance.getAvailable()) > 0
            ) {
                return true;
            }


            fromUserBalance.setAvailable(fromUserBalance.getAvailable().subtract(amt));
            fromUserBalance.setBalance(fromUserBalance.getBalance().subtract(amt));
            if (fromUserBalance.getBalance().compareTo(BigDecimal.ZERO) == 0){
                tokenInfo.setHolders(tokenInfo.getHolders() - 1);
            }
            userTokenBalanceService.updateById(fromUserBalance);

            //TODO 处理to
            if (content.getBurnTo() != null) {
                to = content.getBurnTo();


                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, amt, 1, tokenInfo.getOrc20Id(),
                        tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, true,
                        history.getToAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            } else {
                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, amt, 1, tokenInfo.getOrc20Id(),
                        tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, true,
                        history.getToAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            }
        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0, tokenInfo.getOrc20Id(),
                    tokenInfo.getTicker(), "toAddress is invalid", tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        }
        return false;
    }


    private Boolean dealBurnInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                        Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                        List<OperationHistoryDO> latestHistoryCache) {
        BigDecimal amt;
        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }
        //token
        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) {
            log.info("ticker is not found , ticker:{}, hash: {}, orc20Id:{}", content.getTick(),
                    inscriptionData.getTxHash(), content.getId());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        //amt
        if (content.getAmt() == null) {
            log.info("amt is not found, tick:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                amt = new BigDecimal(content.getAmt());
            } catch (Exception e) {
                log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return true;
        }

        saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_BURN, BigDecimal.ZERO, 1,
                tokenInfo.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                content.getN(), false, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

        return false;
    }

    private Boolean dealLock(InscriptionDataDO inscriptionData, OperationHistoryDO history, TokenInfoDO tokenInfo,
                             Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                             List<OperationHistoryDO> latestHistoryCache) {
        final InscriptionContent content = getInscriptionContent(inscriptionData.getContentBody());
        final UserTokenBalanceDO fromUserBalance =
                getUserTokenBalanceByInsNumAndAddress(history.getToAddress(),
                        tokenInfo.getInscriptionNumber(), latestBalanceCache);

        if (fromUserBalance == null) {
            log.info(" fromUserBlance is not found ,tick: {}, hash: {}, address: {}", content.getTick(),
                    inscriptionData.getTxHash(), content.getSeller());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }
        if (content == null) {
            return true;
        }
        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }

        if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
            BigDecimal amt;
            String expire;
            String to;
            //amt
            if (content.getAmt() == null) {
                log.info("amt is not found, tick:{}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    amt = new BigDecimal(content.getAmt());
                } catch (Exception e) {
                    log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                    || amt.compareTo(fromUserBalance.getAvailable()) > 0
            ) {
                return true;
            }

            //expire
            if (content.getExpire() == null || content.getExpire().equalsIgnoreCase("never")) {
                expire = "never";
            } else {
                try {
                    Integer.valueOf(content.getExpire());
                    expire = content.getExpire();
                } catch (NumberFormatException e) {
                    log.info("expire: {} quo:{} is invalid , ticker:{}, hash: {}", content.getExpire(),
                            content.getQuo(), content.getTick(), inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }

            }

            //to
            if (content.getToAddress() == null) {
                log.info("expire: {} quo:{}: to is not exist , ticker:{}, hash: {}", content.getExpire(),
                        content.getQuo(), content.getTick(), inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                to = content.getToAddress();
            }

            //将余额中的amt数量的代币冻结
            fromUserBalance.setAvailable(fromUserBalance.getAvailable().subtract(amt));
            fromUserBalance.setLockCredits(fromUserBalance.getLockCredits().add(amt));
//            userTokenBalanceService.updateById(fromUserBalance);
            //保存lock
            saveLock(inscriptionData, content, id, amt, to, history.getToAddress(), 1, expire, tokenInfo);
            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 1, tokenInfo.getOrc20Id(),
                    tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0, tokenInfo.getOrc20Id(),
                    tokenInfo.getTicker(), "toAddress is invalid", tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        }
        return false;
    }

    private void saveLock(InscriptionDataDO inscriptionData, InscriptionContent content,
                          Long id, BigDecimal amt, String to, String toAddress, int status,
                          String expire, TokenInfoDO tokenInfoDO) {
        LockDO lockDO = new LockDO();
        lockDO.setAmount(amt);
        lockDO.setExpire(expire);
        lockDO.setInitialAddress(toAddress);
        lockDO.setIsActivated(1);
        lockDO.setTicker(content.getTick());
        lockDO.setTokenId(id);
        lockDO.setId(IdValueGenerator.INSTANCE.nextLong());
        lockDO.setToAddress(to);
        lockDO.setInscriptionId(inscriptionData.getInscriptionID());
        lockDO.setActiveHeight(inscriptionData.getHeight());
        lockDO.setStatus(status);
        lockDO.setTokenInscriptionId(tokenInfoDO.getInscriptionID());
        lockService.save(lockDO);
    }

    private Boolean dealLockInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                        Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                        List<OperationHistoryDO> latestHistoryCache) {
        BigDecimal amt;
        String expire;
        Integer expireNum;
        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }
        //token
        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) {
            log.info("ticker is not found , ticker:{}, hash: {}, orc20Id:{}", content.getTick(),
                    inscriptionData.getTxHash(), content.getId());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        //amt
        if (content.getAmt() == null) {
            log.info("amt is not found, tick:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                amt = new BigDecimal(content.getAmt());
            } catch (Exception e) {
                log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
//                || amt.compareTo(fromUserBalance.getAvailable()) > 0
        ) {
            return true;
        }

        //expire
        if (content.getExpire() == null) {
            expire = "never";
        } else {
            if (content.getExpire().equalsIgnoreCase("never")) {
                expire = "never";
            } else {
                try {
                    expireNum = Integer.valueOf(content.getExpire());
                    if (expireNum < 1) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.info("expire: {} is invalid , ticker:{}, hash: {}", content.getExpire(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
        }

        //to
        if (content.getToAddress() == null) {
            log.info("to is not exist , ticker:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_LOCK, BigDecimal.ZERO, 1,
                tokenInfo.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                content.getN(), false, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        return false;
    }


    public Boolean dealVote(InscriptionDataDO inscriptionData, TokenInfoDO tokenInfo,
                             Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                             List<OperationHistoryDO> latestHistoryCache) {
        final InscriptionContent content = getInscriptionContent(inscriptionData.getContentBody());
        final UserTokenBalanceDO fromUserBalance =
                getUserTokenBalanceByInsNumAndAddress(inscriptionData.getFromAddress(),
                        tokenInfo.getInscriptionNumber(), latestBalanceCache);

        if (fromUserBalance == null) {
            log.info(" fromUserBlance is not found ,tick: {}, hash: {}, address: {}", content.getTick(),
                    inscriptionData.getTxHash(), content.getSeller());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        if (content == null) {
            return true;
        }
        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }

        if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
            Integer v;
            BigDecimal amt;
            String vote;

            //v
            if (content.getV() == null) {
                log.info("v is not found , ticker:{}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    v = Integer.valueOf(content.getV());
                } catch (NumberFormatException e) {
                    log.info("v: {} is invalid , ticker:{}, hash: {}", content.getV(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            if (v <= 0) {
                return true;
            }

            //amt
            if (content.getAmt() == null) {
                log.info("amt is not found, tick:{}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    amt = new BigDecimal(content.getAmt());
                } catch (Exception e) {
                    log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                    || amt.compareTo(fromUserBalance.getBalance()) > 0
            ) {
                return true;
            }

            //vote
            if (content.getVote() == null) {
                log.info("vote is not found, tick:{}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                vote = content.getVote();
                if (!vote.equalsIgnoreCase("yes") && !vote.equalsIgnoreCase("no")) {
                    log.info("{} vote is invalid, ticker:{}, hash: {}", content.getVote(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            //获取提案
            ProposeDO proposeDO = proposeService.lambdaQuery().
                    eq(ProposeDO::getTicker, content.getTick()).
                    eq(ProposeDO::getTokenId, id).
                    eq(ProposeDO::getProposeVersion, v).
                    one();

            //若提案不存在则直接投票失败
            if (proposeDO == null) {
                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0,
                        tokenInfo.getOrc20Id(),
                        tokenInfo.getTicker(), "this propose is not exist", tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, true,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                return true;
            }
            //如果提案过期则投票失败
            String expire = proposeDO.getExpire();
            if (!expire.equalsIgnoreCase("never") &&
                    Integer.parseInt(expire) + proposeDO.getActiveHeight() < inscriptionData.getHeight()
            ) {
                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0,
                        tokenInfo.getOrc20Id(),
                        tokenInfo.getTicker(), "this propose is expired", tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, true,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                proposeDO.setStatus(3);
                proposeService.updateById(proposeDO);
                saveVoteOrder(tokenInfo, id, inscriptionData, content, v, amt, vote, proposeDO, 0);
                return true;
            }


            //判断对于此提案是否已经投过票了
            VoteDO voteDO = voteService.lambdaQuery().
                    eq(VoteDO::getTicker, content.getTick()).
                    eq(VoteDO::getTokenId, id).
                    eq(VoteDO::getStatus, 1).
                    eq(VoteDO::getProposeVersion, v).
                    eq(VoteDO::getVoteAddress, inscriptionData.getFromAddress()).
                    one();
            if (voteDO != null) {
                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0,
                        tokenInfo.getOrc20Id(),
                        tokenInfo.getTicker(), "this address is limited to vote", tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, true,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                saveVoteOrder(tokenInfo, id, inscriptionData, content, v, amt, vote, proposeDO, 0);
                return true;
            }

            //判断提案状态，若提案过期则投票失败
            if (proposeDO.getStatus() == 2) {
                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0,
                        tokenInfo.getOrc20Id(),
                        tokenInfo.getTicker(), "propose is end, can not vote", tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, true,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                saveVoteOrder(tokenInfo, id, inscriptionData, content, v, amt, vote, proposeDO, 0);
                return true;
            }
            //修改赞成或反对的投票数量
            if (vote.equalsIgnoreCase("yes")) {
                proposeDO.setVoteYes(proposeDO.getVoteYes().add(amt));
                proposeDO.setVoteYesNum(proposeDO.getVoteYesNum() + 1);
            } else {
                proposeDO.setVoteNo(proposeDO.getVoteNo().add(amt));
                proposeDO.setVoteNoNum(proposeDO.getVoteNoNum() + 1);
            }
            //修改投票总数和比例
            proposeDO.setVoteAmount(proposeDO.getVoteAmount().add(amt));
            if (proposeDO.getVoteAmount().compareTo(BigDecimal.ZERO) != 0) {
                proposeDO.setRate(proposeDO.getVoteYes().
                        divide(proposeDO.getVoteAmount(), 2, RoundingMode.HALF_UP));
            }

            //如果投票未出结果
            if (proposeDO.getResult() == 0){
                //判断投票总数是否超过quo，超过则提议完成，结束投票，未超过则继续
                if (proposeDO.getTotalAmount().compareTo(proposeDO.getVoteAmount()) <= 0) {
                    proposeDO.setStatus(2);
                    //修改投票结果
                    if (proposeDO.getVoteYes().divide(proposeDO.getVoteAmount(), 2, RoundingMode.HALF_UP).
                            multiply(new BigDecimal(100)).compareTo(new BigDecimal(proposeDO.getPass())) >= 0) {
                        proposeDO.setResult(1);
                    } else {
                        proposeDO.setResult(2);
                    }
                }
            }

            proposeService.updateById(proposeDO);
            saveVoteOrder(tokenInfo, id, inscriptionData, content, v, amt, vote, proposeDO, 1);
            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 1, tokenInfo.getOrc20Id(),
                    tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0, tokenInfo.getOrc20Id(),
                    tokenInfo.getTicker(), "toAddress is invalid", tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        }

        return false;
    }

    private void saveVoteOrder(TokenInfoDO tokenInfo, Long tokenId, InscriptionDataDO inscriptionData, InscriptionContent content,
                               Integer v, BigDecimal amt, String vote, ProposeDO proposeId, Integer status) {

        VoteDO voteDO = new VoteDO();
        voteDO.setTicker(content.getTick());
        voteDO.setTokenId(tokenId);
        voteDO.setProposeVersion(v);
        voteDO.setAmount(amt);
        voteDO.setVote(vote);
        voteDO.setMsg(content.getMsg());
        voteDO.setStatus(status);
//        voteDO.setMsg(p);
        voteDO.setProposeId(proposeId.getInscriptionId());
        voteDO.setId(IdValueGenerator.INSTANCE.nextLong());
        voteDO.setInscriptionId(inscriptionData.getInscriptionID());
        voteDO.setTokenInscriptionId(tokenInfo.getInscriptionID());
        voteDO.setChainTime(inscriptionData.getBlockTime());
        voteDO.setVoteAddress(inscriptionData.getFromAddress());
        voteService.save(voteDO);
    }

    public Boolean dealVoteInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                        Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                        List<OperationHistoryDO> latestHistoryCache) {

        Integer v;
        BigDecimal amt;
        String vote;
        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }
        //token
        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) {
            log.info("ticker is not found , ticker:{}, hash: {}, orc20Id:{}", content.getTick(),
                    inscriptionData.getTxHash(), content.getId());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }
        //v
        if (content.getV() == null) {
            log.info("v is not found , ticker:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                v = Integer.valueOf(content.getV());

            } catch (NumberFormatException e) {
                log.info("v: {} is invalid , ticker:{}, hash: {}", content.getV(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }
        if (v <= 0) {
            log.info("v <= 0 ,so it is invalid, v:{} ticker:{}, hash: {}", content.getV(), content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());

            return true;
        }


        //amt
        if (content.getAmt() == null) {
            log.info("amt is not found, tick:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                amt = new BigDecimal(content.getAmt());
            } catch (Exception e) {
                log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
//                || amt.compareTo(fromUserBalance.getAvailable()) > 0
        ) {
            return true;
        }

        //vote
        if (content.getVote() == null) {
            log.info("vote is not found, tick:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            vote = content.getVote();
            if (!vote.equalsIgnoreCase("yes") && !vote.equalsIgnoreCase("no")) {
                log.info("{} vote is invalid, ticker:{}, hash: {}", content.getVote(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }


        saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_VOTE, BigDecimal.ZERO, 1,
                tokenInfo.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                content.getN(), false, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(),
                latestHistoryCache);
        return false;
    }

    public Boolean dealPropose(InscriptionDataDO inscriptionData, TokenInfoDO tokenInfo,
                                Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                                List<OperationHistoryDO> latestHistoryCache) {
        final InscriptionContent content = getInscriptionContent(inscriptionData.getContentBody());
        final UserTokenBalanceDO fromUserBalance =
                getUserTokenBalanceByInsNumAndAddress(inscriptionData.getFromAddress(),
                        tokenInfo.getInscriptionNumber(), latestBalanceCache);

        if (fromUserBalance == null) {
            log.info(" fromUserBlance is not found ,tick: {}, hash: {}, address: {}", content.getTick(),
                    inscriptionData.getTxHash(), content.getSeller());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        if (content == null) {
            return true;
        }
        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }
        if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
            Integer v;
            Integer quo;
            Integer pass;
            String expire;
            //v
            if (content.getV() == null) {
                log.info("v is not found , ticker:{}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    v = Integer.valueOf(content.getV());
                } catch (NumberFormatException e) {
                    log.info("v: {} is invalid , ticker:{}, hash: {}", content.getV(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            if (v <= 0) {
                return true;
            }

            //若当前代币的propose已存在v，则投票失败
            List<ProposeDO> proposeDOList = proposeService.lambdaQuery().
                    eq(ProposeDO::getTicker, content.getTick()).
                    eq(ProposeDO::getTokenId, id).
                    eq(ProposeDO::getIsActived, 1).
                    list();
            for (ProposeDO proposeDO : proposeDOList) {
                if (proposeDO.getProposeVersion() == v) {
                    log.info("in this token:[ticker:{}, ord20id:{}], v :{} is exist , hash: {}", content.getTick(),
                            id, content.getV(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());

                    saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0,
                            tokenInfo.getOrc20Id(),
                            tokenInfo.getTicker(), "this proposeVersion is existed", tokenInfo.getInscriptionNumber(),
                            content.getN(), false, tokenInfo, true,
                            inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                    return true;
                }
            }
            //quo
            if (content.getQuo() == null) {
                quo = 100;
            } else {
                try {
                    quo = Integer.valueOf(content.getQuo());
                } catch (NumberFormatException e) {
                    log.info("{} quo is invalid , ticker:{}, hash: {}", content.getQuo(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            if (quo < 10 || quo > 100) {
                return true;
            }

            //pass
            if (content.getPass() == null) {
                log.info("pass is not found , ticker:{}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    pass = Integer.valueOf(content.getPass());
                } catch (NumberFormatException e) {
                    log.info("pass: {} is invalid , ticker:{}, hash: {}", content.getPass(),
                            content.getTick(), inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            BigDecimal q = new BigDecimal(quo);
            BigDecimal p = new BigDecimal(pass);
            if (pass < 5 || pass > quo || p.compareTo(q.multiply(new BigDecimal("0.5"))) < 0) {
                return true;
            }

            //expire
            if (content.getExpire() == null || content.getExpire().equalsIgnoreCase("never")) {
                expire = "never";
            } else {
                try {
                    Integer.valueOf(content.getExpire());
                    expire = content.getExpire();
                } catch (NumberFormatException e) {
                    log.info("expire: {} is invalid , ticker:{}, hash: {}", content.getExpire(),
                            content.getTick(), inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }

            }

            //msg
            //校验发起提案地址发起的提案数量是否超出限制
            List<ProposeDO> list = proposeService.lambdaQuery().
                    eq(ProposeDO::getActiveAddress, inscriptionData.getFromAddress()).
                    eq(ProposeDO::getStatus, 1).
                    list();
            if (list.size() > 9) {
                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0,
                        tokenInfo.getOrc20Id(),
                        tokenInfo.getTicker(), "this address is limited to add new propose",
                        tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, true,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                return true;
            }


            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 1, tokenInfo.getOrc20Id(),
                    tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            saveProposeOrder(id, inscriptionData, content, 1, 1,
                    tokenInfo, v, quo, expire, pass);

        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0, tokenInfo.getOrc20Id(),
                    tokenInfo.getTicker(), "toAddress is invalid", tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        }


        return false;
    }

    private void saveProposeOrder(Long id, InscriptionDataDO inscriptionData, InscriptionContent content,
                                  int isActivated, int status, TokenInfoDO tokenInfo,
                                  Integer version, Integer quo, String expire, Integer pass) {
        ProposeDO proposeDO = new ProposeDO();
        proposeDO.setId(IdValueGenerator.INSTANCE.nextLong());
        proposeDO.setTicker(content.getTick());
        proposeDO.setTokenId(id);
        proposeDO.setProposeVersion(version);
        proposeDO.setQuorum(quo);
        proposeDO.setActiveAddress(inscriptionData.getFromAddress());
        proposeDO.setExpire(expire);
        proposeDO.setIsActived(isActivated);
        proposeDO.setMsg(content.getMsg());
        proposeDO.setPass(pass);
        proposeDO.setRate(BigDecimal.ZERO);
        proposeDO.setStatus(status);
        proposeDO.setVoteYes(BigDecimal.ZERO);
        proposeDO.setVoteNo(BigDecimal.ZERO);
        proposeDO.setVoteAmount(BigDecimal.ZERO);
        proposeDO.setTotalAmount(tokenInfo.getTotalMinted().
                multiply(new BigDecimal(quo)).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP));
        proposeDO.setResult(0);
        proposeDO.setActiveHeight(inscriptionData.getHeight());
        proposeDO.setVoteYesNum(0);
        proposeDO.setVoteNoNum(0);
        proposeDO.setTokenInscriptionId(tokenInfo.getInscriptionID());
        proposeDO.setChainTime(inscriptionData.getBlockTime());
        proposeDO.setInscriptionId(inscriptionData.getInscriptionID());
        proposeDO.setTokenInscriptionNumber(tokenInfo.getInscriptionNumber());
        proposeService.save(proposeDO);
    }

    public Boolean dealProposeInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                           Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                           List<OperationHistoryDO> latestHistoryCache) {
        Integer v;
        Integer quo;
        Integer pass;

        //id
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }
        //token
        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) {
            log.info("ticker is not found , ticker:{}, hash: {}, orc20Id:{}", content.getTick(),
                    inscriptionData.getTxHash(), content.getId());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }
        //v
        if (content.getV() == null) {
            log.info("v is not found , ticker:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                v = Integer.valueOf(content.getV());
            } catch (NumberFormatException e) {
                log.info("v: {} is invalid , ticker:{}, hash: {}", content.getV(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (v <= 0) {
            return true;
        }

        //quo
        if (content.getQuo() == null) {
            quo = 100;
        } else {
            try {
                quo = Integer.valueOf(content.getQuo());
            } catch (NumberFormatException e) {
                log.info("{} quo is invalid , ticker:{}, hash: {}", content.getQuo(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }
        if (quo < 10 || quo > 100) {
            return true;
        }

        //pass
        if (content.getPass() == null) {
            log.info("pass is not found , ticker:{}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                pass = Integer.valueOf(content.getPass());
            } catch (NumberFormatException e) {
                log.info("pass: {} is invalid , ticker:{}, hash: {}", content.getPass(),
                        content.getTick(), inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (pass < 5 || pass > quo) {
            return true;
        }

        //expire
        if (content.getExpire() != null && !content.getExpire().equalsIgnoreCase("never")) {
            try {
                Integer.valueOf(content.getExpire());
            } catch (NumberFormatException e) {
                log.info("expire: {} is invalid , ticker:{}, hash: {}", content.getExpire(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        //msg
        saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_PROPOSE, BigDecimal.ZERO, 1,
                tokenInfo.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                content.getN(), false, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        return false;
    }

    private Boolean dealSellInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                        Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                        List<OperationHistoryDO> latestHistoryCache) {
        BigDecimal lim;
        BigDecimal amt;
        BigDecimal price;
        String expire;
        Integer expireNum = 1;
        String seller;
        String buyer;
        if (content == null) {
            return true;
        }
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }

        //token
        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) {
            log.info("ticker is not found , ticker:{}, hash: {}, orc20Id:{}", content.getTick(),
                    inscriptionData.getTxHash(), content.getId());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        //amt
        if (content.getAmt() == null) {
            log.info(" amount is not found ,tick: {}, hash: {}", content.getTick(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                amt = new BigDecimal(content.getAmt());
            } catch (Exception e) {
                log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }


        if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
//                || amt.compareTo(fromUserBalance.getAvailable()) > 0
        ) {
            return true;
        }
        //lim
        if (content.getLim() == null) {
            lim = amt;
        } else {
            try {
                lim = new BigDecimal(content.getLim());
                if (lim.compareTo(amt) > 0) {
                    lim = amt;
                }
            } catch (Exception e) {
                log.info("{} lim invalid:{}, hash: {}", content.getTick(), content.getLim(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }
        if (lim.signum() <= 0) {
            return true;
        }
        //price
        if (content.getPrice() == null) {
            log.info("{} price invalid:{}, hash: {}", content.getTick(), content.getPrice(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                price = new BigDecimal(content.getPrice());
            } catch (Exception e) {
                log.info("{} price invalid:{}, hash: {}", content.getTick(), content.getPrice(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }
        if (price.signum() < 0 || price.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return true;
        }
        //expire
        if (content.getExpire() == null) {
            log.info("{} expire invalid:{}, hash: {}", content.getTick(), content.getExpire(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            expire = content.getExpire();
            if (!expire.equalsIgnoreCase("never")) {
                try {
                    expireNum = Integer.parseInt(expire);
                    if (expireNum < 1) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    log.info("{} expire invalid:{}, hash: {}", content.getTick(), content.getExpire(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

        }


        //seller
        if (content.getSeller() == null) {
            log.info("{} seller invalid:{}, hash: {}", content.getTick(), content.getSeller(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            try {
                seller = content.getSeller();
                MainNetParams netParams = MainNetParams.get();
                Address.fromString(netParams, seller);
            } catch (AddressFormatException e) {
                log.info("{} seller invalid:{}, hash: {}", content.getTick(), content.getSeller(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }
        //buyer
        if (StringUtils.isEmpty(content.getBuyer())) {
            log.info("{} buyer invalid:{}, hash: {}", content.getTick(), content.getBuyer(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        } else {
            buyer = content.getBuyer();
            if (!isValidAddress(buyer)) {
                log.info("{} buyer invalid:{}, hash: {}", content.getTick(), content.getBuyer(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }

        }

        saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_SELL, amt, 1, tokenInfo.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                content.getN(), false, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

        return false;

    }

    private Long saveSellOrder(InscriptionDataDO inscriptionData, InscriptionContent content,
                               int status, TokenInfoDO tokenInfo, BigDecimal lim) {

        SellOrderDO sellOrderDO = new SellOrderDO();
        sellOrderDO.setTicker(content.getTick());
        sellOrderDO.setTokenId(tokenInfo.getOrc20Id());
        sellOrderDO.setTxId(inscriptionData.getTxHash());
        sellOrderDO.setHeight(inscriptionData.getHeight());
        sellOrderDO.setInscriptionNumber(inscriptionData.getInscriptionNumber());
        sellOrderDO.setInscriptionId(inscriptionData.getInscriptionID());
        sellOrderDO.setTokenInscriptionId(tokenInfo.getInscriptionID());
        sellOrderDO.setTokenInscriptionNumber(tokenInfo.getInscriptionNumber());
        sellOrderDO.setAmount(new BigDecimal(content.getAmt()));
        sellOrderDO.setLim(lim);
        sellOrderDO.setPrice(new BigDecimal(content.getPrice()));
        sellOrderDO.setExpire(content.getExpire());
        sellOrderDO.setSeller(content.getSeller());
        sellOrderDO.setBuyer(content.getBuyer());
        sellOrderDO.setSoldAmount(BigDecimal.ZERO);
        sellOrderDO.setStatus(status);
        sellOrderDO.setChainTime(inscriptionData.getBlockTime());
        sellOrderDO.setId(IdValueGenerator.INSTANCE.nextLong());
        sellOrderDO.setActivationAddress(inscriptionData.getFromAddress());
        sellOrderService.save(sellOrderDO);
        return sellOrderDO.getId();
    }

    private Boolean isValidAddress(String buyer) {
        String pattern = "\\[.*\\]";
        if (Pattern.matches(pattern, buyer)) {
            return true;
        }
        for (int i = 0; i < buyer.length(); i++) {
            char c = buyer.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private Boolean dealSell(InscriptionDataDO inscriptionData, OperationHistoryDO history,
                             TokenInfoDO tokenInfo, Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                             List<OperationHistoryDO> latestHistoryCache) {
        final InscriptionContent content = getInscriptionContent(inscriptionData.getContentBody());
        final UserTokenBalanceDO fromUserBalance =
                getUserTokenBalanceByInsNumAndAddress(inscriptionData.getFromAddress(),
                        tokenInfo.getInscriptionNumber(), latestBalanceCache);
        if (fromUserBalance == null) {
            log.info(" fromUserBlance is not found ,tick: {}, hash: {}, address: {}", content.getTick(),
                    inscriptionData.getTxHash(), content.getSeller());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }
        if (content == null) {
            return true;
        }
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }
        if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
            BigDecimal lim;
            BigDecimal amt;
            BigDecimal price;
            String expire;
            Integer expireNum = 1;
            String seller;
            String buyer;
            //amt

            if (content.getAmt() == null) {
                log.info(" amount is not found ,tick: {}, hash: {}", content.getTick(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    amt = new BigDecimal(content.getAmt());
                } catch (Exception e) {
                    log.info("{} amt is invalid, ticker: {}, hash: {}", content.getAmt(), content.getTick(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            if (amt.signum() <= 0 || amt.compareTo(tokenInfo.getMaxNumber()) > 0
                    || amt.compareTo(fromUserBalance.getAvailable()) > 0
            ) {
                return true;
            }
            //lim
            if (content.getLim() == null) {
                lim = amt;
            } else {
                try {
                    lim = new BigDecimal(content.getLim());
                    if (lim.compareTo(amt) > 0) {
                        lim = amt;
                    }
                } catch (Exception e) {
                    log.info("{} lim invalid:{}, hash: {}", content.getTick(), content.getLim(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            if (lim.signum() <= 0) {
                return true;
            }
            tokenInfo.setLimitNumber(lim);

            //price
            if (content.getPrice() == null) {
                log.info("{} price invalid:{}, hash: {}", content.getTick(), content.getPrice(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    price = new BigDecimal(content.getPrice());
                } catch (Exception e) {
                    log.info("{} price invalid:{}, hash: {}", content.getTick(), content.getPrice(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            if (price.signum() < 0 || price.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                return true;
            }
            //expire
            if (content.getExpire() == null) {
                log.info("{} expire invalid:{}, hash: {}", content.getTick(), content.getExpire(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                expire = content.getExpire();
                if (!expire.equalsIgnoreCase("never")) {
                    try {
                        expireNum = Integer.parseInt(expire);
                        if (expireNum < 1) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        log.info("{} expire invalid:{}, hash: {}", content.getTick(), content.getExpire(),
                                inscriptionData.getTxHash());
                        log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                                inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                        return true;
                    }
                }

            }


            //seller
            if (content.getSeller() == null) {
                log.info("{} seller invalid:{}, hash: {}", content.getTick(), content.getSeller(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                try {
                    seller = content.getSeller();
                    NetworkParameters netParams;
                    if (ProfileEnum.PROD.isActive()) {
                        //正式
                        netParams = MainNetParams.get();
                    } else if (ProfileEnum.TEST.isActive()) {
                        netParams = MainNetParams.get();
                    } else {
                        List<String> activeProfiles = Arrays.asList(SpringUtil.getActiveProfiles());
                        if (activeProfiles.contains("dev")) {
                            //dev
                            netParams = TestNet3Params.get();
                        } else if (activeProfiles.contains("prod1")) {
                            netParams = MainNetParams.get();
                        } else {
                            netParams = MainNetParams.get();
                        }
                    }
                    Address.fromString(netParams, seller);
                } catch (AddressFormatException e) {
                    log.info("{} seller invalid:{}, hash: {}", content.getTick(), content.getSeller(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            //buyer
            if (StringUtils.isEmpty(content.getBuyer())) {
                log.info("{} buyer invalid:{}, hash: {}", content.getTick(), content.getBuyer(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            } else {
                buyer = content.getBuyer();
                if (!isValidAddress(buyer)) {
                    log.info("{} buyer invalid:{}, hash: {}", content.getTick(), content.getBuyer(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            //保存卖单，如果买家地址不是anyone，则保存白名单
            Long sellOrderId = saveSellOrder(inscriptionData, content, 0, tokenInfo, lim);
            if (!buyer.equalsIgnoreCase("anyone")) {
                saveSellOrderWhiteList(buyer, lim, sellOrderId);
            }
            //修改冻结余额以及credits余额
            if (fromUserBalance.getLockCredits() == null) {
                fromUserBalance.setLockCredits(amt);
            } else {
                fromUserBalance.setLockCredits(fromUserBalance.getLockCredits().add(amt));
            }
            fromUserBalance.setAvailable(fromUserBalance.getAvailable().subtract(amt));
            //TODO
            userTokenBalanceService.updateById(fromUserBalance);

            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 1, id,
                    tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                    history.getNonce(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER, BigDecimal.ZERO, 0, id,
                    tokenInfo.getTicker(), "toAddress is invalid",
                    tokenInfo.getInscriptionNumber(),
                    history.getNonce(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        }
        return false;
    }

    private void saveSellOrderWhiteList(String buyer, BigDecimal lim,
                                        Long sellOrderId) {
        try {
            JSONArray buyerArry = JSONArray.parseArray(buyer);
            List<String> list = buyerArry.toJavaList(String.class);
            Map<String, List<String>> collect = list.stream().collect(Collectors.
                    groupingBy(o -> o, Collectors.toList()));
            collect.forEach((key, value) -> {
                SellOrderWhiteListDO sellOrderWhiteListDO = new SellOrderWhiteListDO();
                sellOrderWhiteListDO.setAddress(key);
                sellOrderWhiteListDO.setLim(lim.multiply(BigDecimal.valueOf(value.size())));
                sellOrderWhiteListDO.setAmountFilled(BigDecimal.ZERO);
                sellOrderWhiteListDO.setSellOrderId(sellOrderId);
                sellOrderWhiteListDO.setId(IdValueGenerator.INSTANCE.nextLong());
                sellOrderWhiteListService.save(sellOrderWhiteListDO);
            });
        } catch (Exception e) {
            SellOrderWhiteListDO sellOrderWhiteListDO = new SellOrderWhiteListDO();
            sellOrderWhiteListDO.setAddress(buyer);
            sellOrderWhiteListDO.setLim(lim);
            sellOrderWhiteListDO.setAmountFilled(BigDecimal.ZERO);
            sellOrderWhiteListDO.setSellOrderId(sellOrderId);
            sellOrderWhiteListDO.setId(IdValueGenerator.INSTANCE.nextLong());
            sellOrderWhiteListService.save(sellOrderWhiteListDO);
        }
    }

    private Boolean dealAirdropInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                           Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                           List<OperationHistoryDO> latestHistoryCache) {
        BigDecimal amt;
        BigDecimal lim;
        Long id = getId(content, inscriptionData);
        if (id == null) return true;

        //token
        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) {
            log.info("ticker is not found , ticker:{}, hash: {}, orc20Id:{}", content.getTick(),
                    inscriptionData.getTxHash(), content.getId());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }
        //amt
        try {
            amt = new BigDecimal(content.getAmt());
        } catch (Exception e) {
            log.info("{} amt invalid:{}, hash: {}", content.getTick(), content.getAmt(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return true;
        }
        //lim
        if (content.getLim() == null) {
            lim = tokenInfo.getLimitNumber();
        } else {
            try {
                lim = new BigDecimal(content.getLim());
            } catch (Exception e) {
                log.info("{} lim invalid:{}, hash: {}", content.getTick(), content.getMax(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (lim.signum() <= 0 || lim.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 || lim.compareTo(amt) > 0) {
            return true;
        }


        saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.INSCRIBE_AIRDROP, BigDecimal.ZERO, 2,
                tokenInfo.getOrc20Id(), tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(), content.getN()
                , false, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        return false;
    }


    private Boolean dealAirdrop(InscriptionDataDO inscriptionData, OperationHistoryDO history, TokenInfoDO tokenInfo,
                                InscriptionContent content,
                                Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                                List<OperationHistoryDO> latestHistoryCache) {

        log.info("fromAddress{}", inscriptionData.getFromAddress());
        log.info("InscriptionNumber{}", inscriptionData.getInscriptionNumber());
        final UserTokenBalanceDO fromUserBalance =
                getUserTokenBalanceByInsNumAndAddress(inscriptionData.getFromAddress(),
                        tokenInfo.getInscriptionNumber(), latestBalanceCache);


        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }

        if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
            BigDecimal lim;
            BigDecimal amt;
            //amt
            try {
                amt = new BigDecimal(content.getAmt());
            } catch (Exception e) {
                log.info("{} amt invalid:{}, hash: {}", content.getTick(), content.getAmt(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }

            if (amt.signum() <= 0 || amt.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                    || amt.compareTo(fromUserBalance.getAvailable()) > 0
            ) {
                return true;
            }
            //lim
            if (content.getLim() == null) {
                lim = tokenInfo.getLimitNumber();
            } else {
                try {
                    lim = new BigDecimal(content.getLim());
                } catch (Exception e) {
                    log.info("{} lim invalid:{}, hash: {}", content.getTick(), content.getMax(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }
            if (lim.signum() <= 0 || lim.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
                    || lim.compareTo(amt) > 0) {
                return true;
            }
            tokenInfo.setLimitNumber(lim);
            //转账
            try {
                List<String> toAddressList = content.getTo();
                BigDecimal addressTransNumber = new BigDecimal(0);
                for (String toAddress : toAddressList) {
                    //地址校验

                    try {
                        NetworkParameters netParams;
                        if (ProfileEnum.PROD.isActive()) {
                            //正式
                            netParams = MainNetParams.get();
                        } else if (ProfileEnum.TEST.isActive()) {
                            netParams = MainNetParams.get();
                        } else {
                            List<String> activeProfiles = Arrays.asList(SpringUtil.getActiveProfiles());
                            if (activeProfiles.contains("dev")) {
                                //dev
                                netParams = TestNet3Params.get();
                            } else if (activeProfiles.contains("prod1")) {
                                netParams = MainNetParams.get();
                            } else {
                                netParams = MainNetParams.get();
                            }
                        }
                        Address.fromString(netParams, toAddress);

                    } catch (AddressFormatException e) {
                        log.info("地址：{}无效", toAddress);
                        continue;
                    }
                    amt = amt.subtract(lim);
                    addressTransNumber = addressTransNumber.add(lim);

                    insertOrUpdateBalance(toAddress, history.getTicker(),
                            history.getOrc20Id(), lim, BigDecimal.ZERO,
                            lim, tokenInfo, false, latestBalanceCache);
                    saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER,
                            lim, 1, id,
                            tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                            history.getNonce(), false, tokenInfo, false,
                            inscriptionData.getFromAddress(), toAddress, latestHistoryCache);
                    if (amt.compareTo(lim) < 0) {
                        break;
                    }
                }
                fromUserBalance.setAvailable(fromUserBalance.getAvailable().subtract(addressTransNumber));
                fromUserBalance.setBalance(fromUserBalance.getBalance().subtract(addressTransNumber));
                if (fromUserBalance.getBalance().compareTo(BigDecimal.ZERO) == 0){
                    tokenInfo.setHolders(tokenInfo.getHolders() - 1);
                }

            } catch (Exception e) {
                log.info("{} airdrop invalid:{}, hash: {}", content.getTick(), content.getAmt(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;

            }

            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.TRANSFER, BigDecimal.ZERO, 1, id,
                    tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                    history.getNonce(), false, tokenInfo, false,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

            //把之前待生效的铭文状态改成1
            history.setStatus(1);
            operationHistoryService.updateById(history);
        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.TRANSFER, BigDecimal.ZERO, 0, id,
                    tokenInfo.getTicker(), "toAddress is invalid",
                    tokenInfo.getInscriptionNumber(),
                    history.getNonce(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        }
        return false;
    }


    private Boolean dealTransfer(InscriptionDataDO inscriptionData, OperationHistoryDO history, TokenInfoDO tokenInfo
            , Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                                 List<OperationHistoryDO> latestHistoryCache) {
        //get from user's token to update

        if (history.getTransferable()) {
            if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
                final UserTokenBalanceDO fromUserBalance =
                        getUserTokenBalanceByInsNumAndAddress(inscriptionData.getFromAddress(),
                                tokenInfo.getInscriptionNumber(), latestBalanceCache);
                fromUserBalance.setAvailable(fromUserBalance.getAvailable().add(history.getAmount()));
                fromUserBalance.setTransferableBalance(fromUserBalance.getTransferableBalance().subtract(history.getAmount()));
                if (fromUserBalance.getTransferableBalance().compareTo(BigDecimal.ZERO) < 0 ||
                        fromUserBalance.getBalance().compareTo(BigDecimal.ZERO) < 0 ||
                        fromUserBalance.getAvailable().compareTo(BigDecimal.ZERO) < 0) {
                    throw new RuntimeException(fromUserBalance.getAddress() + ":" + fromUserBalance.getTicker() + ":" + fromUserBalance.getOrc20Id() + ":");
                }

            } else {
                insertOrUpdateBalance(inscriptionData.getFromAddress(), history.getTicker(),
                        history.getOrc20Id(), BigDecimal.ZERO, history.getAmount().negate(),
                        history.getAmount().negate(), tokenInfo, false, latestBalanceCache);
                //resolve address by explore and update to address balance
                insertOrUpdateBalance(inscriptionData.getToAddress(), history.getTicker(), history.getOrc20Id(),
                        BigDecimal.ZERO, history.getAmount(), history.getAmount(), tokenInfo, false,
                        latestBalanceCache);

            }

            //insert history
            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.TRANSFER, history.getAmount(), 1,
                    tokenInfo.getOrc20Id(), history.getTicker(), null,
                    tokenInfo.getInscriptionNumber(), history.getNonce(), false, tokenInfo, false,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            return false;
        }
        return true;


    }

    private UserTokenBalanceDO getUserTokenBalanceByInsNumAndAddress(String address, Long insNum,
                                                                     Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache) {

        if (latestBalanceCache.containsKey(insNum)) {
            if (latestBalanceCache.get(insNum).containsKey(address)) {
                return latestBalanceCache.get(insNum).get(address);
            } else {
                return getUserTokenBalanceByDb(insNum, address, latestBalanceCache);
            }
        } else {
            return getUserTokenBalanceByDb(insNum, address, latestBalanceCache);
        }

    }

    @Nullable
    private UserTokenBalanceDO getUserTokenBalanceByDb(Long insNum, String address, Map<Long, Map<String,
            UserTokenBalanceDO>> latestBalanceCache) {
        final UserTokenBalanceDO balanceDO = userTokenBalanceService.lambdaQuery()
                .eq(UserTokenBalanceDO::getInscriptionNumber, insNum)
                .eq(UserTokenBalanceDO::getAddress, address)
                .one();
        if (balanceDO != null) {
            Map<String, UserTokenBalanceDO> map;
            if (latestBalanceCache.containsKey(insNum)) {
                map = latestBalanceCache.get(insNum);
                map.put(address, balanceDO);
            } else {
                map = new HashMap<>();
            }
            map.put(address, balanceDO);
            balanceDO.setInsert(false);
            latestBalanceCache.put(insNum, map);
        }
        return balanceDO;
    }

    private boolean dealUpgrade(InscriptionDataDO inscriptionData, OperationHistoryDO history, TokenInfoDO tokenInfo,
                                InscriptionContent content,
                                List<OperationHistoryDO> latestHistoryCache) {


        if (!tokenInfo.getUpgradable()) {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.UPGRADE, BigDecimal.ZERO, 0,
                    tokenInfo.getOrc20Id(), tokenInfo.getTicker(), "token is not upgradeable",
                    tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, false,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            history.setStatus(0);
            history.setErrorMsg("token is not upgradeable");
            operationHistoryService.updateById(history);
            return true;
        }
        Long id = getId(content, inscriptionData);
        if (id == null) {
            return true;
        }
        if (VALIDATION_ADDRESS.contains(inscriptionData.getToAddress())) {
            //校验fromAddress是否是代币部署者
            if (!inscriptionData.getFromAddress().equals(tokenInfo.getDeployer())) {
                saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.UPGRADE, BigDecimal.ZERO, 0,
                        tokenInfo.getOrc20Id(), tokenInfo.getTicker(), "from address is not deployer",
                        tokenInfo.getInscriptionNumber(),
                        content.getN(), false, tokenInfo, false,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                history.setStatus(0);
                history.setErrorMsg("from address is not deployer");
                operationHistoryService.updateById(history);
                return true;
            }
            BigDecimal max;
            BigDecimal lim;
            //max
            if (content.getMax() == null) {
                max = tokenInfo.getMaxNumber();
            } else {
                try {
                    max = new BigDecimal(content.getMax());
                } catch (Exception e) {
                    log.info("{} max invalid:{}, hash: {}", content.getTick(), content.getMax(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            if (max.signum() <= 0 || max.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0
            ) {
                return true;
            }
            if (max.compareTo(tokenInfo.getTotalMinted()) < 0) {
                final String format = String.format("%s upgrade max invalid:%s, max less than minted:%s",
                        content.getTick(),
                        content.getMax(), tokenInfo.getTotalMinted());
                saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.UPGRADE, BigDecimal.ZERO, 0, id,
                        tokenInfo.getTicker(), format, tokenInfo.getInscriptionNumber(), history.getNonce(),
                        false, tokenInfo, false,
                        inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
                return true;
            }
            tokenInfo.setMaxNumber(max);
            //lim
            if (content.getLim() == null) {
                lim = BigDecimal.ONE;
            } else {
                try {
                    if (!content.getLim().equalsIgnoreCase("self")) {
                        lim = new BigDecimal(content.getLim());
                    } else {
                        lim = tokenInfo.getMaxNumber().subtract(tokenInfo.getTotalMinted());
                        tokenInfo.setIsSelf(true);
                    }

                } catch (Exception e) {
                    log.info("{} lim invalid:{}, hash: {}", content.getTick(), content.getMax(),
                            inscriptionData.getTxHash());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
            }

            if (lim.signum() <= 0 || lim.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 || lim.compareTo(max) > 0) {
                return true;
            }

            tokenInfo.setLimitNumber(lim);
            //ug
            if (content.getUg() != null) {
                if (!"true".equals(content.getUg()) && !"false".equals(content.getUg())) {
                    log.info("{}:{} ug invalid:{}", content.getTick(), content.getId(), content.getMax());
                    log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                            inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                    return true;
                }
                tokenInfo.setUpgradable(Boolean.valueOf(content.getUg()));
            }

            //v
            if (content.getV() != null) {
                tokenInfo.setTokenVersion(content.getV());
            }

            //msg
            if (content.getMsg() != null) {
                tokenInfo.setMessage(content.getMsg());
            }
            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.UPGRADE, BigDecimal.ZERO, 1, id,
                    tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                    history.getNonce(), false, tokenInfo, false,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

            tokenInfo.setProgress(tokenInfo.getTotalMinted().divide(tokenInfo.getMaxNumber(), 10, RoundingMode.DOWN));

            //把之前待生效的铭文状态改成1
            history.setStatus(1);
            operationHistoryService.updateById(history);

        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.UPGRADE, BigDecimal.ZERO, 0, id,
                    tokenInfo.getTicker(), "toAddress is not " +
                            "bc1pgha2vs4m4d70aw82qzrhmg98yea4fuxtnf7lpguez3z9cjtukpssrhakhl",
                    tokenInfo.getInscriptionNumber(),
                    history.getNonce(), false, tokenInfo, false,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        }
        return false;
    }

    private boolean dealTransferInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                            Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                            Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                                            List<OperationHistoryDO> latestHistoryCache) {
        Long id = getId(content, inscriptionData);
        if (id == null) return true;
        final TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);

        if (tokenInfo == null) {
            //Tick未部署
            log.info("mint invalid {}:{} is not deploy  hash:{}", content.getTick(), content.getId(),
                    inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }
        //check amt
        BigDecimal amt;

        try {
            amt = new BigDecimal(content.getAmt());
        } catch (Exception e) {
            log.info("{} amt invalid:{}", content.getTick(), content.getAmt());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        if (amt.signum() <= 0 || amt.compareTo(tokenInfo.getMaxNumber()) > 0) {
            return true;
        }

        UserTokenBalanceDO tokenBalance =
                getUserTokenBalanceByInsNumAndAddress(inscriptionData.getToAddress(),
                        tokenInfo.getInscriptionNumber(), latestBalanceCache);

        if (tokenBalance == null || tokenBalance.getAvailable().compareTo(amt) < 0) {
            //insert invalid history
            saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_SEND, amt, 0,
                    tokenInfo.getOrc20Id(), tokenInfo.getTicker(), "user available is less than inscribe send" +
                            " amount", tokenInfo.getInscriptionNumber(), null, false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        } else {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_SEND, amt, 1,
                    tokenInfo.getOrc20Id(), tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                    null, true, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            tokenBalance.setAvailable(tokenBalance.getAvailable().subtract(amt));
            tokenBalance.setTransferableBalance(tokenBalance.getTransferableBalance().add(amt));
            //userTokenBalanceService.updateById(tokenBalance);
        }
        return false;
    }

    private boolean dealUpgradeInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                           Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                           List<OperationHistoryDO> latestHistoryCache) {

        BigDecimal max;
        BigDecimal lim;

        Long id = getId(content, inscriptionData);
        if (id == null) return true;

        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) return true;


        if (!tokenInfo.getUpgradable()) {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, INSCRIBE_UPGRADE, BigDecimal.ZERO, 0,
                    tokenInfo.getOrc20Id(), tokenInfo.getTicker(), "token is not upgradeable",
                    tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            return true;
        }

        //max
        if (content.getMax() == null) {
            max = tokenInfo.getMaxNumber();
        } else {
            try {
                max = new BigDecimal(content.getMax());
            } catch (Exception e) {
                log.info("{} max invalid:{}, hash: {}", content.getTick(), content.getMax(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (max.signum() <= 0 || max.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return true;
        }
        //lim
        if (content.getLim() == null) {
            lim = BigDecimal.ONE;
        } else {
            try {
                if (!content.getLim().equalsIgnoreCase("self")) {
                    lim = new BigDecimal(content.getLim());
                    if (lim.signum() <= 0 || lim.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 || lim.compareTo(max) > 0) {
                        return true;
                    }
                }
            } catch (Exception e) {
                log.info("{} lim invalid:{}, hash: {}", content.getTick(), content.getMax(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }


        //ug
        if (content.getUg() != null) {
            if (!"true".equals(content.getUg()) && !"false".equals(content.getUg())) {
                log.info("{}:{} ug invalid:{}, hash: {}", content.getTick(), content.getId(), content.getMax(),
                        inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.INSCRIBE_UPGRADE, BigDecimal.ZERO, 2,
                tokenInfo.getOrc20Id(), tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(), content.getN()
                , false, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
        return false;
    }

    private boolean dealMintInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                        Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                        Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                                        List<OperationHistoryDO> latestHistoryCache) {
        Long id = getId(content, inscriptionData);
        if (id == null) return true;

        //从缓存中取
        TokenInfoDO tokenInfo = getTokenInfoDO(content.getTick(), id, latestTokenCache);
        if (tokenInfo == null) return true;

        BigDecimal amt;

        try {
            amt = new BigDecimal(content.getAmt());
        } catch (Exception e) {
            log.info("{} max invalid:{}, hash: {}", content.getTick(), content.getMax(), inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        if (amt.signum() <= 0 || amt.compareTo(tokenInfo.getLimitNumber()) > 0) {
            return true;
        }

        //如果只有部署者可以mint但from地址不是部署者地址则mint失败
        if (tokenInfo.getIsSelf() && !inscriptionData.getToAddress().equals(tokenInfo.getDeployer())) {
            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.INSCRIBE_MINT, amt, 0,
                    tokenInfo.getOrc20Id(), tokenInfo.getTicker(), "only deployer can mint",
                    tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            return true;
        }

        BigDecimal totalMint = tokenInfo.getTotalMinted().add(amt);
        if (totalMint.compareTo(tokenInfo.getMaxNumber()) > 0) {
            //step 2
            //invalid
            //insert invalid history
            final String format = "totalMinted is greater than Max Number";
            saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.INSCRIBE_MINT, amt, 0,
                    tokenInfo.getOrc20Id(), tokenInfo.getTicker(), format, tokenInfo.getInscriptionNumber(),
                    content.getN(), false, tokenInfo, true,
                    inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);
            return true;
        } else {

            if (totalMint.compareTo(tokenInfo.getMaxNumber()) == 0) {
                tokenInfo.setCompleteHeight(inscriptionData.getHeight());
                tokenInfo.setCompleteBlockTime(inscriptionData.getBlockTime());
                tokenInfo.setInscriptionNumberEnd(inscriptionData.getInscriptionNumber());
            }
            tokenInfo.setTotalMinted(totalMint);
            tokenInfo.setProgress(totalMint.divide(tokenInfo.getMaxNumber(), 6, RoundingMode.DOWN));
        }
        //step 2
        //insert history
        saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.INSCRIBE_MINT, amt, 1,
                tokenInfo.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(), null,
                true, tokenInfo, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

        //insert or update balance
        insertOrUpdateBalance(inscriptionData.getToAddress(), tokenInfo.getTicker(), id, BigDecimal.ZERO, amt, amt,
                tokenInfo, false, latestBalanceCache);
        return false;
    }

    @Nullable
    public TokenInfoDO getTokenInfoDO(String ticker, Long id, Map<String, Map<Long, TokenInfoDO>> latestTokenCache) {
        TokenInfoDO tokenInfo = null;

        Map<Long, TokenInfoDO> longTokenInfoDOMap = latestTokenCache.get(ticker.toLowerCase(Locale.ROOT));
        if (longTokenInfoDOMap != null) {
            tokenInfo = longTokenInfoDOMap.get(id);
        }

        return tokenInfo;
    }

    @Nullable
    public Long getId(InscriptionContent content, InscriptionDataDO inscriptionData) {
        long id;
        //id
        if (content.getId() != null) {
            try {
                id = Long.parseLong(content.getId());
            } catch (Exception e) {
                log.info("ProcessUpdateLatestORC20 mint, but id invalid. ticker: {}, id: {}, content{}",
                        content.getTick(), content.getId(), inscriptionData);
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return null;
            }

        } else {
            log.info("ProcessUpdateLatestORC20 mint, but id invalid. ticker: {}, id: {}, content{}",
                    content.getTick(), content.getId(), inscriptionData);
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return null;
        }
        return id;
    }

    private boolean dealDeployInscription(InscriptionDataDO inscriptionData, InscriptionContent content,
                                          Map<String, Map<Long, TokenInfoDO>> latestTokenCache,
                                          Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                                          List<OperationHistoryDO> latestHistoryCache) {
        int dec;
        BigDecimal max;
        BigDecimal lim;
        long id;
        Boolean isSelf = false;
        //dec
        try {
            dec = Integer.parseInt(content.getDec());
            if (dec > 18) {
                log.info("ProcessUpdateLatestORC20 deploy, but dec invalid. ticker: {}, dec: {}, hash: {}",
                        content.getTick(), content.getDec(), inscriptionData.getTxHash());
                log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        } catch (Exception e) {
            log.info("ProcessUpdateLatestORC20 deploy, but dec invalid. ticker: {}, dec: {}, hash: {}",
                    content.getTick(), content.getDec(), inscriptionData.getTxHash());
            log.info("处理orc20铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        //max
        if (content.getMax() == null) {
            max = new BigDecimal(Long.MAX_VALUE);
        } else {
            try {
                max = new BigDecimal(content.getMax());
            } catch (Exception e) {
                log.info("{} max invalid:{}, hash: {}", content.getTick(), content.getMax(),
                        inscriptionData.getTxHash());
                log.info("处理铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }
        }

        if (max.signum() <= 0 || max.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return true;
        }

        //lim
        try {
            if (content.getLim() == null) {
                content.setLim("1");
                lim = BigDecimal.ONE;
            } else {
                if (content.getLim().equalsIgnoreCase("self")) {
                    isSelf = true;
                    lim = max;
                } else {
                    lim = new BigDecimal(content.getLim());
                }
            }
        } catch (Exception e) {
            log.info("{} lim invalid:{}, hash: {}", content.getTick(), content.getLim(), inscriptionData.getTxHash());
            log.info("处理铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        if (lim.signum() <= 0 || lim.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 || lim.compareTo(max) > 0) {
            return true;
        }

        //ug
        if (content.getUg() == null) {
            content.setUg("true");
        }

        if (!"true".equals(content.getUg()) && !"false".equals(content.getUg())) {
            log.info("{}:{} ug invalid:{}, hash: {}", content.getTick(), content.getId(), content.getMax(),
                    inscriptionData.getTxHash());
            log.info("处理铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        //wp
        if (content.getWp() == null) {
            content.setWp("false");
        }
        if (!"true".equals(content.getWp()) && !"false".equals(content.getWp())) {
            log.info("{}:{} ug invalid:{}, hash: {}", content.getTick(), content.getId(), content.getMax(),
                    inscriptionData.getTxHash());
            return true;
        }

        //todo 可以不填,填錯了就非法
        try {
            if (content.getId() != null) {
                id = Long.parseLong(content.getId());

            } else {
                id = 1;
            }

            TokenInfoDO tokenInfoDO = getTokenInfoDO(content.getTick(), id, latestTokenCache);

            if (tokenInfoDO != null) {
                log.info("ProcessUpdateLatestORC20 deploy, but id is exist. ticker: {}, id: {}, height: " +
                                "{}, " + "hash: {}",
                        content.getTick(), content.getId(), inscriptionData.getHeight(),
                        inscriptionData.getTxHash());
                log.info("处理铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                        inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
                return true;
            }

        } catch (Exception e) {
            log.info("ProcessUpdateLatestORC20 deploy, but id invalid. ticker: {}, id: {}, hash: {}",
                    content.getTick(), content.getId(), inscriptionData.getTxHash());
            log.info("处理铭文失败:{}:{}:  是否转账:  {}", inscriptionData.getInscriptionID(),
                    inscriptionData.getContentBody(), inscriptionData.getIsTransfer());
            return true;
        }

        //save tokenInfo
        TokenInfoDO tokenInfoDO = new TokenInfoDO();
        tokenInfoDO.setTicker(content.getTick());
        tokenInfoDO.setMaxNumber(max);
        tokenInfoDO.setName(content.getName());
        tokenInfoDO.setOrc20Id(id);
        tokenInfoDO.setLimitNumber(lim);
        tokenInfoDO.setTotalMinted(BigDecimal.ZERO);
        tokenInfoDO.setTokenDecimal(dec);
        tokenInfoDO.setTxId(inscriptionData.getTxHash());
        tokenInfoDO.setInscriptionID(inscriptionData.getInscriptionID());
        tokenInfoDO.setInscriptionNumber(inscriptionData.getInscriptionNumber());
        tokenInfoDO.setVout(inscriptionData.getVout());
        tokenInfoDO.setSatoshi(inscriptionData.getSatoshi());
        tokenInfoDO.setPkScript(inscriptionData.getPkScript());
        tokenInfoDO.setDeployer(inscriptionData.getToAddress());
        tokenInfoDO.setCreateIdxKey(inscriptionData.getCreateIdxKey());
        tokenInfoDO.setDeployHeight(inscriptionData.getHeight());
        tokenInfoDO.setDeployTimes(inscriptionData.getBlockTime());
        tokenInfoDO.setId(IdValueGenerator.INSTANCE.nextLong());
        tokenInfoDO.setUpgradable(Boolean.valueOf(content.getUg()));
        tokenInfoDO.setMigrationWrapper(Boolean.valueOf(content.getWp()));
        tokenInfoDO.setTokenVersion(content.getV());
        tokenInfoDO.setMessage(content.getMsg());
        tokenInfoDO.setTransactions(0);
        tokenInfoDO.setHolders(0);
        tokenInfoDO.setProgress(BigDecimal.ZERO);
        tokenInfoDO.setVersion(0L);
        tokenInfoDO.setIsSelf(isSelf);
        if ("orc-20".equals(content.getP()) || "orc20".equals(content.getP())) {
            tokenInfoDO.setProtocol("orc-20");
        } else {
            tokenInfoDO.setProtocol(content.getP());
        }
        tokenInfoDO.setInsert(true);


        if (latestTokenCache.containsKey(tokenInfoDO.getTicker().toLowerCase(Locale.ROOT))) {
            final Map<Long, TokenInfoDO> map = latestTokenCache.get(tokenInfoDO.getTicker().toLowerCase(Locale.ROOT));
            map.put(tokenInfoDO.getOrc20Id(), tokenInfoDO);
        } else {
            Map<Long, TokenInfoDO> map = new HashMap<>();
            map.put(tokenInfoDO.getOrc20Id(), tokenInfoDO);
            latestTokenCache.put(tokenInfoDO.getTicker().toLowerCase(Locale.ROOT), map);
        }

        //save history
        saveHistoryAndUpdateTokenTransactions(inscriptionData, OperationType.INSCRIBE_DEPLOY, BigDecimal.ZERO, 1, id,
                tokenInfoDO.getTicker(), null, tokenInfoDO.getInscriptionNumber(),
                content.getN(), false, tokenInfoDO, true,
                inscriptionData.getFromAddress(), inscriptionData.getToAddress(), latestHistoryCache);

        //create balance
        insertOrUpdateBalance(inscriptionData.getToAddress(), tokenInfoDO.getTicker(),
                tokenInfoDO.getOrc20Id(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, tokenInfoDO,
                true, latestBalanceCache);
        return false;
    }

    public InscriptionContent getInscriptionContent(String contentBody) {
        InscriptionContent content = new InscriptionContent();
        JSONObject jsonObject;
        try {
            //校验json格式
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.readTree(contentBody);

            jsonObject = JSONObject.parseObject(contentBody);
        } catch (Exception e) {
            return null;
        }
        if (jsonObject == null) {
            return null;
        }

        content.setP(jsonObject.getString("p"));
        content.setOp(jsonObject.getString("op"));
        if (content.getOp() == null) {
            return null;
        }
        content.setTick(jsonObject.getString("tick"));
        if (content.getTick() == null || content.getTick().length() > 100) {
            return null;
        }
        content.setName(jsonObject.getString("name"));
        content.setUg(jsonObject.getString("ug"));

        if (jsonObject.getString("id") == null &&
                Arrays.asList(ORC20_OP_DEPLOY, ORC20_OP_MINT).contains(content.getOp())) {
            content.setId("1");
        } else {
            content.setId(jsonObject.getString("id"));
        }

        content.setWp(jsonObject.getString("wp"));
        content.setV(jsonObject.getString("v"));
        content.setMsg(jsonObject.getString("msg"));

        content.setMax(jsonObject.getString("max"));
        content.setAmt(jsonObject.getString("amt"));
        content.setN(jsonObject.getString("n"));
        content.setLim(jsonObject.getString("lim") == null ? content.getMax() : jsonObject.getString("lim"
        ));
        content.setDec(jsonObject.getString("dec") == null ? "18" : jsonObject.getString("dec"));
        if (content.getOp().equals(ORC20_OP_AIRDROP) ||content.getOp().equals(ORC20_OP_SWAP)) {
            if (null != jsonObject.getJSONArray("to")) {
                content.setTo(jsonObject.getJSONArray("to").toJavaList(String.class));
            } else {
                return null;
            }
        }
        content.setPrice(jsonObject.getString("price"));
        content.setSeller(jsonObject.getString("seller"));
        content.setBuyer(jsonObject.getString("buyer"));
        content.setExpire(jsonObject.getString("expire"));
        content.setVote(jsonObject.getString("vote"));
        if (content.getOp().equals(ORC20_OP_PROPOSE)) {
            content.setQuo(jsonObject.getString("quo"));
            content.setPass(jsonObject.getString("pass"));
        }
        if (content.getOp().equals(ORC20_OP_LOCK)) {
            content.setToAddress(jsonObject.getString("to"));
        }
        if (content.getOp().equals(ORC20_OP_BURN)) {
            content.setBurnTo(jsonObject.getString("to"));
        }
        if (content.getOp().equals(ORC20_OP_SWAP)) {
            if (null != jsonObject.getJSONArray("from")) {
                content.setTo(jsonObject.getJSONArray("from").toJavaList(String.class));
            } else {
                return null;
            }
        }

        return content;
    }

    private void insertOrUpdateBalance(String address, String ticker, Long id, BigDecimal credits,
                                       BigDecimal transferable, BigDecimal balance, TokenInfoDO tokenInfo,
                                       Boolean isDeployer, Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache) {


        UserTokenBalanceDO tokenBalance = getUserTokenBalanceByInsNumAndAddress(address, tokenInfo.getInscriptionNumber(),
                latestBalanceCache);

        if (tokenBalance != null) {
            if (balance.compareTo(BigDecimal.ZERO) > 0 && tokenBalance.getBalance().compareTo(BigDecimal.ZERO) == 0){
                tokenInfo.setHolders(tokenInfo.getHolders() + 1);
            }
            tokenBalance.setAvailable(tokenBalance.getAvailable().add(credits));
            tokenBalance.setTransferableBalance(tokenBalance.getTransferableBalance().add(transferable));
            tokenBalance.setBalance(tokenBalance.getBalance().add(balance));

            if (balance.compareTo(BigDecimal.ZERO) < 0 && tokenBalance.getBalance().compareTo(BigDecimal.ZERO) == 0){
                tokenInfo.setHolders(tokenInfo.getHolders() - 1);
            }

            //userTokenBalanceService.updateById(tokenBalance);
        } else {
            tokenBalance = new UserTokenBalanceDO();
            tokenBalance.setTicker(ticker);
            tokenBalance.setInscriptionNumber(tokenInfo.getInscriptionNumber());
            tokenBalance.setAddress(address);
            tokenBalance.setTransferableBalance(transferable);
            tokenBalance.setAvailable(credits);
            tokenBalance.setBalance(balance);
            tokenBalance.setId(IdValueGenerator.INSTANCE.nextLong());
            tokenBalance.setOrc20Id(id);
            tokenBalance.setIsDeployer(isDeployer);
            tokenBalance.setInsert(true);
            tokenBalance.setLockCredits(BigDecimal.ZERO);

            if (balance.compareTo(BigDecimal.ZERO) != 0){
                tokenInfo.setHolders(tokenInfo.getHolders() + 1);
            }


            Map<String, UserTokenBalanceDO> map;
            if (latestBalanceCache.containsKey(tokenInfo.getInscriptionNumber())) {
                map = latestBalanceCache.get(tokenInfo.getInscriptionNumber());
                map.put(address, tokenBalance);
            } else {
                map = new HashMap<>();
            }
            map.put(address, tokenBalance);
            latestBalanceCache.put(tokenInfo.getInscriptionNumber(), map);
        }

        if (tokenBalance.getTransferableBalance().compareTo(BigDecimal.ZERO) < 0 ||
                tokenBalance.getBalance().compareTo(BigDecimal.ZERO) < 0 ||
                tokenBalance.getAvailable().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException(tokenBalance.getAddress() + ":" + tokenBalance.getTicker() + ":" + tokenBalance.getOrc20Id() + ":");
        }
    }


    public void saveHistoryAndUpdateTokenTransactions(InscriptionDataDO inscriptionData, OperationType type,
                                                      BigDecimal amt, Integer isValid, Long orc20Id, String ticker,
                                                      String errorMsg,
                                                      Long tokenInscriptionNumber, String nonce, Boolean transferable,
                                                      TokenInfoDO tokenInfo, Boolean save, String fromAddress,
                                                      String toAddress, List<OperationHistoryDO> latestHistoryCache) {
        OperationHistoryDO transfersHistory = new OperationHistoryDO();
        transfersHistory.setType(type);
        transfersHistory.setTxHash(inscriptionData.getTxHash());
        transfersHistory.setNumber(inscriptionData.getInscriptionNumber());
        transfersHistory.setInscriptionID(inscriptionData.getInscriptionID());
        transfersHistory.setTicker(ticker);
        transfersHistory.setAmount(amt);
        transfersHistory.setVout(inscriptionData.getVout());
        transfersHistory.setCreateIdxKey(inscriptionData.getCreateIdxKey());
        transfersHistory.setFromAddress(fromAddress);
        transfersHistory.setToAddress(toAddress);
        transfersHistory.setSatoshi(inscriptionData.getSatoshi());
        transfersHistory.setBlockTime(inscriptionData.getBlockTime());
        transfersHistory.setHeight(inscriptionData.getHeight());
        transfersHistory.setStatus(isValid);
        transfersHistory.setId(IdValueGenerator.INSTANCE.nextLong());
        transfersHistory.setOrc20Id(orc20Id);
        transfersHistory.setErrorMsg(errorMsg);
        transfersHistory.setTokenInscriptionNumber(tokenInscriptionNumber);
        transfersHistory.setNonce(nonce);
        transfersHistory.setTransferable(transferable);
        transfersHistory.setHolderAddress(inscriptionData.getToAddress());

        String location = inscriptionData.getLocation();
        if (location == null || location.isEmpty()) {
            location = String.format("%s:%s:%s", inscriptionData.getTxHash(),
                    inscriptionData.getVout(), inscriptionData.getOffset());
        }
        transfersHistory.setLocation(location);
        if (save) {
            operationHistoryService.save(transfersHistory);
        } else {
            latestHistoryCache.add(transfersHistory);
        }

        tokenInfo.setTransactions(tokenInfo.getTransactions() + 1);

    }

    @Transactional(rollbackFor = Exception.class)
    public void dealTxs(Map<String, List<InscriptionDataDO>> txCache) throws Exception {
        //保存orc20相关交易
        try {
            List<InscriptionDataDO> inscriptionDataDOS = new ArrayList<>();
            for (List<InscriptionDataDO> cache : txCache.values()) {
                if (!cache.isEmpty()) {
                    inscriptionDataDOS.addAll(cache);
                }
            }
            if (!inscriptionDataDOS.isEmpty()) {
                inscriptionDataService.saveBatch(inscriptionDataDOS);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        } finally {
            txCache.clear();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void dealTxs(List<InscriptionDataDO> inscriptions, Integer height, DealHeightDO one, Boolean filter,
                        Set<String> filterInscriptions) throws ExecutionException, InterruptedException {


        Map<String, Map<Long, TokenInfoDO>> latestTokenCache = new HashMap<>(128);

        Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache = new HashMap<>(128);
        List<OperationHistoryDO> latestHistoryCache = new ArrayList<>(256);


        one.setHeight(one.getHeight() + 1);
        dealHeightService.updateById(one);
        StopWatch stopWatch = new StopWatch();

        //查询所有有效sell操作
        final Future<HashMap<String, List<SellOrderDetailDO>>> submit = executor.submit(() -> {

            if (height > SELL_HEIGHT) {
                List<SellOrderDO> sellOrderDOList;

                if (filter) {
                    sellOrderDOList = sellOrderService.lambdaQuery().
                            eq(SellOrderDO::getStatus, 0).
                            notIn(SellOrderDO::getTokenInscriptionId, filterInscriptions).
                            list();
                } else {
                    sellOrderDOList = sellOrderService.lambdaQuery().
                            eq(SellOrderDO::getStatus, 0).
                            in(SellOrderDO::getTokenInscriptionId, filterInscriptions).
                            list();
                }

                if (!sellOrderDOList.isEmpty()) {
                    Set<String> sellAddressSets = new HashSet<>();
                    for (SellOrderDO sellOrderDO : sellOrderDOList) {
                        sellAddressSets.add(sellOrderDO.getSeller());
                    }
                    OrdiRpc ordiRpc = new OrdiRpc(ordiUrl, null);
                    HashMap<String, List<SellOrderDetailDO>> tx = ordiRpc.getTx(height, sellAddressSets);

                    return tx;
                }

            }
            return new HashMap<>();
        });


        //查询未过期的lock
        Future<List<LockDO>> lockSubmit = executor.submit(() -> {

            List<LockDO> list;

            if (filter) {
                list = lockService.lambdaQuery().
                        eq(LockDO::getStatus, 1).
                        ne(LockDO::getExpire, "never").
                        notIn(LockDO::getTokenInscriptionId, filterInscriptions).
                        list();

            } else {

                list = lockService.lambdaQuery().
                        eq(LockDO::getStatus, 1).
                        ne(LockDO::getExpire, "never").
                        in(LockDO::getTokenInscriptionId, filterInscriptions).
                        list();
            }

            return list;
        });

        final List<LockDO> latestLockCache = lockSubmit.get();

        if (inscriptions.isEmpty()) {
            final HashMap<String, List<SellOrderDetailDO>> latestSellCache = submit.get();

            if (!latestSellCache.isEmpty()) {
                dealSellOrder(latestSellCache, height, latestBalanceCache, latestHistoryCache);
            }
            if (!latestLockCache.isEmpty()) {
                dealExpireLock(latestLockCache, height, latestHistoryCache);
            }
            return;
        }

        if (!latestLockCache.isEmpty()) {
            dealExpireLock(latestLockCache, height, latestHistoryCache);
        }

        Map<Long, InscriptionContent> contents = new HashMap<>(32);
        //从铭文中分析出所有ticker,并一次性加载到内存

        stopWatch.start("fillLatestToken");
        fillLatestToken(inscriptions, contents, latestTokenCache);
        stopWatch.stop();
        stopWatch.start("deal");
        inscriptions.forEach(inscription -> {
            try {

                dealInscriptionData(inscription, contents.get(inscription.getId()), latestTokenCache,
                        latestBalanceCache, latestHistoryCache);

            } catch (Exception e) {
                log.info("处理orc20铭文:{}:{}:  是否转账:  {}", inscription.getInscriptionID(),
                        inscription.getContentBody(), inscription.getIsTransfer());
                throw new RuntimeException(e);
            }
        });
        stopWatch.stop();
        try {
            //并发处理
            //批量插入代币

            List<TokenInfoDO> insertToken = new ArrayList<>(32);
            List<TokenInfoDO> updateToken = new ArrayList<>(64);
            for (Map<Long, TokenInfoDO> map : latestTokenCache.values()) {
                for (TokenInfoDO value : map.values()) {
                    if (value.getInsert()) {
                        insertToken.add(value);
                    } else {
                        updateToken.add(value);
                    }
                }
            }
            stopWatch.start(String.format("insert token:插入:%s:更新%s", insertToken.size(), updateToken.size()));
            tokenInfoService.saveBatch(insertToken);
            tokenInfoService.updateBatchById(updateToken);
            stopWatch.stop();

            //批量插入用户余额
            List<UserTokenBalanceDO> insertBalance = new ArrayList<>(32);
            List<UserTokenBalanceDO> updateBalance = new ArrayList<>(64);
            for (Map<String, UserTokenBalanceDO> map : latestBalanceCache.values()) {
                for (UserTokenBalanceDO value : map.values()) {
                    if (value.getInsert()) {
                        insertBalance.add(value);
                    } else {
                        updateBalance.add(value);
                    }
                }
            }
            stopWatch.start(String.format("insert balance:插入:%s:更新%s", insertBalance.size(), updateBalance.size()));
            userTokenBalanceService.saveBatch(insertBalance);
            userTokenBalanceService.updateBatchById(updateBalance);
            stopWatch.stop();

            stopWatch.start("批量插入历史数据:" + latestHistoryCache.size());
            //批量插入历史数据
            operationHistoryService.saveBatch(latestHistoryCache);
            stopWatch.stop();


            //处理所有购买的订单
            stopWatch.start("处理所有购买的订单");

            final HashMap<String, List<SellOrderDetailDO>> latestSellCache = submit.get();
            if (!latestSellCache.isEmpty()) {
                dealSellOrder(latestSellCache, height, latestBalanceCache, latestHistoryCache);
            }

            stopWatch.stop();

            log.info(stopWatch.prettyPrint(TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void dealExpireLock(List<LockDO> latestLockCache, Integer height,
                               List<OperationHistoryDO> latestHistoryCache) {
        Integer expire;
        for (LockDO lockDO : latestLockCache) {
            expire = Integer.valueOf(lockDO.getExpire());
            if (expire + lockDO.getActiveHeight() < height) {
                lockDO.setStatus(0);

                InscriptionDataDO inscriptionData = inscriptionDataService.lambdaQuery().
                        eq(InscriptionDataDO::getInscriptionID, lockDO.getInscriptionId()).
                        eq(InscriptionDataDO::getIsTransfer, 0).
                        one();

                TokenInfoDO tokenInfo = tokenInfoService.lambdaQuery().
                        eq(TokenInfoDO::getInscriptionNumber, lockDO.getTokenInscriptionId()).
                        one();
                //获取冻结余额并减去amount

                UserTokenBalanceDO tokenBalance = userTokenBalanceService.lambdaQuery().
                        eq(UserTokenBalanceDO::getInscriptionNumber, tokenInfo.getInscriptionNumber()).
                        eq(UserTokenBalanceDO::getAddress, lockDO.getInitialAddress()).
                        one();
                if (tokenBalance.getLockCredits().compareTo(lockDO.getAmount()) >= 0) {
                    tokenBalance.setLockCredits(tokenBalance.getLockCredits().subtract(lockDO.getAmount()));
                    tokenBalance.setBalance(tokenBalance.getBalance().subtract(lockDO.getAmount()));
                    if (tokenBalance.getBalance().compareTo(BigDecimal.ZERO) == 0){
                        tokenInfo.setHolders(tokenInfo.getHolders() - 1);

                    }
                    userTokenBalanceService.updateById(tokenBalance);
                } else {
                    log.info("冻结余额不足");
                    return;
                }
                //获取目标地址余额并增加amount
                if (tokenInfo.getDeployer().equals(lockDO.getToAddress())) {
                    lockInsertOrUpdateBalance(lockDO.getToAddress(), lockDO.getTicker(),
                            lockDO.getTokenId(), lockDO.getAmount(), tokenInfo.getInscriptionNumber(),
                            true, tokenInfo);
                } else {
                    lockInsertOrUpdateBalance(lockDO.getToAddress(), lockDO.getTicker(),
                            lockDO.getTokenId(), lockDO.getAmount(), tokenInfo.getInscriptionNumber(),
                            false, tokenInfo);
                }
                tokenInfoService.updateById(tokenInfo);
                lockService.updateById(lockDO);
                inscriptionData.setHeight(height);
                saveHistoryAndUpdateTokenTransactions(inscriptionData, TRANSFER,
                        lockDO.getAmount(), 1,
                        lockDO.getTokenId(), lockDO.getTicker(), null,
                        tokenInfo.getInscriptionNumber(), null, false, tokenInfo, true,
                        lockDO.getInitialAddress(), lockDO.getToAddress(), latestHistoryCache);
            }

        }

    }

    private void lockInsertOrUpdateBalance(String address, String ticker, Long tokenId,
                                           BigDecimal amt, Long inscriptionNumber, boolean isDeployer, TokenInfoDO tokenInfo) {
        UserTokenBalanceDO tokenBalance = userTokenBalanceService.lambdaQuery().
                eq(UserTokenBalanceDO::getInscriptionNumber, inscriptionNumber).
                eq(UserTokenBalanceDO::getAddress, address).
                one();

        if (tokenBalance != null) {
            if (tokenBalance.getBalance().compareTo(BigDecimal.ZERO) == 0){
                tokenInfo.setHolders(tokenInfo.getHolders() + 1);
            }
            tokenBalance.setAvailable(tokenBalance.getAvailable().add(amt));
            tokenBalance.setBalance(tokenBalance.getBalance().add(amt));


            userTokenBalanceService.updateById(tokenBalance);
            //userTokenBalanceService.updateById(tokenBalance);
        } else {
            tokenBalance = new UserTokenBalanceDO();
            tokenBalance.setTicker(ticker);
            tokenBalance.setInscriptionNumber(inscriptionNumber);
            tokenBalance.setAddress(address);
            tokenBalance.setTransferableBalance(amt);
            tokenBalance.setAvailable(BigDecimal.ZERO);
            tokenBalance.setBalance(amt);
            tokenBalance.setId(IdValueGenerator.INSTANCE.nextLong());
            tokenBalance.setOrc20Id(tokenId);
            tokenBalance.setIsDeployer(isDeployer);
            tokenBalance.setInsert(true);

            tokenInfo.setHolders(tokenInfo.getHolders() + 1);
            userTokenBalanceService.save(tokenBalance);
        }
        tokenInfoService.updateById(tokenInfo);
        if (tokenBalance.getTransferableBalance().compareTo(BigDecimal.ZERO) < 0 ||
                tokenBalance.getBalance().compareTo(BigDecimal.ZERO) < 0 ||
                tokenBalance.getAvailable().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException(tokenBalance.getAddress() + ":" + tokenBalance.getTicker() + ":" + tokenBalance.getOrc20Id() + ":");
        }
    }

    public void dealSellOrder(Map<String, List<SellOrderDetailDO>> sellOrderDOList, Integer height,
                              Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                              List<OperationHistoryDO> latestHistoryCache) {
        sellOrderDOList.forEach((address, sellOrderDetailDOList) -> {
            //获取当前address的有效sell铭文并遍历
            List<SellOrderDO> list = sellOrderService.lambdaQuery().
                    eq(SellOrderDO::getSeller, address).
                    eq(SellOrderDO::getStatus, 0).
                    list();
            if (!sellOrderDetailDOList.isEmpty()) {
                for (SellOrderDO sellOrderDO : list) {
                    //获取激活地址的lockCredits
                    UserTokenBalanceDO tokenBalanceDO =
                            getUserTokenBalanceByDb(sellOrderDO.getTokenInscriptionNumber(),
                                    sellOrderDO.getActivationAddress(), latestBalanceCache);
                    TokenInfoDO tokenInfo = tokenInfoService.lambdaQuery().
                            eq(TokenInfoDO::getTicker, sellOrderDO.getTicker()).
                            eq(TokenInfoDO::getOrc20Id, tokenBalanceDO.getOrc20Id()).
                            one();
                    BigDecimal soldAmount = sellOrderDO.getSoldAmount();
                    BigDecimal remainCredits = sellOrderDO.getAmount().subtract(soldAmount);
                    //判断当前高度卖单是否过期
                    if (!sellOrderDO.getExpire().equalsIgnoreCase("never")) {
                        if (height > sellOrderDO.getHeight() + Integer.parseInt(sellOrderDO.getExpire())) {
                            sellOrderDO.setStatus(2);
                            sellOrderService.updateById(sellOrderDO);
                            tokenBalanceDO.setAvailable(tokenBalanceDO.getAvailable().add(remainCredits));
                            tokenBalanceDO.setLockCredits(BigDecimal.ZERO);

                            for (SellOrderDetailDO sellOrderDetailDO : sellOrderDetailDOList) {
                                insertSellOrderDetail(BigDecimal.ZERO, sellOrderDO,sellOrderDetailDO,
                                        sellOrderDO.getId(), BigDecimal.ZERO, 1);
                                saveSellAndBuyHistory(height, sellOrderDO, sellOrderDetailDO, OperationType.TRANSFER, BigDecimal.ZERO, 0,
                                        tokenBalanceDO.getOrc20Id(),
                                        tokenInfo.getTicker(), "sell-order is expire", tokenInfo.getInscriptionNumber(),
                                        false, tokenInfo, true,
                                        sellOrderDO.getActivationAddress(), sellOrderDetailDO.getAddress(),
                                        latestHistoryCache);
                            }
                            break;
                        }
                    }

                    //遍历当前address的交易记录
                    for (SellOrderDetailDO sellOrderDetailDO : sellOrderDetailDOList) {
                        //判断支付金额并计算
                        //购买数量
                        BigDecimal amount = sellOrderDetailDO.getBtcAmount().
                                divide(sellOrderDO.getPrice(), 2, RoundingMode.DOWN);

                        //判断购买者地址是否在白名单内
                        Integer repeatNum = isWhiteList(sellOrderDetailDO.getAddress(), sellOrderDO.getBuyer());
                        if (repeatNum == 0) {
                            log.info("{} is not allowed to buy", sellOrderDetailDO.getAddress());

                            insertSellOrderDetail(amount, sellOrderDO,sellOrderDetailDO, sellOrderDO.getId(), BigDecimal.ZERO, 1);
                            saveSellAndBuyHistory(height, sellOrderDO, sellOrderDetailDO, OperationType.TRANSFER, amount, 0,
                                    tokenBalanceDO.getOrc20Id(),
                                    tokenInfo.getTicker(), "is not allowed to buy", tokenInfo.getInscriptionNumber(),
                                    false, tokenInfo, false,
                                    sellOrderDO.getActivationAddress(), sellOrderDetailDO.getAddress(),
                                    latestHistoryCache);
                            continue;
                        }
                        //判断支付金额是否能够购买最小额度的代币
                        if (amount.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal max;
                            SellOrderWhiteListDO sellOrderWhiteListDO = sellOrderWhiteListService.lambdaQuery().
                                    eq(SellOrderWhiteListDO::getAddress, sellOrderDetailDO.getAddress()).
                                    eq(SellOrderWhiteListDO::getSellOrderId, sellOrderDO.getId()).
                                    one();

                            if (sellOrderWhiteListDO == null) {
                                max = sellOrderDO.getLim();
                            } else {
                                max = sellOrderWhiteListDO.getLim().subtract(sellOrderWhiteListDO.getAmountFilled());
                            }

                            //判断购买数量和lim的关系
                            if (amount.compareTo(max) > 0) {
                                if (remainCredits.compareTo(max) >= 0) {
                                    dealBuyOrder(height, amount, sellOrderDO, tokenBalanceDO,
                                            max, tokenInfo, sellOrderDetailDO, 1, latestBalanceCache,
                                            latestHistoryCache);
                                    remainCredits = remainCredits.subtract(max);
                                } else {
                                    dealBuyOrder(height, amount, sellOrderDO, tokenBalanceDO,
                                            remainCredits, tokenInfo, sellOrderDetailDO, 1, latestBalanceCache,
                                            latestHistoryCache);
                                    remainCredits = BigDecimal.ZERO;
                                }
                            } else {
                                if (amount.compareTo(remainCredits) >= 0) {
                                    dealBuyOrder(height, amount, sellOrderDO, tokenBalanceDO,
                                            remainCredits, tokenInfo, sellOrderDetailDO, 1, latestBalanceCache,
                                            latestHistoryCache);
                                    remainCredits = BigDecimal.ZERO;
                                } else {
                                    dealBuyOrder(height, amount, sellOrderDO, tokenBalanceDO,
                                            amount, tokenInfo, sellOrderDetailDO, 1, latestBalanceCache,
                                            latestHistoryCache);
                                    remainCredits = remainCredits.subtract(amount);
                                }
                            }
                        } else {
                            insertSellOrderDetail(amount, sellOrderDO, sellOrderDetailDO, sellOrderDO.getId(),
                                    BigDecimal.ZERO, 1);
                            saveSellAndBuyHistory(height, sellOrderDO, sellOrderDetailDO, OperationType.TRANSFER,
                                    BigDecimal.ZERO, 0,
                                    tokenBalanceDO.getOrc20Id(),
                                    tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                                    false, tokenInfo, false,
                                    sellOrderDO.getActivationAddress(), sellOrderDetailDO.getAddress(),
                                    latestHistoryCache);
                            continue;
                        }
                        if (remainCredits.compareTo(BigDecimal.ZERO) == 0) {
                            sellOrderDO.setStatus(1);
                            sellOrderService.updateById(sellOrderDO);
                        }
                    }
                }
            }
        });
    }

    private void dealBuyOrder(Integer height, BigDecimal amount, SellOrderDO sellOrderDO,
                              UserTokenBalanceDO tokenBalanceDO,
                              BigDecimal tokenAmount, TokenInfoDO tokenInfo,
                              SellOrderDetailDO sellOrderDetailDO, Integer isValid,
                              Map<Long, Map<String, UserTokenBalanceDO>> latestBalanceCache,
                              List<OperationHistoryDO> latestHistoryCache) {
        //给买家转代币
        insertOrUpdateBalance(sellOrderDetailDO.getAddress(), sellOrderDO.getTicker(),
                tokenBalanceDO.getOrc20Id(), tokenAmount, BigDecimal.ZERO,
                tokenAmount, tokenInfo, false, latestBalanceCache);
        //更新买单明细
        insertSellOrderDetail(amount, sellOrderDO, sellOrderDetailDO, sellOrderDO.getId(), tokenAmount, 0);
        //更新卖家余额中的冻结金额
        tokenBalanceDO.setLockCredits(tokenBalanceDO.getLockCredits().subtract(tokenAmount));
        tokenBalanceDO.setBalance(tokenBalanceDO.getBalance().subtract(tokenAmount));
        if (tokenBalanceDO.getBalance().compareTo(BigDecimal.ZERO) == 0){
            tokenInfo.setHolders(tokenInfo.getHolders() - 1);
        }
//        userTokenBalanceService.updateById(tokenBalanceDO);
        //更新白名单购买数量
        if (!sellOrderDO.getBuyer().equalsIgnoreCase("anyone")) {
            SellOrderWhiteListDO one = sellOrderWhiteListService.lambdaQuery().
                    eq(SellOrderWhiteListDO::getAddress, sellOrderDetailDO.getAddress()).
                    eq(SellOrderWhiteListDO::getSellOrderId, sellOrderDO.getId()).
                    one();

            one.setAmountFilled(one.getAmountFilled().add(tokenAmount));
            sellOrderWhiteListService.updateById(one);
        }
        //保存交易历史记录
        saveSellAndBuyHistory(height, sellOrderDO, sellOrderDetailDO, OperationType.TRANSFER, tokenAmount, isValid,
                tokenBalanceDO.getOrc20Id(),
                tokenInfo.getTicker(), null, tokenInfo.getInscriptionNumber(),
                false, tokenInfo, false,
                sellOrderDO.getActivationAddress(), sellOrderDetailDO.getAddress(), latestHistoryCache);
        //更新卖单已售出数量
        sellOrderDO.setSoldAmount(sellOrderDO.getSoldAmount().add(tokenAmount));
        sellOrderService.updateById(sellOrderDO);
    }


    private Integer isWhiteList(String address, String buyer) {
        try {
            JSONArray buyerArry = JSONArray.parseArray(buyer);
            Map<String, List<String>> collect = buyerArry.toJavaList(String.class).stream().collect(Collectors.
                    groupingBy(o -> o, Collectors.toList()));
            return collect.get(address).size();

        } catch (Exception e) {
            if (buyer.equalsIgnoreCase("anyone")) {
                return 1;
            }
            return address.equals(buyer) ? 1 : 0;
        }

    }

    private void saveSellAndBuyHistory(Integer height, SellOrderDO sellOrderDO, SellOrderDetailDO sellOrderDetailDO,
                                       OperationType type, BigDecimal amt, int isValid,
                                       Long orc20Id, String ticker, String errorMsg, Long tokenInscriptionNumber,
                                       Boolean transferable, TokenInfoDO tokenInfo, Boolean save, String fromAddress,
                                       String toAddress, List<OperationHistoryDO> latestHistoryCache) {
        OperationHistoryDO transfersHistory = new OperationHistoryDO();
        transfersHistory.setType(type);
        transfersHistory.setTxHash(sellOrderDetailDO.getTxId());
        transfersHistory.setNumber(sellOrderDO.getInscriptionNumber());
        transfersHistory.setInscriptionID(sellOrderDO.getInscriptionId());
        transfersHistory.setTicker(ticker);
        transfersHistory.setAmount(amt);
        transfersHistory.setFromAddress(fromAddress);
        transfersHistory.setToAddress(toAddress);
        //TODO:
        transfersHistory.setBlockTime(sellOrderDetailDO.getChainTime());
        transfersHistory.setHeight(height);
        transfersHistory.setStatus(isValid);
        transfersHistory.setId(IdValueGenerator.INSTANCE.nextLong());
        transfersHistory.setOrc20Id(orc20Id);
        transfersHistory.setErrorMsg(errorMsg);
        transfersHistory.setTokenInscriptionNumber(tokenInscriptionNumber);
        transfersHistory.setTransferable(transferable);
        transfersHistory.setHolderAddress(toAddress);
        if (save) {
            operationHistoryService.save(transfersHistory);
        } else {
            latestHistoryCache.add(transfersHistory);
        }

        tokenInfo.setTransactions(tokenInfo.getTransactions() + 1);
    }

    private void insertSellOrderDetail(BigDecimal amount, SellOrderDO sellOrderDO,
                                       SellOrderDetailDO sellOrderDetailDO, Long id,
                                       BigDecimal credits, Integer status) {
        SellOrderDetailDO detailDO = new SellOrderDetailDO();
        detailDO.setAddress(sellOrderDetailDO.getAddress());
        detailDO.setBtcAmount(amount);
        detailDO.setChainTime(sellOrderDetailDO.getChainTime());
        detailDO.setSellOrderId(id);
        detailDO.setStatus(status);
        detailDO.setTokenAmount(credits);
        detailDO.setTxId(sellOrderDetailDO.getTxId());
        detailDO.setId(IdValueGenerator.INSTANCE.nextLong());
        sellOrderDetailService.save(detailDO);
    }

    public void fillLatestToken(List<InscriptionDataDO> inscriptions,
                                 Map<Long, InscriptionContent> contents,
                                 Map<String, Map<Long, TokenInfoDO>> latestTokenCache) {


        Set<String> tickers = new HashSet<>();
        for (InscriptionDataDO inscription : inscriptions) {
            InscriptionContent content = getInscriptionContent(inscription.getContentBody());
            if (content != null) {
                if (contents != null) {
                    contents.put(inscription.getId(), content);
                }

                tickers.add(content.getTick());
            }
        }
        if (tickers.isEmpty()) {
            return;
        }

        final List<TokenInfoDO> tokenInfoList = tokenInfoService.lambdaQuery()
                .in(TokenInfoDO::getTicker, tickers)
                .list();

        for (TokenInfoDO tokenInfo : tokenInfoList) {
            Map<Long, TokenInfoDO> map = latestTokenCache.get(tokenInfo.getTicker().toLowerCase(Locale.ROOT));

            if (map == null) {
                map = new HashMap<>();
            }
            map.put(tokenInfo.getOrc20Id(), tokenInfo);
            tokenInfo.setInsert(false);
            latestTokenCache.put(tokenInfo.getTicker().toLowerCase(Locale.ROOT), map);
        }
    }
}
