package in.wynk.payment.service.impl;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.ErrorCode;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.apb.paytm.*;
import in.wynk.payment.dto.phonepe.PhonePeStatusEnum;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.Apb.paytm.APBPaytmResponse;
import in.wynk.payment.dto.response.Apb.paytm.APBPaytmResponseData;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET_USER_ID;
import static in.wynk.payment.dto.apb.paytm.APBPaytmConstants.*;

@Slf4j
@Service(BeanConstant.APB_PAYTM_MERCHANT_WALLET_SERVICE)
public class APBPaytmMerchantWalletPaymentService extends AbstractMerchantPaymentStatusService implements IRenewalMerchantWalletService{
    @Value("${payment.encKey}")
    private String paymentEncryptionKey;
    @Value("${paytm.native.wcf.callbackUrl}")
    private String callBackUrl;
    @Value("${payment.merchant.apbPaytm.api.base.url}")
    private String apbPaytmBaseUrl;
    @Value("${payment.success.page}")
    private String successPage;
    @Value("${payment.failure.page}")
    private String failurePage;
    private final ObjectMapper objectMapper;
    private final Gson gson;
    private final RestTemplate restTemplate;
    private final IUserPaymentsManager userPaymentsManager;
    private final CheckSumServiceHelper checkSumServiceHelper;
    private final PaymentCachingService paymentCachingService;
    private final ApplicationEventPublisher eventPublisher;

    public APBPaytmMerchantWalletPaymentService(ObjectMapper objectMapper, Gson gson, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, IUserPaymentsManager userPaymentsManager, PaymentCachingService paymentCachingService, ApplicationEventPublisher eventPublisher) {
        super(paymentCachingService);
        this.objectMapper = objectMapper;
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.userPaymentsManager = userPaymentsManager;
        this.paymentCachingService = paymentCachingService;
        this.eventPublisher = eventPublisher;
        this.checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override
    public BaseResponse<?> linkRequest(WalletLinkRequest walletLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        sessionDTO.put(WALLET_USER_ID, walletLinkRequest.getEncSi());

        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            APBPaytmLinkRequest linkRequest = APBPaytmLinkRequest.builder().walletLoginId(walletLinkRequest.getEncSi()).
                    wallet(WALLET_PAYTM).build();
            HttpHeaders headers= generateHeaders();
            HttpEntity<APBPaytmLinkRequest> requestEntity = new HttpEntity<APBPaytmLinkRequest>(linkRequest,headers);
            APBPaytmResponse linkResponse =restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_SEND_OTP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            APBPaytmResponse response = linkResponse;
            if (response.isResult()) {
                sessionDTO.put(ABP_PAYTM_OTP_TOKEN, response.getData().getOtpToken());
                log.info("otp send successfully {} ", response.getData().getOtpToken());

            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(response.getErrorCode());
            }
        }
        catch (HttpStatusCodeException hex) {
            log.error("APB_PAYTM_OTP_SEND_FAILURE", hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error("APB_PAYTM_OTP_SEND_FAILURE", e.getMessage());
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.build()).build();
        }
    }

    private HttpHeaders generateHeaders(){
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTHORIZATION, ABP_PAYTM_AUTHORIZATION);
        headers.add(CHANNEL_ID,ABP_PAYTM_CHANNEL_ID);
        headers.add(CONTENT_TYPE, ABP_PAYTM_CONTENT_TYPE);
        headers.add(ACCEPT, ABP_PAYTM_ACCEPT);
        return headers;
    }
    @Override
    public BaseResponse<?> validateLink(WalletValidateLinkRequest request) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String loginId=sessionDTO.get(WALLET_USER_ID);
            String otpToken=sessionDTO.get(ABP_PAYTM_OTP_TOKEN);
            APBPaytmLinkRequest abpPaytmLinkRequest=APBPaytmLinkRequest.builder().walletLoginId(loginId).
            channel(CHANNEL_WEB).wallet(WALLET_PAYTM).authType("UN_AUTH").otp(request.getOtp()).otpToken(otpToken).build();
            HttpHeaders headers = generateHeaders();
            HttpEntity<APBPaytmLinkRequest> requestEntity = new HttpEntity<APBPaytmLinkRequest>(abpPaytmLinkRequest,headers);
            APBPaytmResponse linkResponse =restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_VERIFY_OTP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            if (linkResponse!=null && linkResponse.isResult()) {
                sessionDTO.put(ABP_PAYTM_ENCRYPTED_TOKEN, linkResponse.getData().getEncryptedToken());
                sessionDTO.put(ABP_PAYTM_BALANCE_AMOUNT,linkResponse.getData().getBalance());
                log.info("otp validated successfully {}", linkResponse.getData().getEncryptedToken());

            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode("");
            }
        }
        catch (HttpStatusCodeException hex) {
        log.error("APB_PAYTM_OTP_SEND_FAILURE", hex.getResponseBodyAsString());
        errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
    } catch (Exception e) {
        log.error("APB_PAYTM_OTP_SEND_FAILURE", e.getMessage());
        errorCode = ErrorCode.UNKNOWN;
        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    } finally {
        if (Objects.nonNull(errorCode)) {
            builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
        }
        return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.build()).build();
    }

    }

    @Override
    public BaseResponse<?> unlink() {
        return null;
    }

    @Override
    public BaseResponse<?> balance(int planId) {
        return null;
    }


    @Override
    public BaseResponse<?> addMoney(WalletAddMoneyRequest request) {
        return new BaseResponse<>(addMoney(), HttpStatus.OK, null);

    }

    private APBPaytmResponse addMoney(){
        Transaction transaction= TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        ErrorCode errorCode = null;
        ChargingResponse chargingResponse = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            String loginId=sessionDTO.get(WALLET_USER_ID);
            String encryptedToken=sessionDTO.get(ABP_PAYTM_ENCRYPTED_TOKEN);
            APBPaytmTopUpRequest topUpRequest=APBPaytmTopUpRequest.builder().
                    encryptedToken(encryptedToken).
                    authType(AUTH_TYPE_WEB_UNAUTH).
                    channel(CHANNEL_WEB).
                    userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).build()).
                    topUpInfo(APBTopUpInfo.builder().wallet(WALLET_PAYTM).paymentMode(WALLET).topUpAmount(transaction.getAmount()).currency(CURRENCY_INR).walletLoginId(loginId).
                            data(APBPaytmRequestData.builder().returnUrl(callBackUrl+SessionContextHolder.getId()).build()).build()).build();
            HttpHeaders headers = generateHeaders();
            HttpEntity<APBPaytmTopUpRequest> requestEntity = new HttpEntity<APBPaytmTopUpRequest>(topUpRequest,headers);
            APBPaytmResponse topUpResponse = restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_TOP_UP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            return topUpResponse;
        }
        catch (HttpStatusCodeException hex) {
            AnalyticService.update("APB_PAYTM_ADD_MONEY_FAILURE", hex.getRawStatusCode());
            log.error("APB_PAYTM_ADD_MONEY_FAILURE", hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            APBPaytmResponse topUpResponse = gson.fromJson(errorResponse, APBPaytmResponse.class);
            return topUpResponse;

        } catch (Exception e) {
            log.error("APB_PAYTM_ADD_MONEY_FAILURE", e.getMessage(), e);
            return APBPaytmResponse.builder().result(false).data(APBPaytmResponseData.builder().html(null).build()).build();
        }

    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error("APB_PAYTM_CHARGING_STATUS_VERIFICATION", "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "Transaction is still pending at phonepe");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error("APB_PAYTM_CHARGING_STATUS_VERIFICATION", "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "APB_PAYTM_CHARGING_STATUS_VERIFICATION");
        }
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    private APBPaytmResponse getTransactionStatus(Transaction txn) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(txn.getIdStr());
        try {
            HttpHeaders headers= generateHeaders();
            HttpEntity<HttpHeaders> requestEntity = new HttpEntity<HttpHeaders>(headers);
            APBPaytmResponse statusResponse =restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_TRANSACTION_STATUS+txn.getIdStr(), HttpMethod.GET, requestEntity, APBPaytmResponse.class).getBody();
            if (statusResponse != null && statusResponse.isResult() ) {
                merchantTransactionEventBuilder.externalTransactionId(statusResponse.getData().getPgId());
            }
            merchantTransactionEventBuilder.response(objectMapper.writeValueAsString(statusResponse));
            return statusResponse;
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            log.error("APB_PAYTM_CHARGING_STATUS_VERIFICATION_FAILURE", "Error from phonepe: {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, "Error from PhonePe " + e.getStatusCode().toString());
        } catch (Exception e) {
            log.error("APB_PAYTM_CHARGING_STATUS_VERIFICATION_FAILURE", "Unable to verify status from Phonepe");
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e,e.getMessage());
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {

        TransactionStatus finalTransactionStatus;
        APBPaytmResponse response = getTransactionStatus(transaction);
        if (response.isResult()) {
            String statusCode = response.getData().getPaymentStatus();
            if (statusCode == "PAYMENT_SUCCESS") {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    statusCode == "PAYMENT_PENDING") {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }
        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(response.getErrorCode()).description(response.getErrorCode()).build());
        }
        transaction.setStatus(finalTransactionStatus.name());
    }

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        String redirectUrl = null;
        Transaction transaction= TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String sid = SessionContextHolder.getId();
        ChargingResponse.ChargingResponseBuilder chargingResponseBuilder = ChargingResponse.builder();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<ChargingResponse>builder();
        try {
            String encryptedToken = sessionDTO.get(ABP_PAYTM_ENCRYPTED_TOKEN);
            String loginId=sessionDTO.get(WALLET_USER_ID);
            final double amountToCharge=transaction.getAmount();
            APBPaytmWalletPaymentRequest walletPaymentRequest = APBPaytmWalletPaymentRequest.builder()
                    .orderId(transaction.getIdStr())
                    .channel(CHANNEL_WEB)
                    .userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).serviceInstance(loginId).build())
                    .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(null).encodedParams(false).build())
                    .paymentInfo(APBPaytmPaymentInfo.builder().lob(WYNK).paymentAmount(amountToCharge).paymentMode(WALLET).wallet(WALLET_PAYTM).currency(CURRENCY_INR).walletLoginId(loginId).encryptedToken(encryptedToken).build()).build();

            HttpHeaders headers = generateHeaders();
            HttpEntity<APBPaytmWalletPaymentRequest> requestEntity = new HttpEntity<APBPaytmWalletPaymentRequest>(walletPaymentRequest,headers);
            APBPaytmResponse chargeResponse = restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_WALLET_PAYMENT, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();

            if (chargeResponse!=null && chargeResponse.isResult() && chargeResponse.getData().getPaymentStatus().equalsIgnoreCase("PAYMENT_SUCCESS")) {
                transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                redirectUrl=successPage+sid;

                log.info("redirectUrl {}", redirectUrl);
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode("");
            }
        }
        catch (HttpStatusCodeException hex) {
            log.error("APB_PAYTM_CHARGE_FAILURE", hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error("APB_PAYTM_CHARGE_FAILURE", e.getMessage());
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }

            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = failurePage+sid;
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }
            handleError(errorCode, builder);
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.data(chargingResponseBuilder
                    .redirectUrl(redirectUrl +
                            SLASH +
                            sessionDTO.<String>get(OS) +
                            QUESTION_MARK +
                            SERVICE +
                            EQUAL +
                            sessionDTO.<String>get(SERVICE) +
                            AND +
                            BUILD_NO +
                            EQUAL +
                            sessionDTO.<Integer>get(BUILD_NO))
                    .build()).build()).build();
        }

        }
        private APBPaytmResponse getBalance()
        {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String loginId = sessionDTO.get(WALLET_USER_ID);
            String encryptedToken = sessionDTO.get(ABP_PAYTM_ENCRYPTED_TOKEN);
            try {
                Map map = new HashMap();
                map.put("walletLoginId", loginId);
                map.put("wallet", "PAYTM");
                map.put("encryptedToken", encryptedToken);
                String requestBalance = objectMapper.writeValueAsString(map);
                HttpHeaders headers = generateHeaders();
                HttpEntity<String> requestEntityForBalance = new HttpEntity<String>(requestBalance, headers);
                APBPaytmResponse balanceResponse = restTemplate.exchange(
                        apbPaytmBaseUrl + ABP_PAYTM_GET_BALANCE, HttpMethod.POST, requestEntityForBalance, APBPaytmResponse.class).getBody();
                return balanceResponse;
            }
            catch (Exception e){
                return APBPaytmResponse.builder().result(false).build();
            }

        }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        String redirectUrl = null;
        Transaction transaction= TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String sid = SessionContextHolder.getId();
        ChargingResponse.ChargingResponseBuilder chargingResponseBuilder = ChargingResponse.builder();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<ChargingResponse>builder();
        try {
            String loginId = sessionDTO.get(WALLET_USER_ID);
            String encryptedToken = sessionDTO.get(ABP_PAYTM_ENCRYPTED_TOKEN);
            HttpHeaders headers = generateHeaders();
            final double amountToCharge = 1;
            APBPaytmResponse balanceResponse = this.getBalance();
            if (balanceResponse.isResult() && balanceResponse.getData().getBalance() < amountToCharge) {
                APBPaytmResponse topUpResponse = this.addMoney();
                if (topUpResponse.isResult() && topUpResponse.getData().getHtml() != null) {
                    chargingResponseBuilder.deficit(true).info(EncryptionUtils.encrypt(topUpResponse.getData().getHtml(), paymentEncryptionKey));
        //            chargingResponseBuilder.deficit(true).info(topUpResponse.getData().getHtml());
                    log.info("topUp Response {}", topUpResponse);
                } else {
                    errorCode = ErrorCode.getErrorCodesFromExternalCode("");
                }
            } else if(balanceResponse.isResult() && balanceResponse.getData().getBalance()>=amountToCharge) {

                APBPaytmWalletPaymentRequest walletPaymentRequest = APBPaytmWalletPaymentRequest.builder()
                        .orderId(transaction.getIdStr())
                        .channel(CHANNEL_WEB)
                        .userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).serviceInstance(loginId).build())
                        .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(successPage+sid).channel(AUTH_TYPE_WEB_UNAUTH).build())
                        .paymentInfo(APBPaytmPaymentInfo.builder().lob(WYNK).paymentAmount(1).paymentMode(WALLET).wallet(WALLET_PAYTM).currency(CURRENCY_INR).walletLoginId(loginId).encryptedToken(encryptedToken).build()).build();


                HttpEntity<APBPaytmWalletPaymentRequest> requestEntity = new HttpEntity<APBPaytmWalletPaymentRequest>(walletPaymentRequest, headers);
                APBPaytmResponse paymentResponse = restTemplate.exchange(
                        apbPaytmBaseUrl+ABP_PAYTM_WALLET_PAYMENT, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
                if(paymentResponse.isResult() && paymentResponse.getData().getPaymentStatus()!=null && paymentResponse.getData().getPaymentStatus().equalsIgnoreCase("PAYMENT_SUCCESS") )
                {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    redirectUrl=successPage+sid;
                }
            }

        }
        catch (HttpStatusCodeException hex) {
            log.error("APB_PAYTM_CHARGE_FAILURE", hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error("APB_PAYTM_CHARGE_FAILURE", e.getMessage());
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally
        {
            if (StringUtils.isBlank(redirectUrl)) {
                transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                redirectUrl = failurePage+sid;

        }
        handleError(errorCode, builder);
        return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.data(chargingResponseBuilder
                .redirectUrl(redirectUrl +
                        SLASH +
                        sessionDTO.<String>get(OS) +
                        QUESTION_MARK +
                        SERVICE +
                        EQUAL +
                        sessionDTO.<String>get(SERVICE) +
                        AND +
                        BUILD_NO +
                        EQUAL +
                        sessionDTO.<Integer>get(BUILD_NO))
                .build()).build()).build();
    }


}

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {

    }

    private void handleError(ErrorCode errorCode, WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder) {
        if (Objects.nonNull(errorCode)) {
            if (errorCode == ErrorCode.UNKNOWN) {
                builder.error(TechnicalErrorDetails.builder().code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build()).success(false);
            } else {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
        }
    }
}