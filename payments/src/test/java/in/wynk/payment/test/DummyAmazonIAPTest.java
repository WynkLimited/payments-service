package in.wynk.payment.test;

import in.wynk.common.dto.SessionDTO;
import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.amazonIap.Receipt;
import in.wynk.payment.dto.amazonIap.UserData;
import in.wynk.payment.service.PaymentManager;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.collections4.map.HashedMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {HttpClientConfig.class, PaymentApplication.class})
public class DummyAmazonIAPTest {

    @Autowired
    private PaymentManager paymentManager;

    public AmazonIapVerificationRequest getDummyData() {
        SessionContextHolder.set(Session.builder()
                .body(SessionDTO.builder().sessionPayload(new HashedMap<>()).build())
                .id(UUID.fromString("85efab22-6eb8-425d-acb7-6a0fa6015fa6"))
                .build());
        return AmazonIapVerificationRequest.builder()
                .uid("wA57HOCySewPNcuCe0")
                .deviceId("f67bb47448024fde")
                .msisdn("919538014167")
                .service("")
                .sid("85efab22-6eb8-425d-acb7-6a0fa6015fa6")
                .receipt(Receipt.builder()
                        .receiptId("DctBC8IgFADg__LOS3BrioNui8Yqdoigq9Vogm7u-ZQi-u95_74vRGjAVt1J3rzfnyccDu0cUW2iTevi8PIS62doj05PUtj-uoMCQi6UmDZIo2XvQDhqxyhpz0K8hwcaT2aZsyRoygKe2fNabUsuVM0rIeH3Bw")
                        .sku("com.amazon.sample.iap.consumable.orange")
                        .build())
                .userData(UserData.builder().userId("l3HL7XppEMhrOGDnur9-ulvqomrSg6qyODKmah76lJU=").build())
                .build();
    }

    @Test
    public void test1() {
        paymentManager.doVerifyIap(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString(), getDummyData());
        Assert.assertTrue(true);
    }

}