package com.bixin.nft.core.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.bixin.ido.server.common.errorcode.IdoErrorCode;
import com.bixin.ido.server.common.exception.IdoException;
import com.bixin.ido.server.enums.NftGroupStatus;
import com.bixin.ido.server.utils.BigDecimalUtil;
import com.bixin.ido.server.utils.TypeArgsUtil;
import com.bixin.nft.bean.DO.NftGroupDo;
import com.bixin.nft.bean.DO.NftInfoDo;
import com.bixin.nft.bean.DO.NftKikoCatDo;
import com.bixin.nft.bean.dto.TokenDto;
import com.bixin.nft.core.mapper.NftGroupMapper;
import com.bixin.nft.core.mapper.NftInfoMapper;
import com.bixin.nft.core.mapper.NftKikoCatMapper;
import com.google.common.collect.Lists;
import com.novi.serde.Bytes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableInt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.starcoin.bean.ScriptFunctionObj;
import org.starcoin.bean.TypeObj;
import org.starcoin.utils.AccountAddressUtils;
import org.starcoin.utils.BcsSerializeHelper;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class NftContractService {

    @Resource
    private NftGroupMapper nftGroupMapper;
    @Resource
    private NftInfoMapper nftInfoMapper;
    @Resource
    private NftKikoCatMapper nftKikoCatMapper;
    @Resource
    private ContractService contractService;

    @Value("${ido.star.nft.market}")
    private String market;

    @Value("${ido.star.nft.scripts}")
    private String scripts;

    private static String MARKET_MODULE = "NFTMarket04";
    private static String SCRIPTS_MODULE = "NFTScripts04";

    /**
     * 1.部署NFT Market
     * 2.部署NFT Scripts
     * 3.初始化config
     *
     * @return
     */
    public void initNFTMarket(BigInteger creatorFee, BigInteger platformFee) {
        if (!contractService.deployContract(market, "contract/nft/" + MARKET_MODULE + ".mv", null)) {
            log.error("NFT Market部署失败");
            throw new IdoException(IdoErrorCode.CONTRACT_DEPLOY_FAILURE);
        }
        if (!contractService.deployContract(scripts, "contract/nft/" + SCRIPTS_MODULE + ".mv", null)) {
            log.error("NFT Scripts部署失败");
            throw new IdoException(IdoErrorCode.CONTRACT_DEPLOY_FAILURE);
        }
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(scripts)
                .moduleName(SCRIPTS_MODULE)
                .functionName("init_config")
                .args(Lists.newArrayList(
                        BcsSerializeHelper.serializeU128ToBytes(creatorFee),
                        BcsSerializeHelper.serializeU128ToBytes(platformFee)
                ))
                .build();
        if (!contractService.callFunction(market, scriptFunctionObj)) {
            log.error("NFT Config初始化失败");
            throw new IdoException(IdoErrorCode.CONTRACT_CALL_FAILURE);
        }
    }

    /**
     * 1.部署NFT合约
     * 2.mint所有NFT
     * 3.盲盒首发
     *
     * @return
     */
    public void createNFT() {
        // 部署nft合约
        NftGroupDo selectNftGroupDo = new NftGroupDo();
        selectNftGroupDo.setStatus(NftGroupStatus.APPENDING.name());
        List<NftGroupDo> nftGroupDos = nftGroupMapper.selectByPrimaryKeySelectiveList(selectNftGroupDo);
        if (nftGroupDos != null) {
            nftGroupDos.forEach(nftGroupDo -> {
                if (!deployNFTContract(nftGroupDo)) {
                    log.error("NFT合约 {} 部署失败", nftGroupDo.getName());
                    throw new IdoException(IdoErrorCode.CONTRACT_DEPLOY_FAILURE);
                }
                nftGroupDo.setStatus(NftGroupStatus.INITIALIZED.name());
                log.info("NFT合约 {} 部署成功", nftGroupDo.getName());
                nftGroupMapper.updateByPrimaryKeySelective(nftGroupDo);
            });
        }
        // mint nft + 盲盒
        selectNftGroupDo.setStatus(NftGroupStatus.INITIALIZED.name());
        nftGroupDos = nftGroupMapper.selectByPrimaryKeySelectiveList(selectNftGroupDo);
        if (nftGroupDos != null) {
            nftGroupDos.forEach(nftGroupDo -> {
                NftInfoDo selectNftInfoDo = new NftInfoDo();
                selectNftInfoDo.setGroupId(nftGroupDo.getId());
                selectNftInfoDo.setCreated(false);
                // 取出该组下所有待铸造NFT
                List<NftInfoDo> nftInfoDos = nftInfoMapper.selectByPrimaryKeySelectiveList(selectNftInfoDo);
                if (nftInfoDos == null) {
                    return;
                }
                MutableInt nftId = new MutableInt(1);
                // 获取该组最后一个id
                selectNftInfoDo = new NftInfoDo();
                selectNftInfoDo.setGroupId(nftGroupDo.getId());
                selectNftInfoDo.setCreated(true);
                List<NftInfoDo> createdNftInfoDos = nftInfoMapper.selectByPrimaryKeySelectiveList(selectNftInfoDo);
                if (!CollectionUtils.isEmpty(createdNftInfoDos)) {
                    createdNftInfoDos.sort(Comparator.comparingLong(NftInfoDo::getNftId).reversed());
                    nftId.setValue(createdNftInfoDos.get(0).getNftId() + 1);
                }
                nftInfoDos.stream().sorted(Comparator.comparingLong(NftInfoDo::getId)).forEach(nftInfoDo -> {
                    if (!mintKikoCatNFT(nftGroupDo, nftInfoDo)) {
                        log.error("NFT {} mint失败", nftInfoDo.getName());
                        throw new IdoException(IdoErrorCode.CONTRACT_CALL_FAILURE);
                    }
                    log.info("NFT {} mint成功", nftInfoDo.getName());
                    nftInfoDo.setNftId(nftId.longValue());
                    nftInfoDo.setCreated(true);
                    nftInfoMapper.updateByPrimaryKeySelective(nftInfoDo);
                    nftId.add(1);
                });
                // 全部铸造完成，修改
                nftGroupDo.setStatus(NftGroupStatus.CREATED.name());
                nftGroupMapper.updateByPrimaryKeySelective(nftGroupDo);
            });
        }

        // 市场创建resource + 盲盒发售
        selectNftGroupDo.setStatus(NftGroupStatus.CREATED.name());
        nftGroupDos = nftGroupMapper.selectByPrimaryKeySelectiveList(selectNftGroupDo);
        if (nftGroupDos != null) {
            nftGroupDos.forEach(nftGroupDo -> {
                List<TokenDto> supportTokenList = JSON.parseObject(nftGroupDo.getSupportToken(),
                        new TypeReference<>() {});
                supportTokenList.forEach(tokenDto -> {
                    if (!initMarket(nftGroupDo, tokenDto.getAddress())) {
                        log.error("NFT {} 市场初始化失败, 设置币种:{}", nftGroupDo.getName(), tokenDto.getAddress());
                        throw new IdoException(IdoErrorCode.CONTRACT_CALL_FAILURE);
                    }
                });
                if (!transferBox(nftGroupDo)) {
                    log.error("NFT {} 盲盒转账失败", nftGroupDo.getName());
                    throw new IdoException(IdoErrorCode.CONTRACT_CALL_FAILURE);
                }
                if (!initBoxOffering(nftGroupDo)) {
                    log.error("NFT {} 盲盒发售创建失败", nftGroupDo.getName());
                    throw new IdoException(IdoErrorCode.CONTRACT_CALL_FAILURE);
                }
                // 发售成功
                log.info("NFT {} 盲盒发售创建成功", nftGroupDo.getName());
                nftGroupDo.setStatus(NftGroupStatus.OFFERING.name());
                nftGroupMapper.updateByPrimaryKeySelective(nftGroupDo);
            });
        }
    }

    /**
     * 部署NFT Token
     *
     * @return
     */
    private boolean deployNFTContract(NftGroupDo nftGroupDo) {
        String moduleName = TypeArgsUtil.parseTypeObj(nftGroupDo.getNftMeta()).getModuleName();
        String path = "contract/nft/" + moduleName + ".mv";
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(nftGroupDo.getCreator())
                .moduleName(moduleName)
                .functionName("init")
                .tyArgs(Lists.newArrayList())
                .args(Lists.newArrayList(
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftGroupDo.getName())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftGroupDo.getNftTypeImageData())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftGroupDo.getEnDescription()))
                ))
                .build();
        return contractService.deployContract(nftGroupDo.getCreator(), path, scriptFunctionObj);
    }

    /**
     * mint NFT
     */
    private boolean mintKikoCatNFT(NftGroupDo nftGroupDo, NftInfoDo nftInfoDo) {
        TypeObj typeObj = TypeArgsUtil.parseTypeObj(nftGroupDo.getNftMeta());
        String address = typeObj.getModuleAddress();
        NftKikoCatDo nftKikoCatDo = new NftKikoCatDo();
        nftKikoCatDo.setInfoId(nftInfoDo.getId());
        nftKikoCatDo = nftKikoCatMapper.selectByPrimaryKeySelective(nftKikoCatDo);

        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(address)
                .moduleName(typeObj.getModuleName())
                .functionName("mint")
                .args(Lists.newArrayList(
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftInfoDo.getName())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftInfoDo.getImageData())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftGroupDo.getEnDescription())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftKikoCatDo.getBackground())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftKikoCatDo.getFur())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftKikoCatDo.getClothes())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftKikoCatDo.getFacialExpression())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftKikoCatDo.getHead())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftKikoCatDo.getAccessories())),
                        Bytes.valueOf(BcsSerializeHelper.serializeString(nftKikoCatDo.getEyes()))
                ))
                .build();
        return contractService.callFunction(address, scriptFunctionObj);
    }

    /**
     * 将盲盒转至market
     *
     * @param nftGroupDo
     * @return
     */
    private boolean transferBox(NftGroupDo nftGroupDo) {
        double boxTokenDecimal = nftGroupDo.getOfferingQuantity() * Math.pow(10, nftGroupDo.getBoxTokenPrecision());
        TypeObj typeObj = TypeArgsUtil.parseTypeObj(nftGroupDo.getBoxToken());
        return contractService.transfer(nftGroupDo.getCreator(), market, typeObj, BigInteger.valueOf((long) boxTokenDecimal));
    }

    /**
     * 创建盲盒发售
     */
    private boolean initBoxOffering(NftGroupDo nftGroupDo) {
        double boxTokenDecimal = Math.pow(10, nftGroupDo.getBoxTokenPrecision());
        double payTokenDecimal = Math.pow(10, nftGroupDo.getPayTokenPrecision());
        BigInteger boxAmount = BigInteger.valueOf(nftGroupDo.getOfferingQuantity() * (long) boxTokenDecimal);
        BigInteger sellingPrice = BigInteger.valueOf(nftGroupDo.getSellingPrice() * (long) payTokenDecimal);
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(scripts)
                .moduleName(SCRIPTS_MODULE)
                .functionName("box_initial_offering")
                .args(Lists.newArrayList(
                        BcsSerializeHelper.serializeU128ToBytes(boxAmount),
                        BcsSerializeHelper.serializeU128ToBytes(sellingPrice),
                        BcsSerializeHelper.serializeU64ToBytes(nftGroupDo.getSellingTime()),
                        BcsSerializeHelper.serializeAddressToBytes(AccountAddressUtils.create(nftGroupDo.getCreator()))
                ))
                .tyArgs(Lists.newArrayList(
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftMeta()),
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftBody()),
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getBoxToken()),
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getPayToken())
                ))
                .build();
        return contractService.callFunction(scripts, scriptFunctionObj);
    }

    /**
     * 初始化市场
     */
    private boolean initMarket(NftGroupDo nftGroupDo, String payToken) {
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(scripts)
                .moduleName(SCRIPTS_MODULE)
                .functionName("init_market")
                .args(Lists.newArrayList(
                        BcsSerializeHelper.serializeAddressToBytes(AccountAddressUtils.create(nftGroupDo.getCreator()))
                ))
                .tyArgs(Lists.newArrayList(
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftMeta()),
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftBody()),
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getBoxToken()),
                        TypeArgsUtil.parseTypeObj(payToken)
                ))
                .build();
        return contractService.callFunction(scripts, scriptFunctionObj);
    }

    /**
     * 初始化NFT回购
     */
    public void initBuyBackNFT(Long groupId, String payToken) {
        NftGroupDo nftGroupDo = nftGroupMapper.selectByPrimaryKey(groupId);
        if (nftGroupDo == null) {
            log.error("NFT市场回购初始化失败, groupId:{} 不存在", groupId);
            throw new IdoException(IdoErrorCode.DATA_NOT_EXIST);
        }
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(scripts)
                .moduleName(SCRIPTS_MODULE)
                .functionName("init_buy_back_list")
                .args(Lists.newArrayList())
                .tyArgs(Lists.newArrayList(
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftMeta()),
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftBody()),
                        TypeArgsUtil.parseTypeObj(payToken)
                ))
                .build();
        if (!contractService.callFunction(market, scriptFunctionObj)) {
            log.error("NFT市场回购初始化失败, 合约请求失败");
            throw new IdoException(IdoErrorCode.CONTRACT_CALL_FAILURE);
        }
    }

    /**
     * @return
     */
    public void buyBackNFT(Long infoId, String payToken, BigDecimal price) {
        NftInfoDo nftInfoDo = nftInfoMapper.selectByPrimaryKey(infoId);
        if (nftInfoDo == null) {
            log.error("NFT市场回购失败, infoId:{} 不存在", infoId);
            throw new IdoException(IdoErrorCode.DATA_NOT_EXIST);
        }
        NftGroupDo nftGroupDo = nftGroupMapper.selectByPrimaryKey(nftInfoDo.getGroupId());
        if (nftGroupDo == null) {
            log.error("NFT市场回购失败, groupId:{} 不存在", nftInfoDo.getGroupId());
            throw new IdoException(IdoErrorCode.DATA_NOT_EXIST);
        }
        BigInteger priceFactor = BigDecimalUtil.getPrecisionFactor(9).multiply(price).toBigInteger();
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(scripts)
                .moduleName(SCRIPTS_MODULE)
                .functionName("nft_buy_back")
                .args(Lists.newArrayList(
                        BcsSerializeHelper.serializeU64ToBytes(nftInfoDo.getNftId()),
                        BcsSerializeHelper.serializeU128ToBytes(priceFactor)
                ))
                .tyArgs(Lists.newArrayList(
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftMeta()),
                        TypeArgsUtil.parseTypeObj(nftGroupDo.getNftBody()),
                        TypeArgsUtil.parseTypeObj(payToken)
                ))
                .build();
        if (!contractService.callFunction(market, scriptFunctionObj)) {
            log.error("NFT市场回购失败, 合约请求失败");
            throw new IdoException(IdoErrorCode.CONTRACT_CALL_FAILURE);
        }
    }

    public boolean sellNFT() {
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(scripts)
                .moduleName(SCRIPTS_MODULE)
                .functionName("nft_sell")
                .args(Lists.newArrayList(
                        BcsSerializeHelper.serializeU64ToBytes(6L),
                        BcsSerializeHelper.serializeU128ToBytes(BigInteger.valueOf(100000000))
                ))
                .tyArgs(Lists.newArrayList(
                        TypeArgsUtil.parseTypeObj("0xd30b4de81d71c1793aa4db4763211e63::KikoCat06::KikoCatMeta"),
                        TypeArgsUtil.parseTypeObj("0xd30b4de81d71c1793aa4db4763211e63::KikoCat06::KikoCatBody"),
                        TypeArgsUtil.parseTypeObj("0x1::STC::STC")
                ))
                .build();
        return contractService.callFunction("0x142f352A24FEB989C65C1d48c4d884a9", scriptFunctionObj);
    }

    public boolean sellBox() {
        ScriptFunctionObj scriptFunctionObj = ScriptFunctionObj
                .builder()
                .moduleAddress(scripts)
                .moduleName(SCRIPTS_MODULE)
                .functionName("box_sell")
                .args(Lists.newArrayList(
                        BcsSerializeHelper.serializeU128ToBytes(BigInteger.valueOf(200000000))
                ))
                .tyArgs(Lists.newArrayList(
                        TypeArgsUtil.parseTypeObj("0xd30b4de81d71c1793aa4db4763211e63::KikoCat06::KikoCatBox"),
                        TypeArgsUtil.parseTypeObj("0x1::STC::STC")
                ))
                .build();
        return contractService.callFunction("0x142f352A24FEB989C65C1d48c4d884a9", scriptFunctionObj);
    }

//    public void rankNft() {
//
//
//        NftInfoDo nftInfoDo = new NftInfoDo();
//        nftInfoMapper.selectByPrimaryKeySelectiveList();
//
//    }

}
