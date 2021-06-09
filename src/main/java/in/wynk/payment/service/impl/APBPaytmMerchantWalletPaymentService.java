package in.wynk.payment.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.ErrorCode;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.apb.paytm.*;
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
import static in.wynk.payment.core.constant.PaymentConstants.WALLET_USER_ID;
import static in.wynk.payment.dto.apb.paytm.APBPaytmConstants.*;

@Slf4j
@Service(BeanConstant.APB_PAYTM_MERCHANT_WALLET_SERVICE)
public class APBPaytmMerchantWalletPaymentService extends AbstractMerchantPaymentStatusService implements IRenewalMerchantWalletService, IUserPreferredPaymentService, IMerchantPaymentRefundService {
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
    private final ApplicationEventPublisher applicationEventPublisher;

    public APBPaytmMerchantWalletPaymentService(ObjectMapper objectMapper, Gson gson, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, IUserPaymentsManager userPaymentsManager, PaymentCachingService paymentCachingService, ApplicationEventPublisher applicationEventPublisher) {
        super(paymentCachingService);
        this.objectMapper = objectMapper;
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.userPaymentsManager = userPaymentsManager;
        this.paymentCachingService = paymentCachingService;
        this.applicationEventPublisher = applicationEventPublisher;
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
                    loginId(walletLinkRequest.getEncSi()).wallet("PAYTM").authType("AUTH").build();
            HttpHeaders headers= generateHeaders(walletLinkRequest.getEncSi());
            HttpEntity<APBPaytmLinkRequest> requestEntity = new HttpEntity<APBPaytmLinkRequest>(linkRequest,headers);
            APBPaytmResponse linkResponse =restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_SEND_OTP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            APBPaytmResponse response = linkResponse;
            if (response.isResult()) {
                sessionDTO.put(ABP_PAYTM_OTP_TOKEN, response.getData().getOtpToken());
                log.info("otp send successfully {} ", response.getData().getOtpToken());

            } else {
//TODO:  Need to take errorCodes list from APB and put all in  ErrorCode  enum to provide ErrorCodes to FE
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

    private HttpHeaders generateHeaders(String loginId){
        HttpHeaders headers = new HttpHeaders();
        headers.add("authorization", "Basic cGF5bWVudDpwYXlAcWNrc2x2cg==");
        headers.add("channel-id", "WEB_MOBILE_UNAUTH");
        headers.add("iv-user", loginId);
        headers.add("content-type", "application/json");
        headers.add("accept", "application/json");
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
                    loginId(loginId).channel("ANDROID").wallet("PAYTM").authType("AUTH").otp(request.getOtp()).otpToken(otpToken).build();
            HttpHeaders headers = generateHeaders(sessionDTO.get(WALLET_USER_ID));
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
                    authType("AUTH").
                    channel("WEB").
                    userInfo(APBPaytmUserInfo.builder().circleId("-1").build()).
                    topUpInfo(APBTopUpInfo.builder().wallet("PAYTM").paymentMode("WALLET").topUpAmount(transaction.getAmount()).currency("INR").walletLoginId(loginId).
                            data(APBPaytmRequestData.builder().returnUrl(callBackUrl+SessionContextHolder.getId()).build()).build()).build();
            HttpHeaders headers = generateHeaders(loginId);
            HttpEntity<APBPaytmTopUpRequest> requestEntity = new HttpEntity<APBPaytmTopUpRequest>(topUpRequest,headers);
            APBPaytmResponse topUpResponse = restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_TOP_UP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            return topUpResponse;
        }
        catch (HttpStatusCodeException hex) {
            AnalyticService.update("topUpFailed", hex.getRawStatusCode());
            log.error("topUpFailed", hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            APBPaytmResponse topUpResponse = gson.fromJson(errorResponse, APBPaytmResponse.class);
            return topUpResponse;

        } catch (Exception e) {
            log.error("topUpFailed", e.getMessage(), e);
            return APBPaytmResponse.builder().result(false).data(APBPaytmResponseData.builder().html(null).build()).build();
        }

    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        return null;
    }

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        ErrorCode errorCode = null;
        ChargingResponse chargingResponse = null;
        HttpStatus httpStatus = HttpStatus.OK;
        String redirectUrl = null;
        Transaction transaction= TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String sid = SessionContextHolder.getId();
        ChargingResponse.ChargingResponseBuilder chargingResponseBuilder = ChargingResponse.builder();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<ChargingResponse>builder();
        try {
            final double amountToCharge=transaction.getAmount();
            String loginId=sessionDTO.get(WALLET_USER_ID);
            APBPaytmWalletPaymentRequest walletPaymentRequest=APBPaytmWalletPaymentRequest.builder()
                    .orderId(transaction.getIdStr())
                    .channel("WEB")
                    .userInfo(APBPaytmUserInfo.builder().circleId("").loginId(loginId).build())
                    .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(redirectUrl).encodedParams(false).build())
                    .paymentInfo(APBPaytmPaymentInfo.builder().lob("WYNK").paymentAmount(amountToCharge).paymentMode("WALLET").wallet("PAYTM").currency("INR").walletLoginId(loginId).build()).build();

            HttpHeaders headers = generateHeaders(loginId);
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



    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        ErrorCode errorCode = null;
        ChargingResponse chargingResponse = null;
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
            final double balance = sessionDTO.get(ABP_PAYTM_BALANCE_AMOUNT);
            final double amountToCharge = transaction.getAmount();
            Map map = new HashMap();
            map.put("walletLoginId", loginId);
            map.put("wallet", "PAYTM");
            map.put("encryptedToken", encryptedToken);
            String requestBalance = objectMapper.writeValueAsString(map);
            HttpHeaders headers = generateHeaders(loginId);
            HttpEntity<String> requestEntityForBalance = new HttpEntity<String>(requestBalance, headers);
            APBPaytmResponse balanceResponse = restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_GET_BALANCE, HttpMethod.POST, requestEntityForBalance, APBPaytmResponse.class).getBody();

            if (balanceResponse.isResult() && balanceResponse.getData().getBalance() < amountToCharge) {

                APBPaytmResponse topUpResponse = this.addMoney();
                if (topUpResponse.isResult() && topUpResponse.getData().getHtml() != null) {
  //                  chargingResponseBuilder.deficit(true).info(EncryptionUtils.encrypt(topUpResponse.getData().getHtml(), paymentEncryptionKey));
                    chargingResponseBuilder.deficit(true).info(topUpResponse.getData().getHtml());
                    log.info("topUp Response {}", topUpResponse);
                } else {
                    errorCode = ErrorCode.getErrorCodesFromExternalCode("");
                }
            } else {

                APBPaytmWalletPaymentRequest walletPaymentRequest = APBPaytmWalletPaymentRequest.builder()
                        .orderId(transaction.getIdStr())
                        .channel("WEB")
                        .userInfo(APBPaytmUserInfo.builder().circleId("").loginId(loginId).build())
                        .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(redirectUrl).encodedParams(false).build())
                        .paymentInfo(APBPaytmPaymentInfo.builder().lob("WYNK").paymentAmount(transaction.getAmount()).paymentMode("WALLET").wallet("PAYTM").currency("INR").walletLoginId(loginId).build()).build();


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
            log.error("APB_PAYTM_OTP_SEND_FAILURE", hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error("APB_PAYTM_OTP_SEND_FAILURE", e.getMessage());
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
    public BaseResponse<?> refund(AbstractPaymentRefundRequest request) {
        return null;
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {

    }

    @Override
    public WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> getUserPreferredPayments(Key key, int planId) {
        return null;
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