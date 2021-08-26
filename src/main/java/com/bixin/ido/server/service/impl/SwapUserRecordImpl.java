package com.bixin.ido.server.service.impl;

import com.bixin.ido.server.bean.DO.SwapUserRecord;
import com.bixin.ido.server.core.mapper.SwapUserRecordMapper;
import com.bixin.ido.server.service.ISwapUserRecordService;
import com.bixin.ido.server.utils.CaseUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhangcheng
 * create  2021-08-25 5:27 下午
 */
@Service
public class SwapUserRecordImpl implements ISwapUserRecordService {

    @Resource
    SwapUserRecordMapper swapUserRecordMapper;

    @Override
    public int insert(SwapUserRecord record) {
        return swapUserRecordMapper.insert(record);
    }

    @Override
    public List<SwapUserRecord> getALlByPage(long pageSize, long nextId) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("pageSize", pageSize);
        paramMap.put("sort", "liquidityTime");
        paramMap.put("order", "desc");

        CaseUtil.buildNoneValue(nextId, id -> paramMap.put("nextId", id));

        return swapUserRecordMapper.selectByPage(paramMap);
    }
}
