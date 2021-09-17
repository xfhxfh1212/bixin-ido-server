package com.bixin.nft.core.service;

import com.bixin.IdoServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.math.BigInteger;

@ActiveProfiles("local")
@SpringBootTest(classes = IdoServerApplication.class)
class NftInitServiceTest {
    @Resource
    private NftInitService nftInitService;
    @Resource
    private ContractService contractService;


    @Test
    void deployNftMarket() {
        nftInitService.initNFTMarket(new BigInteger("10"), new BigInteger("10"));
    }

    @Test
    void initNFT() {
        nftInitService.createNFT();
    }

}