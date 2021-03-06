package com.bixin.ido.service.impl;

import com.bixin.ido.bean.DO.SwapCoins;
import com.bixin.ido.core.mapper.SwapCoinsMapper;
import com.bixin.ido.core.wrapDDL.SwapCoinsDDL;
import com.bixin.ido.service.ISwapCoinsService;
import com.bixin.common.utils.CaseUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhangcheng
 * create  2021-08-26 2:57 下午
 */
@Service
public class SwapCoinImpl implements ISwapCoinsService {

    @Resource
    SwapCoinsMapper swapCoinsMapper;

    @Override
    public List<SwapCoins> selectByDDL(SwapCoins coins) {
        SwapCoinsDDL coinsDDL = new SwapCoinsDDL();
        SwapCoinsDDL.Criteria criteria = coinsDDL.createCriteria();

        CaseUtil.buildNoneValue(coins.getShortName(), name -> criteria.andShortNameEqualTo(coins.getShortName()));

        coinsDDL.setOrderByClause("weight desc, id desc");

        return swapCoinsMapper.selectByDDL(coinsDDL);
    }

    @Override
    public List<SwapCoins> getALlByPage(int from, int offset) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("from", from);
        paramMap.put("offset", offset);

        return swapCoinsMapper.selectByPage(paramMap);
    }
}
