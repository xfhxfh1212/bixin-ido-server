package com.bixin.ido.server.bean.DO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdoSwapCoins {
    private Long id;

    private String shortName;

    private String fullName;

    private String icon;

    private String address;

    private Integer weight;

    private Short exchangePrecision;

    private Short displayPrecision;

    private Long createTime;

    private Long updateTime;

}