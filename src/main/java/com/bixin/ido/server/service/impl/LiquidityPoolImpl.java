package com.bixin.ido.server.service.impl;

import com.bixin.ido.server.bean.DO.LiquidityPool;
import com.bixin.ido.server.core.mapper.LiquidityPoolMapper;
import com.bixin.ido.server.core.wrapDDL.LiquidityPoolDDL;
import com.bixin.ido.server.service.ILiquidityPoolService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author zhangcheng
 * create  2021-08-27 11:15 上午
 */
@Service
public class LiquidityPoolImpl implements ILiquidityPoolService {

    @Resource
    LiquidityPoolMapper liquidityPoolMapper;

    @Override
    public int insert(LiquidityPool record) {
        return liquidityPoolMapper.insert(record);
    }

    @Override
    public List<LiquidityPool> getAllPools() {
        LiquidityPoolDDL ddl = new LiquidityPoolDDL();
        return liquidityPoolMapper.selectByDDL(ddl);
    }


}
