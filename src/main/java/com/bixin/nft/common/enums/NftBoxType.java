package com.bixin.nft.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author zhangcheng
 * create   2021/9/18
 */
@Getter
@AllArgsConstructor
public enum NftBoxType {


    NFT("nft"),
    BOX("box"),
    RECOMBINE("recombine"),
    ELEMENT("element");

    private String desc;

    public static NftBoxType of(String desc) {
        switch (desc) {
            case "nft":
                return NFT;
            case "box":
                return BOX;
            case "recombine":
                return RECOMBINE;
            case "element":
                return ELEMENT;
            default:
                return null;
        }
    }

}
