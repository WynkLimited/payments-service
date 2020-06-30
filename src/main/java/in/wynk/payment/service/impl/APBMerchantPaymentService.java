package in.wynk.payment.service.impl;

import in.wynk.commons.constants.Constants;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.Currency;
import in.wynk.commons.utils.CommonUtils;
import in.wynk.commons.utils.SessionUtils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.ApbConstants;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.request.Apb.ApbChargingStatusRequest;
import in.wynk.payment.dto.request.Apb.ApbPaymentCallbackRequest;
import in.wynk.payment.dto.request.Apb.ApbTransactionInquiryRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.Apb.ApbChargingStatusResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.enums.Apb.ApbStatus;
import in.wynk.payment.enums.Apb.StatusMode;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.session.context.SessionContextHolder;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Value("${apb.txn.inquiry.url}")
    private String APB_TXN_INQUIRY_URL;

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        Map<String, List<String>> urlParameters = (Map<String, List<String>>)  callbackRequest.getBody();
        ApbStatus status = ApbStatus.valueOf(CommonUtils.getStringParameter(urlParameters, ApbConstants.STATUS));
        String code = CommonUtils.getStringParameter(urlParameters, ApbConstants.CODE);
        String externalMessage = CommonUtils.getStringParameter(urlParameters, ApbConstants.MSG);
        String merchantId = CommonUtils.getStringParameter(urlParameters, ApbConstants.MID);
        String externalTxnId = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_ID);
        String amount = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_AMT);
        String txnDate = CommonUtils.getStringParameter(urlParameters, ApbConstants.TRAN_DATE);
        String txnId = CommonUtils.getStringParameter(urlParameters, ApbConstants.TXN_REF_NO);
        String requestHash = CommonUtils.getStringParameter(urlParameters, ApbConstants.HASH);
        boolean verified = false;
        if (status == null) {
            throw new RuntimeException("Status is null");
        }
        try {
            verified = verifyHash(status, merchantId, txnId, externalTxnId, amount, txnDate, code, requestHash);
            String sessionId = SessionContextHolder.get().getId().toString();
            String txn_Id = UUID.randomUUID().toString();// TODO fetch from Transaction object
            String url = String.format(FAILURE_PAGE, sessionId, txnId);
            if (verified) {
                if (status == ApbStatus.SUC) {
                    //TODO: update txn
                    // externalTxnId
                    // call subscription API
                    //push into RECON QUEUE
                    url = String.format(SUCCESS_PAGE, sessionId, txnId);
                } else {
                    // externalMessage, externalTxnId
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
    public BaseResponse<ChargingStatusResponse> status(ChargingStatusRequest chargingStatusRequest) {
        ApbChargingStatusRequest apbChargingStatusRequest = (ApbChargingStatusRequest) chargingStatusRequest;
        if(apbChargingStatusRequest.getStatusMode() == StatusMode.MERCHANT_CHECK) {
            ApbChargingStatusResponse apbChargingStatusResponse =
                    fetchTxnStatus(apbChargingStatusRequest.getTxnId(), apbChargingStatusRequest.getAmount(), apbChargingStatusRequest.getTxnDate());
            return new BaseResponse<>(apbChargingStatusResponse, HttpStatus.OK, null);
        } else if(apbChargingStatusRequest.getStatusMode() == StatusMode.LOCAL_CHECK){
            //check from db
            return new BaseResponse<>(null, HttpStatus.OK, null);
        }
        return null;
    }

    private ApbChargingStatusResponse fetchTxnStatus(String txnId, double amount, long txnDate) {
        try {
            String formattedDate = CommonUtils.getFormattedDate(txnDate, "ddMMyyyyHHmmss");
            URI uri = new URI(APB_TXN_INQUIRY_URL);
            String hashText = MERCHANT_ID + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + formattedDate + Constants.HASH + SALT;
            String hashValue = CommonUtils.generateHash(hashText, SHA_512);
            ApbTransactionInquiryRequest apbTransactionInquiryRequest = ApbTransactionInquiryRequest.builder()
                    .feSessionId(UUID.randomUUID().toString())
                    .txnRefNo(txnId).txnDate(formattedDate)
                    .request(ECOMM_INQ).merchantId(MERCHANT_ID)
                    .hash(hashValue).langId(LANG_ID)
                    .amount(String.valueOf(amount))
                    .build();
            logger.info("ApbTransactionInquiryRequest: {}", apbTransactionInquiryRequest);
            RequestEntity<ApbTransactionInquiryRequest> requestEntity = new RequestEntity<>(apbTransactionInquiryRequest, HttpMethod.POST, uri);
            ApbChargingStatusResponse apbChargingStatusResponse = null;
            ResponseEntity<ApbChargingStatusResponse> responseEntity = restTemplate.exchange(requestEntity, ApbChargingStatusResponse.class);
            if(responseEntity != null) {
                apbChargingStatusResponse = responseEntity.getBody();
            }
            return apbChargingStatusResponse;
        } catch(HttpStatusCodeException e) {
            throw new RuntimeException("Http Status code exception occurred");
        } catch(Exception e){
            throw new RuntimeException("Unable to fetch transaction status for txnId = " + txnId);
        }
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

    private boolean verifyHash(ApbStatus status, String merchantId, String txnId, String externalTxnId, String amount, String txnDate, String code, String requestHash) throws NoSuchAlgorithmException {
        String str = null;
        if(status == ApbStatus.SUC) {
            str = merchantId + Constants.HASH + externalTxnId + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + txnDate + Constants.HASH + SALT;
        } else if (status == ApbStatus.FAL) {
            str = merchantId + Constants.HASH + txnId + Constants.HASH + amount + Constants.HASH + SALT + Constants.HASH + code + "#FAL";
        }
        String generatedHash = CommonUtils.generateHash(str, SHA_512);
        return requestHash.equals(generatedHash);
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
