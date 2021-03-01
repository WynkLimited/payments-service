package in.wynk.payment.test.payu.testcases.verify;

import com.google.gson.Gson;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.PayUVpaVerificationResponse;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.test.config.PaymentTestConfiguration;
import in.wynk.payment.test.payu.data.PayUTestData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = PaymentTestConfiguration.class)
public class PayUPaymentVerifyTest {

    @Value("${payment.merchant.payu.key}")
    private String payUMerchantKey;

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;

    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private Gson gson;

    private IMerchantVerificationService verificationService;

    @Before
    public void setup() {
        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildValidVPAVerificationRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildValidVPAPayUTransactionStatusResponse());
        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildValidBINVerificationRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildValidBINPayUTransactionStatusResponse());
        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildInValidVPAVerificationRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildInvalidVPAPayUTransactionStatusResponse());
        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildInValidBINVerificationRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildInvalidBINPayUTransactionStatusResponse());
        verificationService = BeanLocatorFactory.getBean(PaymentCode.PAYU.getCode(), IMerchantVerificationService.class);
    }

    @Test
    @Order(1)
    public void verifyValidVPA() {
        PayUVpaVerificationResponse payuVpaVerificationResponse = gson.fromJson(PayUTestData.buildValidVPAPayUTransactionStatusResponse(), PayUVpaVerificationResponse.class);
        BaseResponse<?> response = verificationService.doVerify(VerificationRequest.builder().paymentCode(PaymentCode.PAYU).verificationType(VerificationType.VPA).verifyValue("valid_random_vpa").build());
        PayUVpaVerificationResponse payUResponse = gson.fromJson((String) response.getBody(), PayUVpaVerificationResponse.class);
        Assert.assertNotNull(payUResponse);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertEquals(payUResponse.getPayerAccountName(), payuVpaVerificationResponse.getPayerAccountName());
        Assert.assertEquals(payUResponse.getIsVPAValid(), payuVpaVerificationResponse.getIsVPAValid());
        Assert.assertEquals(payUResponse.getStatus(), payuVpaVerificationResponse.getStatus());
        Assert.assertEquals(payUResponse.getVpa(), payuVpaVerificationResponse.getVpa());
        Assert.assertEquals(payUResponse.isValid(), true);

    }

    @Test
    @Order(2)
    public void verifyInValidVPA() {
        PayUVpaVerificationResponse payuVpaVerificationResponse = gson.fromJson(PayUTestData.buildInvalidVPAPayUTransactionStatusResponse(), PayUVpaVerificationResponse.class);
        BaseResponse<?> response = verificationService.doVerify(VerificationRequest.builder().paymentCode(PaymentCode.PAYU).verificationType(VerificationType.VPA).verifyValue("invalid_random_vpa").build());
        PayUVpaVerificationResponse payUResponse = gson.fromJson((String) response.getBody(), PayUVpaVerificationResponse.class);
        Assert.assertNotNull(payUResponse);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertEquals(payUResponse.getPayerAccountName(), payuVpaVerificationResponse.getPayerAccountName());
        Assert.assertEquals(payUResponse.getIsVPAValid(), payuVpaVerificationResponse.getIsVPAValid());
        Assert.assertEquals(payUResponse.getStatus(), payuVpaVerificationResponse.getStatus());
        Assert.assertEquals(payUResponse.getVpa(), payuVpaVerificationResponse.getVpa());
        Assert.assertEquals(payUResponse.isValid(), false);

    }

    @Test
    @Order(3)
    public void verifyValidBIN() {
        PayUCardInfo cardInfo = gson.fromJson(PayUTestData.buildValidBINPayUTransactionStatusResponse(), PayUCardInfo.class);
        BaseResponse<?> response = verificationService.doVerify(VerificationRequest.builder().paymentCode(PaymentCode.PAYU).verificationType(VerificationType.BIN).verifyValue("valid_random_bin").build());
        PayUCardInfo responseBody = gson.fromJson((String) response.getBody(), PayUCardInfo.class);
        Assert.assertNotNull(responseBody);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertEquals(responseBody.getCardType(), cardInfo.getCardType());
        Assert.assertEquals(responseBody.getIsDomestic(), cardInfo.getIsDomestic());
        Assert.assertEquals(responseBody.getIssuingBank(), cardInfo.getIssuingBank());
        Assert.assertEquals(responseBody.getCardCategory(), cardInfo.getCardCategory());
        Assert.assertEquals(responseBody.isValid(), true);
    }

    @Test
    @Order(4)
    public void verifyInValidBIN() {
        PayUCardInfo cardInfo = gson.fromJson(PayUTestData.buildInvalidBINPayUTransactionStatusResponse(), PayUCardInfo.class);
        BaseResponse<?> response = verificationService.doVerify(VerificationRequest.builder().paymentCode(PaymentCode.PAYU).verificationType(VerificationType.BIN).verifyValue("invalid_random_bin").build());
        PayUCardInfo responseBody = gson.fromJson((String) response.getBody(), PayUCardInfo.class);
        Assert.assertNotNull(responseBody);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertEquals(responseBody.getCardType(), cardInfo.getCardType());
        Assert.assertEquals(responseBody.getIsDomestic(), cardInfo.getIsDomestic());
        Assert.assertEquals(responseBody.getIssuingBank(), cardInfo.getIssuingBank());
        Assert.assertEquals(responseBody.getCardCategory(), cardInfo.getCardCategory());
        Assert.assertEquals(responseBody.isValid(), false);
    }



}
