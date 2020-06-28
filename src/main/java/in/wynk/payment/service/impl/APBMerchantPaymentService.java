package in.wynk.payment.service.impl;

import in.wynk.commons.constants.Constants;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.Currency;
import in.wynk.commons.utils.CommonUtils;
import in.wynk.commons.utils.SessionUtils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.request.Apb.ApbPaymentCallbackRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.enums.Apb.ApbStatus;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.session.context.SessionContextHolder;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import static in.wynk.commons.constants.Constants.AMOUNT;
import static in.wynk.commons.constants.Constants.MSISDN;
import static in.wynk.commons.constants.Constants.SHA_512;
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

    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;

    @Value("${payment.failure.page}")
    private String FAILURE_PAGE;

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        ApbPaymentCallbackRequest apbPaymentCallbackRequest = (ApbPaymentCallbackRequest) callbackRequest.getBody();
        logger.info("ApbPaymentCallbackRequest: {}", apbPaymentCallbackRequest);
        if (apbPaymentCallbackRequest == null) {
            throw new RuntimeException("Received invalid callback");
        }
        ApbStatus status = apbPaymentCallbackRequest.getStatus();
        boolean verified = false;
        if (status == null) {
            throw new RuntimeException("Status is null");
        }
        try {
            if (status == ApbStatus.SUC) {
                verified = verifySuccessHash(apbPaymentCallbackRequest);
            } else if (status == ApbStatus.FAL) {
                verified = verifyFailureHash(apbPaymentCallbackRequest);
            }
            String sessionId = SessionContextHolder.get().getId().toString();
            String txnId = UUID.randomUUID().toString();// TODO fetch from Transaction object
            String url = String.format(FAILURE_PAGE, sessionId, txnId);
            if (verified) {
                if (status == ApbStatus.SUC) {
                    //TODO: update txn
                    String externalTxnId = apbPaymentCallbackRequest.getExternalTxnId();
                    // call subscription API
                    //push into RECON QUEUE
                    url = String.format(SUCCESS_PAGE, sessionId, txnId);
                } else {
                    String externalMessage = apbPaymentCallbackRequest.getMsg();
                    String externalTxnId = apbPaymentCallbackRequest.getTxnId();
                    //update txn with failure.
                }
            } else {
                throw new RuntimeException("Unable to verify");
            }

            return BaseResponse.redirectResponse(url);
        } catch (Exception e) {
            throw new RuntimeException("Exception Occurred");
        }
    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        //TODO: generate TXN
        URI apbRedirectURL = generateApbRedirectURL(chargingRequest.getTxnId());
        //TODO: push into RECON QUEUE
        return BaseResponse.redirectResponse(apbRedirectURL.toString());
    }

    private URI generateApbRedirectURL(String txnId) {
        try {
            long txnDate = System.currentTimeMillis();
            String serviceName = ApbService.NB.name();
            String formattedDate = CommonUtils.getFormattedDate(txnDate, "ddMMyyyyHHmmss");
            return getReturnUri(txnId, formattedDate, serviceName);
        } catch (Exception e) {
            throw new WynkRuntimeException(WynkErrorType.UT999, "Exception occurred while generating URL");
        }
    }

    @Override
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        //check from DB or APB
        return null;
    }

    private URI getReturnUri(String txnId, String formattedDate, String serviceName) throws Exception {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        double amount = SessionUtils.get(sessionDTO, AMOUNT, Double.class);
        String msisdn = SessionUtils.getString(sessionDTO, MSISDN);
        String hashText = MERCHANT_ID + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + formattedDate + Constants.HASH + serviceName + Constants.HASH + SALT;
        String hash = CommonUtils.generateHash(hashText, SHA_512);
        return new URIBuilder(APB_INIT_PAYMENT_URL)
                .addParameter(MID, MERCHANT_ID)
                .addParameter(TXN_REF_NO, txnId)
                .addParameter(SUCCESS_URL, getCallbackUrl(txnId).toASCIIString())
                .addParameter(FAILURE_URL, getCallbackUrl(txnId).toASCIIString())
                .addParameter(APB_AMOUNT, String.valueOf(amount))
                .addParameter(DATE, formattedDate)
                .addParameter(CURRENCY, Currency.INR.name())
                .addParameter(CUSTOMER_MOBILE, msisdn)
                .addParameter(MERCHANT_NAME, Constants.WYNK)
                .addParameter(HASH, hash)
                .addParameter(SERVICE, serviceName)
                .build();
    }

    private URI getCallbackUrl(String txnId) throws URISyntaxException {
        return new URIBuilder(CALLBACK_URL).addParameter("tid", txnId).build();
    }

    private boolean verifyFailureHash(ApbPaymentCallbackRequest apbPaymentCallbackRequest) throws NoSuchAlgorithmException {
        String str = apbPaymentCallbackRequest.getMerchantId() + Constants.HASH + apbPaymentCallbackRequest.getTxnId() + Constants.HASH + apbPaymentCallbackRequest.getAmount() + Constants.HASH + SALT + Constants.HASH + apbPaymentCallbackRequest.getCode() + "#FAL";
        String generatedHash = CommonUtils.generateHash(str, SHA_512);
        return apbPaymentCallbackRequest.getHash().equals(generatedHash);
    }

    private boolean verifySuccessHash(ApbPaymentCallbackRequest apbPaymentCallbackRequest) throws NoSuchAlgorithmException {
        String str = apbPaymentCallbackRequest.getMerchantId() + Constants.HASH + apbPaymentCallbackRequest.getExternalTxnId() + Constants.HASH + apbPaymentCallbackRequest.getTxnId() + Constants.HASH + apbPaymentCallbackRequest.getAmount() + Constants.HASH + apbPaymentCallbackRequest.getTxnDate() + Constants.HASH + SALT;
        String generatedHash = CommonUtils.generateHash(str, SHA_512);
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

        ApbService(String name) {
            this.name = name;
        }
    }

}
