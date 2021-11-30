package com.bixin.ido.server.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bixin.ido.server.entity.NftMiningUsers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

/**
 * 用户NFT挖矿表 Mapper 接口
 *
 * @author Xiang Feihan
 * @since 2021-11-26
 */
@Mapper
public interface NftMiningUsersMapper extends BaseMapper<NftMiningUsers> {

    /**
     * 计算发放收益
     *
     * @param rewardAmount
     * @param updateTime
     * @return
     */
    int computeReward(@Param("rewardAmount") BigDecimal rewardAmount, @Param("updateTime") Long updateTime);

    /**
     * 获取总分数
     *
     * @return
     */
    BigDecimal totalScore();

}
