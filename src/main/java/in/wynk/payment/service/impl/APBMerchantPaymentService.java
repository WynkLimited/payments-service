package in.wynk.payment.service.impl;

import in.wynk.commons.constants.Constants;
import in.wynk.commons.enums.Currency;
import in.wynk.commons.enums.HashAlgorithm;
import in.wynk.commons.enums.Status;
import in.wynk.commons.utils.CommonUtils;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.request.Apb.ApbPaymentCallbackRequest;
import in.wynk.payment.dto.response.Apb.ApbChargingResponse;
import in.wynk.payment.dto.response.Apb.ApbPaymentCallbackResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.enums.Apb.ApbStatus;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

import static in.wynk.payment.constant.ApbConstants.*;

@Service(BeanConstant.APB_MERCHANT_PAYMENT_SERVICE)
public class APBMerchantPaymentService implements IRenewalMerchantPaymentService {
    private static final Logger logger = LoggerFactory.getLogger(APBMerchantPaymentService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${apb.callback.url}")
    private String CALLBACK_URL;

    @Value("${apb.merchant.id}")
    private String MERCHANT_ID;

    @Value("${apb.salt}")
    private String SALT;

    @Value("${apb.init.payment.url}")
    private String APB_INIT_PAYMENT_URL;

    @Override
    public <T> BaseResponse<T> handleCallback(CallbackRequest callbackRequest) {
        ApbPaymentCallbackRequest apbPaymentCallbackRequest = (ApbPaymentCallbackRequest)callbackRequest.getBody();
        logger.info("ApbPaymentCallbackRequest: {}", apbPaymentCallbackRequest);
        if(apbPaymentCallbackRequest == null){
            throw new RuntimeException("Received invalid callback");
        }
        ApbStatus status = apbPaymentCallbackRequest.getStatus();
        boolean verified = false;
        if(status == null){
            throw new RuntimeException("Status is null");
        }
        try {
            if (status == ApbStatus.SUC) {
                verified = verifySuccessHash(apbPaymentCallbackRequest);
            } else if (status == ApbStatus.FAL) {
                verified = verifyFailureHash(apbPaymentCallbackRequest);
            }
            ApbPaymentCallbackResponse paymentCallbackResponse = new ApbPaymentCallbackResponse();
            if (verified) {
                if (status == ApbStatus.SUC) {
                    paymentCallbackResponse.setPaymentStatus(Status.SUCCESS);
                    paymentCallbackResponse.setExtTxnId(apbPaymentCallbackRequest.getExternalTxnId());
                } else {
                    paymentCallbackResponse.setPaymentStatus(Status.FAILURE);
                }
                paymentCallbackResponse.setMsg(apbPaymentCallbackRequest.getMsg());
                paymentCallbackResponse.setTxnId(apbPaymentCallbackRequest.getTxnId());
            } else {
                throw new RuntimeException("Unable to verify");
            }
            return new BaseResponse(paymentCallbackResponse, HttpStatus.OK, null);
        } catch(Exception e) {
            throw new RuntimeException("Exception Occurred");
        }
    }

    @Override
    public <T> BaseResponse<T> doCharging(ChargingRequest chargingRequest) {
        ApbChargingResponse apbChargingResponse = deductMoney(chargingRequest.getTxnId());
        // create subscription in sync manner
        String uid = "987"; //fetch from session
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(apbChargingResponse.getReturnUri());

        logger.info("Redirecting user, uid: {}, return URL: {}", uid,
                apbChargingResponse.getReturnUri().getHost());

        return new BaseResponse(null, HttpStatus.FOUND, headers);
    }

    private ApbChargingResponse deductMoney(String txnId) {
        try {
            ApbChargingResponse apbChargingResponse = new ApbChargingResponse();
            long txnDate = System.currentTimeMillis();
            String serviceName = ApbService.NB.name();
            String formattedDate = CommonUtils.getFormattedDate(txnDate, "ddMMyyyyHHmmss");
            URI returnUri = getReturnUri(txnId, formattedDate, serviceName);
            apbChargingResponse.setReturnUri(returnUri);
            apbChargingResponse.setTxnDate(new Date(txnDate));
            return apbChargingResponse;
        } catch(Exception e) {
            throw new RuntimeException("Exception occurred");
        }
    }

    @Override
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        //check from DB
        return null;
    }

    private URI getReturnUri(String txnId, String formattedDate, String serviceName) throws Exception {
        double amt = 1.00; //fetch from session
        String msisdn = "9149513172"; //fetch from session
        String hashText = MERCHANT_ID + Constants.HASH_SYMBOL + txnId + Constants.HASH_SYMBOL + amt + Constants.HASH_SYMBOL + formattedDate + Constants.HASH_SYMBOL + serviceName + Constants.HASH_SYMBOL + SALT;
        return new URIBuilder(APB_INIT_PAYMENT_URL)
            .addParameter(MID, MERCHANT_ID)
            .addParameter(TXN_REF_NO, txnId)
            .addParameter(SUCCESS_URL, getCallbackUrl(txnId).toASCIIString())
            .addParameter(FAILURE_URL, getCallbackUrl(txnId).toASCIIString())
            .addParameter(AMOUNT, String.valueOf(amt))
            .addParameter(DATE, formattedDate)
            .addParameter(CURRENCY, Currency.INR.name())
            .addParameter(CUSTOMER_MOBILE, msisdn)
            .addParameter(MERCHANT_NAME, Constants.WYNK)
            .addParameter(HASH, CommonUtils.generateHash(hashText, HashAlgorithm.SHA_512))
            .addParameter(SERVICE, serviceName)
            .build();
    }

    private URI getCallbackUrl(String txnId) throws URISyntaxException {
        return new URIBuilder(CALLBACK_URL).addParameter("tid", txnId).build();
    }

    private boolean verifyFailureHash(ApbPaymentCallbackRequest apbPaymentCallbackRequest) throws NoSuchAlgorithmException {
        String str = apbPaymentCallbackRequest.getMerchantId() + Constants.HASH_SYMBOL + apbPaymentCallbackRequest.getTxnId()+ Constants.HASH_SYMBOL + apbPaymentCallbackRequest.getAmount() + Constants.HASH_SYMBOL + SALT + Constants.HASH_SYMBOL + apbPaymentCallbackRequest.getCode() + "#FAL";
        String generatedHash = CommonUtils.generateHash(str, HashAlgorithm.SHA_512);
        return apbPaymentCallbackRequest.getHash().equals(generatedHash);
    }

    private boolean verifySuccessHash(ApbPaymentCallbackRequest apbPaymentCallbackRequest) throws NoSuchAlgorithmException {
        String str =  apbPaymentCallbackRequest.getMerchantId() + Constants.HASH_SYMBOL + apbPaymentCallbackRequest.getExternalTxnId() + Constants.HASH_SYMBOL + apbPaymentCallbackRequest.getTxnId()+ Constants.HASH_SYMBOL + apbPaymentCallbackRequest.getAmount() + Constants.HASH_SYMBOL + apbPaymentCallbackRequest.getTxnDate() + Constants.HASH_SYMBOL + SALT;
        String generatedHash = CommonUtils.generateHash(str, HashAlgorithm.SHA_512);
        return apbPaymentCallbackRequest.getHash().equals(generatedHash);
    }

    @Override
    public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        throw new RuntimeException("Unsupported Exception");
    }

    public enum ApbService {
        NB("NetBanking"),
        WT("Wallet");

        String name;

        ApbService(String name){
            this.name = name;
        }
    }

}
