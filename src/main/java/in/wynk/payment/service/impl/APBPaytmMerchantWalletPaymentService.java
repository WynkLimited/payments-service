package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.ErrorCode;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.apb.paytm.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.Apb.paytm.APBPaytmResponse;
import in.wynk.payment.dto.response.Apb.paytm.APBPaytmResponseData;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.exception.WynkErrorType.UT022;
import static in.wynk.payment.core.constant.PaymentCode.APB_PAYTM_WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET_USER_ID;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.apb.paytm.APBPaytmConstants.*;

@Slf4j
@Service(BeanConstant.APB_PAYTM_MERCHANT_WALLET_SERVICE)
public class APBPaytmMerchantWalletPaymentService extends AbstractMerchantPaymentStatusService implements IRenewalMerchantWalletService, IUserPreferredPaymentService {

    @Value("${payment.success.page}")
    private String successPage;
    @Value("${payment.failure.page}")
    private String failurePage;
    @Value("${payment.merchant.apbPaytm.auth.token}")
    private String ABP_PAYTM_AUTHORIZATION;
    @Value("${payment.merchant.apbPaytm.callback.url}")
    private String callBackUrl;
    @Value("${payment.merchant.apbPaytm.api.base.url}")
    private String apbPaytmBaseUrl;
    @Value("${payment.encKey}")
    private String paymentEncryptionKey;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final IUserPaymentsManager userPaymentsManager;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService paymentCachingService;
    private final MerchantTransactionImpl merchantTransactionService;

    public APBPaytmMerchantWalletPaymentService(ObjectMapper objectMapper, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, IUserPaymentsManager userPaymentsManager, ApplicationEventPublisher eventPublisher, PaymentCachingService paymentCachingService, MerchantTransactionImpl merchantTransactionService, MerchantTransactionImpl merchantTransactionService1) {
        super(paymentCachingService);
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.userPaymentsManager = userPaymentsManager;
        this.eventPublisher = eventPublisher;
        this.paymentCachingService = paymentCachingService;
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public BaseResponse<?> linkRequest(WalletLinkRequest walletLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        sessionDTO.put(WALLET_USER_ID, walletLinkRequest.getEncSi());

        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            AnalyticService.update(UID,sessionDTO.<String>get(UID));
            APBPaytmLinkRequest linkRequest = APBPaytmLinkRequest.builder().walletLoginId(walletLinkRequest.getEncSi()).wallet(WALLET_PAYTM).build();
            HttpHeaders headers= generateHeaders();
            HttpEntity<APBPaytmLinkRequest> requestEntity = new HttpEntity<APBPaytmLinkRequest>(linkRequest,headers);
            APBPaytmResponse response = restTemplate.exchange(apbPaytmBaseUrl+ABP_PAYTM_SEND_OTP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            if (response.isResult()) {
                sessionDTO.put(ABP_PAYTM_OTP_TOKEN, response.getData().getOtpToken());
                log.info("otp send successfully {} ", response.getData().getOtpToken());
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(response.getErrorCode());
            }
        }
        catch (HttpStatusCodeException hex) {
            log.error(APB_PAYTM_OTP_SEND_FAILURE, hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error(APB_PAYTM_OTP_SEND_FAILURE, e.getMessage());
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
            AnalyticService.update(UID,sessionDTO.<String>get(UID));
            String loginId = sessionDTO.get(WALLET_USER_ID);
            String otpToken = sessionDTO.get(ABP_PAYTM_OTP_TOKEN);
            APBPaytmOtpValidateRequest apbPaytmOtpValidateRequest = APBPaytmOtpValidateRequest.builder().walletLoginId(loginId).channel(CHANNEL_WEB).wallet(WALLET_PAYTM).authType(AUTH_TYPE_UN_AUTH).otp(request.getOtp()).otpToken(otpToken).build();
            HttpHeaders headers = generateHeaders();
            HttpEntity<APBPaytmOtpValidateRequest> requestEntity = new HttpEntity<>(apbPaytmOtpValidateRequest, headers);
            APBPaytmResponse linkResponse =restTemplate.exchange(apbPaytmBaseUrl+ABP_PAYTM_VERIFY_OTP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            if (linkResponse!=null && linkResponse.isResult()) {
                userPaymentsManager.save(Wallet.builder()
                        .walletUserId(loginId)
                        .tokenValidity(System.currentTimeMillis()+1000000)
                        .accessToken(linkResponse.getData().getEncryptedToken())
                        .id(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)))
                        .build());
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(ErrorCode.UNKNOWN.name());
            }
        } catch (HttpStatusCodeException hex) {
            log.error(APB_PAYTM_OTP_VALIDATE_FAILURE, hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error(APB_PAYTM_OTP_VALIDATE_FAILURE,e.getMessage());
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.build()).build();
        }
    }

    private Wallet getWallet(UserPreferredPayment userPreferredPayment) {
        try {
            Wallet wallet = (Wallet) userPreferredPayment;
            if (wallet.getTokenValidity() > System.currentTimeMillis() || StringUtils.isNotBlank(wallet.getAccessToken())) {
                return wallet;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Wallet getWallet(SavedDetailsKey key) {
        Map<SavedDetailsKey, UserPreferredPayment> userPreferredPaymentMap = userPaymentsManager.get(key.getUid()).stream().collect(Collectors.toMap(UserPreferredPayment::getId, Function.identity()));
        return getWallet(userPreferredPaymentMap.getOrDefault(key, null));
    }

    private SavedDetailsKey getKey(String uid, String deviceId) {
        return SavedDetailsKey.builder().uid(uid).deviceId(deviceId).paymentGroup(WALLET).paymentCode(APB_PAYTM_WALLET.name()).build();
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
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        return new BaseResponse<>(addMoney(request.getAmountToCredit(), getWallet(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)))), HttpStatus.OK, null);

    }

    private APBPaytmResponse addMoney(double amount, Wallet wallet){
        try {

            Transaction transaction = TransactionContext.get();
            APBPaytmTopUpRequest topUpRequest = APBPaytmTopUpRequest.builder().orderId(transaction.getIdStr())
                    .channel(CHANNEL_WEB)
                    .authType(AUTH_TYPE_UN_AUTH)
                    .encryptedToken(wallet.getAccessToken())
                    .userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).build())
                    .topUpInfo(APBTopUpInfo.builder()
                            .paymentMode(WALLET)
                            .wallet(WALLET_PAYTM)
                            .currency(CURRENCY_INR)
                            .topUpAmount(amount)
                            .walletLoginId(wallet.getWalletUserId())
                            .data(APBPaytmRequestData.builder().returnUrl(callBackUrl+SessionContextHolder.getId()).build())
                            .build())
                    .build();
            HttpHeaders headers = generateHeaders();
            HttpEntity<APBPaytmTopUpRequest> requestEntity = new HttpEntity<>(topUpRequest, headers);
            APBPaytmResponse response= restTemplate.exchange(apbPaytmBaseUrl+ABP_PAYTM_TOP_UP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            return response;
        } catch (HttpStatusCodeException hex) {
            log.error(APB_PAYTM_ADD_MONEY_FAILURE, hex.getResponseBodyAsString());
            try {
                return objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class);
            } catch (JsonProcessingException e) {
                log.error(APB_PAYTM_ADD_MONEY_FAILURE, e.getMessage());
                return APBPaytmResponse.builder().result(false).data(APBPaytmResponseData.builder().html(null).build()).build();
            }
        } catch (Exception e) {
            log.error(APB_PAYTM_ADD_MONEY_FAILURE, e.getMessage(), e);
            return APBPaytmResponse.builder().result(false).data(APBPaytmResponseData.builder().html(null).build()).build();
        }
    }

    @Override
    public BaseResponse<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(APB_PAYTM_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at APBPaytm end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "Transaction is still pending at APBPaytm");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(APB_PAYTM_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at APBPaytm end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "APB_PAYTM_CHARGING_STATUS_VERIFICATION");
        }
        return BaseResponse.<AbstractChargingStatusResponse>builder().status(HttpStatus.OK).body(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    private APBPaytmResponse getTransactionStatus(Transaction txn) {
        MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(txn.getIdStr());
        if(merchantTransaction==null){
            log.error(APB_PAYTM_CHARGING_STATUS_VERIFICATION_FAILURE, "Transaction not found in MerchantTransaction for the transactionId: {} and uid: {}", txn.getIdStr(), txn.getUid());
        }
        try {
            HttpHeaders headers= generateHeaders();
            HttpEntity<HttpHeaders> requestEntity = new HttpEntity<HttpHeaders>(headers);
            APBPaytmResponse statusResponse =restTemplate.exchange(
                    apbPaytmBaseUrl+ABP_PAYTM_TRANSACTION_STATUS+merchantTransaction.getExternalTransactionId(), HttpMethod.GET, requestEntity, APBPaytmResponse.class).getBody();
            return statusResponse;
        } catch (HttpStatusCodeException e) {
            log.error(APB_PAYTM_CHARGING_STATUS_VERIFICATION_FAILURE, e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, "Error from APBPayTm " + e.getStatusCode().toString());
        } catch (Exception e) {
            log.error(APB_PAYTM_CHARGING_STATUS_VERIFICATION_FAILURE, e.getMessage());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e,e.getMessage());
        }
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {

        TransactionStatus finalTransactionStatus;
        APBPaytmResponse response = getTransactionStatus(transaction);
        if (response.isResult()) {
            String statusCode = response.getData().getPaymentStatus();
            if (StringUtils.isNotBlank(statusCode) && statusCode.equalsIgnoreCase(ABP_PAYTM_PAYMENT_SUCCESS)) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    StringUtils.isNotBlank(statusCode) && statusCode.equalsIgnoreCase(ABP_PAYTM_PAYMENT_PENDING)) {
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
        String redirectUrl = null;
        Transaction transaction = TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String sid = SessionContextHolder.getId();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<ChargingResponse>builder();
        try {
            Wallet wallet = getWallet(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)));
            final double amountToCharge = transaction.getAmount();
            APBPaytmWalletPaymentRequest walletPaymentRequest = APBPaytmWalletPaymentRequest.builder()
                    .orderId(transaction.getIdStr())
                    .channel(CHANNEL_WEB)
                    .userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).serviceInstance(wallet.getWalletUserId()).build())
                    .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(successPage + sid).channel(AUTH_TYPE_WEB_UNAUTH).build())
                    .paymentInfo(APBPaytmPaymentInfo.builder().lob(WYNK).paymentAmount(amountToCharge).paymentMode(WALLET).wallet(WALLET_PAYTM).currency(CURRENCY_INR).walletLoginId(wallet.getWalletUserId()).encryptedToken(wallet.getAccessToken()).build()).build();
            HttpHeaders headers = generateHeaders();
            HttpEntity<APBPaytmWalletPaymentRequest> requestEntity = new HttpEntity<APBPaytmWalletPaymentRequest>(walletPaymentRequest, headers);
            APBPaytmResponse paymentResponse = restTemplate.exchange(
                    apbPaytmBaseUrl + ABP_PAYTM_WALLET_PAYMENT, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();

            if (paymentResponse != null && paymentResponse.isResult() && paymentResponse.getData().getPaymentStatus().equalsIgnoreCase(ABP_PAYTM_PAYMENT_SUCCESS)) {
                transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                redirectUrl = successPage + sid;

                log.info("redirectUrl {}", redirectUrl);
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode("");
            }
            MerchantTransactionEvent merchantTransactionEvent= MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(paymentResponse.getData().getPgId()).request(requestEntity).response(paymentResponse).build();
            eventPublisher.publishEvent(merchantTransactionEvent);
        } catch (HttpStatusCodeException hex) {
            log.error(APB_PAYTM_CHARGE_FAILURE, hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorCode.name()).description(errorCode.getInternalMessage()).build());
        } catch (Exception e) {
            log.error(APB_PAYTM_CHARGE_FAILURE, e.getMessage());
            errorCode = ErrorCode.UNKNOWN;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }

            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = failurePage + sid;
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }
            return BaseResponse.redirectResponse(redirectUrl +
                    SLASH +
                    sessionDTO.<String>get(OS) +
                    QUESTION_MARK +
                    SERVICE +
                    EQUAL +
                    sessionDTO.<String>get(SERVICE) +
                    AND +
                    BUILD_NO +
                    EQUAL +
                    sessionDTO.<Integer>get(BUILD_NO));
        }
    }

    private APBPaytmResponse getBalance(Wallet wallet) {
        try {
            APBPaytmBalanceRequest apbPaytmBalanceRequest = APBPaytmBalanceRequest.builder().walletLoginId(wallet.getWalletUserId()).wallet(WALLET_PAYTM).encryptedToken(wallet.getAccessToken()).build();
            HttpHeaders headers = generateHeaders();
            HttpEntity<APBPaytmBalanceRequest> requestEntityForBalance = new HttpEntity<>(apbPaytmBalanceRequest, headers);
            APBPaytmResponse balanceResponse = restTemplate.exchange(apbPaytmBaseUrl + ABP_PAYTM_GET_BALANCE, HttpMethod.POST, requestEntityForBalance, APBPaytmResponse.class).getBody();
            return balanceResponse;
        } catch (HttpStatusCodeException e) {
            log.error(APB_PAYTM_GET_BALANCE_FAILURE, e.getResponseBodyAsString());
            return APBPaytmResponse.builder().result(false).build();
        } catch (Exception e){
            log.error(APB_PAYTM_GET_BALANCE_FAILURE, e.getMessage());
            return APBPaytmResponse.builder().result(false).build();
        }
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        String redirectUrl = null;
        Transaction transaction = TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String sid = SessionContextHolder.getId();
        ChargingResponse.ChargingResponseBuilder chargingResponseBuilder = ChargingResponse.builder();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<ChargingResponse>builder();
        try {
            Wallet wallet = getWallet(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)));
            HttpHeaders headers = generateHeaders();
            final double amountToCharge = transaction.getAmount();
            APBPaytmResponse balanceResponse = this.getBalance(wallet);
            if (balanceResponse.isResult() && balanceResponse.getData().getBalance() < amountToCharge) {
                APBPaytmResponse topUpResponse = this.addMoney(amountToCharge, wallet);
                if (topUpResponse.isResult() && topUpResponse.getData().getHtml() != null) {
                    chargingResponseBuilder.deficit(true).info(EncryptionUtils.encrypt(topUpResponse.getData().getHtml(), paymentEncryptionKey));
                    log.info("topUp Response {}", topUpResponse);
                } else {
                    errorCode = ErrorCode.getErrorCodesFromExternalCode(ErrorCode.UNKNOWN.name());
                }
            } else if (balanceResponse.isResult() && balanceResponse.getData().getBalance() >= amountToCharge) {
                APBPaytmWalletPaymentRequest walletPaymentRequest = APBPaytmWalletPaymentRequest.builder()
                        .orderId(transaction.getIdStr())
                        .channel(CHANNEL_WEB)
                        .userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).serviceInstance(wallet.getWalletUserId()).build())
                        .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(successPage + sid).channel(AUTH_TYPE_WEB_UNAUTH).build())
                        .paymentInfo(APBPaytmPaymentInfo.builder().lob(WYNK).paymentAmount(amountToCharge).paymentMode(WALLET).wallet(WALLET_PAYTM).currency(CURRENCY_INR).walletLoginId(wallet.getWalletUserId()).encryptedToken(wallet.getAccessToken()).build()).build();

                HttpEntity<APBPaytmWalletPaymentRequest> requestEntity = new HttpEntity<APBPaytmWalletPaymentRequest>(walletPaymentRequest, headers);
                APBPaytmResponse paymentResponse = restTemplate.exchange(
                        apbPaytmBaseUrl + ABP_PAYTM_WALLET_PAYMENT, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
                if (paymentResponse.isResult() && paymentResponse.getData().getPaymentStatus() != null && paymentResponse.getData().getPaymentStatus().equalsIgnoreCase(ABP_PAYTM_PAYMENT_SUCCESS)) {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    redirectUrl = successPage + sid;
                }
                MerchantTransactionEvent merchantTransactionEvent= MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(paymentResponse.getData().getPgId()).request(requestEntity).response(paymentResponse).build();
                eventPublisher.publishEvent(merchantTransactionEvent);
            }

        } catch (HttpStatusCodeException hex) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            log.error(APB_PAYTM_CHARGE_FAILURE, hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorCode.name()).description(errorCode.getInternalMessage()).build());
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            log.error(APB_PAYTM_CHARGE_FAILURE, e.getMessage());
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = failurePage + sid;

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

    @Override
    public WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> getUserPreferredPayments(UserPreferredPayment userPreferredPayment, int planId) {
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<UserWalletDetails>builder();
        Wallet wallet = getWallet(userPreferredPayment);
        if (Objects.nonNull(wallet)) {
            APBPaytmResponse balanceResponse = this.getBalance(wallet);
            if(balanceResponse.isResult()){
                if (balanceResponse.getData().getBalance() < paymentCachingService.getPlan(planId).getFinalPrice()) {
                    double deficitBalance = paymentCachingService.getPlan(planId).getFinalPrice() - balanceResponse.getData().getBalance();
                    builder.data(UserWalletDetails.builder()
                            .linked(true)
                            .active(true)
                            .balance(balanceResponse.getData().getBalance())
                            .linkedMobileNo(wallet.getWalletUserId())
                            .deficitBalance(deficitBalance)
                            .build());
                }
                else{
                    builder.data(UserWalletDetails.builder()
                            .linked(true)
                            .active(true)
                            .balance(balanceResponse.getData().getBalance())
                            .linkedMobileNo(wallet.getWalletUserId())
                            .deficitBalance(0)
                            .build());
                }
            }
             else {
                builder.error(TechnicalErrorDetails.builder().code(UT022.getErrorCode()).description(UT022.getErrorMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
            }

        }
        else {
            builder.error(TechnicalErrorDetails.builder().code(UT022.getErrorCode()).description(UT022.getErrorMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
        }
        return builder.build();
    }

}