package com.bixin.nft.service;

import com.bixin.common.response.R;
import com.bixin.nft.bean.bo.CompositeCardBean;
import com.bixin.nft.bean.vo.NftSelfResourceVo;

import java.util.List;
import java.util.Map;

/**
 * @author zhangcheng
 * create  2021/12/23
 */
public interface NftMetareverseService {

    List<Map<String, Object>> getSumByOccupationGroup();

    String compositeCard(CompositeCardBean bean);

    R analysisCard(String userAddress, long cardId);

    NftSelfResourceVo selfResource(String userAddress, String nftType);

}
