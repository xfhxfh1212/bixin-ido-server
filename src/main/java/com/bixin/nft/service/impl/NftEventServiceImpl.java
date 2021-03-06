package com.bixin.nft.service.impl;

import com.bixin.common.utils.CaseUtil;
import com.bixin.nft.bean.DO.NftEventDo;
import com.bixin.nft.core.mapper.NftEventMapper;
import com.bixin.nft.service.NftEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @class: NftEventServiceImpl
 * @Description:  nft事件表 接口实现
 * @author: 系统
 * @created: 2021-09-22
 */
@Slf4j
@Service
public class NftEventServiceImpl implements NftEventService {

    @Autowired
    private NftEventMapper nftEventMapper;

    /**
     * @explain: 添加NftEventDo对象
     * @param:   model 对象参数
     * @return:  int
     */
    @Override
    public int insert(NftEventDo model) {
        return nftEventMapper.insert(model);
    }

    /**
     * @explain: 删除NftEventDo对象
     * @param:   id
     * @return:  int
     */
    @Override
    public int deleteById(Long id) {
        return nftEventMapper.deleteByPrimaryKey(id);
    }

    /**
     * @explain: 修改NftEventDo对象
     * @param:   model 对象参数
     * @return:  int
     */
    @Override
    public int update(NftEventDo model) {
        return nftEventMapper.updateByPrimaryKeySelective(model);
    }

    /**
     * @explain: 查询NftEventDo对象
     * @param:   id
     * @return:  NftEventDo
     */
    @Override
    public NftEventDo selectById(Long id) {
        return nftEventMapper.selectByPrimaryKey(id);
    }

    /**
     * @explain: 查询NftEventDo对象
     * @param:   model 对象参数
     * @return:  NftEventDo 对象
     */
    @Override
    public NftEventDo selectByObject(NftEventDo model) {
        return nftEventMapper.selectByPrimaryKeySelective(model);
    }

    /**
     * @explain: 查询列表
     * @param:  model  对象参数
     * @return: list
     */
    @Override
    public List<NftEventDo> listByObject(NftEventDo model) {
        return nftEventMapper.selectByPrimaryKeySelectiveList(model);
    }

    @Override
    public List<NftEventDo> getALlByPage(Long infoId, String type, long pageSize, long nextId) {
        Map<String, Object> paramMap = new HashMap<>();
        CaseUtil.buildNoneValue(infoId, id -> paramMap.put("infoId", infoId));
        Optional.ofNullable(type).filter(StringUtils::isNotBlank).ifPresent(data -> paramMap.put("type", data));
        paramMap.put("pageSize", pageSize);
        paramMap.put("sort", "id");
        paramMap.put("order", "desc");
        CaseUtil.buildNoneValue(nextId, id -> paramMap.put("nextId", id));
        return nftEventMapper.selectByPage(paramMap);
    }

    @Override
    public List<NftEventDo> getALlByBoxId(Long boxId, String type, long pageSize, long nextId) {
        Map<String, Object> paramMap = new HashMap<>();
        CaseUtil.buildNoneValue(boxId, id -> paramMap.put("nftId", boxId));
        Optional.ofNullable(type).filter(StringUtils::isNotBlank).ifPresent(data -> paramMap.put("type", data));
        paramMap.put("pageSize", pageSize);
        paramMap.put("sort", "id");
        paramMap.put("order", "desc");
        CaseUtil.buildNoneValue(nextId, id -> paramMap.put("nextId", id));
        return nftEventMapper.selectByPage(paramMap);
    }

    @Override
    public List<String> selectAllAddress() {
        return nftEventMapper.selectAllAddress();
    }

}
