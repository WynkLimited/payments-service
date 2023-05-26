package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.WynkResponseUtils;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.apb.paytm.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.apb.paytm.APBPaytmResponse;
import in.wynk.payment.dto.response.apb.paytm.APBPaytmResponseData;
import in.wynk.payment.dto.response.phonepe.auto.AutoDebitWalletCallbackResponse;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.DiscountUtils;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.exception.WynkErrorType.UT022;
import static in.wynk.payment.core.constant.PaymentConstants.MERCHANT_TOKEN;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.constant.WalletConstants.WALLET;
import static in.wynk.payment.dto.apb.paytm.APBPaytmConstants.*;

@Slf4j
@Service(BeanConstant.APB_PAYTM_MERCHANT_WALLET_SERVICE)
public class APBPaytmMerchantWalletPaymentService extends AbstractMerchantPaymentStatusService implements IWalletLinkService<WalletLinkResponse, WalletLinkRequest>, IWalletValidateLinkService<Void, WalletValidateLinkRequest>, IMerchantPaymentChargingService<AutoDebitWalletChargingResponse, DefaultChargingRequest<?>>, IMerchantPaymentCallbackService<AutoDebitWalletCallbackResponse, ApbPaytmCallbackRequestPayload>, IWalletTopUpService<WalletTopUpResponse, WalletTopUpRequest<?>>, IWalletBalanceService<UserWalletDetails, WalletBalanceRequest>, IUserPreferredPaymentService<UserWalletDetails, PreferredPaymentDetailsRequest<?>> {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final IUserPaymentsManager userPaymentsManager;
    private final ApplicationEventPublisher eventPublisher;
    private final IErrorCodesCacheService errorCodesCacheServiceImpl;

    @Value("${payment.success.page}")
    private String successPage;
    @Value("${payment.failure.page}")
    private String failurePage;
    @Value("${payment.merchant.apbPaytm.api.base.url}")
    private String apbPaytmBaseUrl;
    @Value("${payment.encKey}")
    private String paymentEncryptionKey;

    public APBPaytmMerchantWalletPaymentService(ObjectMapper objectMapper, @Qualifier(BeanConstant.APB_PAYTM_PAYMENT_CLIENT_S2S_TEMPLATE) RestTemplate restTemplate, IUserPaymentsManager userPaymentsManager, ApplicationEventPublisher eventPublisher, PaymentCachingService paymentCachingService, IErrorCodesCacheService errorCodesCacheServiceImpl) {
        super(paymentCachingService, errorCodesCacheServiceImpl);
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
        this.userPaymentsManager = userPaymentsManager;
        this.errorCodesCacheServiceImpl = errorCodesCacheServiceImpl;
    }

    @Override
    public WynkResponseEntity<WalletLinkResponse> link(WalletLinkRequest walletLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<WalletLinkResponse> builder = WynkResponseEntity.builder();
        final WalletLinkResponse.WalletLinkResponseBuilder responseBuilder = WalletLinkResponse.builder().walletUserId(walletLinkRequest.getWalletUserId());
        try {
            APBPaytmLinkRequest linkRequest = APBPaytmLinkRequest.builder().walletLoginId(walletLinkRequest.getWalletUserId()).wallet(WALLET_PAYTM).build();
            HttpHeaders headers = generateHeaders(walletLinkRequest.getClient());
            HttpEntity<APBPaytmLinkRequest> requestEntity = new HttpEntity<>(linkRequest, headers);
            APBPaytmResponse response = restTemplate.exchange(apbPaytmBaseUrl + ABP_PAYTM_SEND_OTP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            if (response.isResult()) {
                responseBuilder.otpToken(response.getData().getOtpToken());
                log.info("otp send successfully {} ", response.getData().getOtpToken());
            } else {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(response.getErrorCode());
            }
        } catch (HttpStatusCodeException hex) {
            log.error(APB_PAYTM_OTP_SEND_FAILURE, hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error(APB_PAYTM_OTP_SEND_FAILURE, e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return builder.status(httpStatus).data(responseBuilder.build()).build();
        }
    }

    private HttpHeaders generateHeaders(String client) {
        final String merchantToken = PropertyResolverUtils.resolve(client, BeanConstant.APB_PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_TOKEN);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, merchantToken);
        headers.add(CHANNEL_ID, ABP_PAYTM_CHANNEL_ID);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    @Override
    public WynkResponseEntity<Void> validate(WalletValidateLinkRequest request) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<Void> builder = WynkResponseEntity.builder();
        try {
            String loginId = request.getWalletUserId();
            String otpToken = request.getOtpToken();
            APBPaytmOtpValidateRequest apbPaytmOtpValidateRequest = APBPaytmOtpValidateRequest.builder().walletLoginId(loginId).channel(CHANNEL_WEB).wallet(WALLET_PAYTM).authType(AUTH_TYPE_UN_AUTH).otp(request.getOtp()).otpToken(otpToken).build();
            HttpHeaders headers = generateHeaders(request.getClient());
            HttpEntity<APBPaytmOtpValidateRequest> requestEntity = new HttpEntity<>(apbPaytmOtpValidateRequest, headers);
            APBPaytmResponse linkResponse = restTemplate.exchange(apbPaytmBaseUrl + ABP_PAYTM_VERIFY_OTP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
            if (linkResponse != null && linkResponse.isResult()) {
                userPaymentsManager.save(Wallet.builder()
                        .walletUserId(loginId)
                        .tokenValidity(System.currentTimeMillis() + 1000000)
                        .accessToken(linkResponse.getData().getEncryptedToken())
                        .id(getKey(request.getUid(), request.getDeviceId()))
                        .build());
            } else {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(linkResponse.getErrorCode());
            }
        } catch (HttpStatusCodeException hex) {
            log.error(APB_PAYTM_OTP_VALIDATE_FAILURE, hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
        } catch (Exception e) {
            log.error(APB_PAYTM_OTP_VALIDATE_FAILURE, e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return builder.status(httpStatus).build();
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
        return SavedDetailsKey.builder().uid(uid).deviceId(deviceId).paymentGroup(WALLET).paymentCode("APB_PAYTM_WALLET").build();
    }

    @Override
    public WynkResponseEntity<UserWalletDetails> balance(WalletBalanceRequest request) {
        final double finalAmount = DiscountUtils.compute(null, PlanDetails.builder().planId(request.getPlanId()).build());
        return balance(request.getClient(), finalAmount, getWallet(getKey(request.getUid(), request.getDeviceId())));
    }

    private WynkResponseEntity<UserWalletDetails> balance(String client, double finalAmount, Wallet wallet) {
        final WynkResponseEntity.WynkResponseEntityBuilder<UserWalletDetails> builder = WynkResponseEntity.builder();
        if (Objects.nonNull(wallet)) {
            final APBPaytmResponse balanceResponse = this.getBalance(client, wallet);
            if (balanceResponse.isResult()) {
                if (balanceResponse.getData().getBalance() < finalAmount) {
                    double deficitBalance = finalAmount - balanceResponse.getData().getBalance();
                    builder.data(UserWalletDetails.builder()
                            .linked(true)
                            .active(true)
                            .balance(balanceResponse.getData().getBalance())
                            .linkedMobileNo(wallet.getWalletUserId())
                            .deficitBalance(deficitBalance)
                            .build());
                } else {
                    builder.data(UserWalletDetails.builder()
                            .linked(true)
                            .active(true)
                            .balance(balanceResponse.getData().getBalance())
                            .linkedMobileNo(wallet.getWalletUserId())
                            .deficitBalance(0)
                            .build());
                }
            } else {
                builder.error(TechnicalErrorDetails.builder().code(UT022.getErrorCode()).description(UT022.getErrorMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
            }
        } else {
            builder.error(TechnicalErrorDetails.builder().code(UT022.getErrorCode()).description(UT022.getErrorMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
        }
        return builder.build();
    }


    @Override
    public WynkResponseEntity<WalletTopUpResponse> topUp(WalletTopUpRequest<?> request) {
        final Transaction transaction = TransactionContext.get();
        final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        final WynkResponseEntity.WynkResponseEntityBuilder<WalletTopUpResponse> builder = WynkResponseEntity.builder();
        final APBPaytmResponse topUpResponse = this.addMoney(((IChargingDetails) request.getPurchaseDetails()).getCallbackDetails().getCallbackUrl(), transaction.getAmount(), getWallet(getKey(IdentityUtils.getUidFromUserName(purchaseDetails.getUserDetails().getMsisdn(), purchaseDetails.getAppDetails().getService()), purchaseDetails.getAppDetails().getDeviceId())));
        if (topUpResponse.isResult() && topUpResponse.getData().getHtml() != null) {
            try {
                builder.data(WalletTopUpResponse.builder().info(EncryptionUtils.encrypt(topUpResponse.getData().getHtml(), paymentEncryptionKey)).build());
            } catch (Exception e) {
                ErrorCode errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
                builder.error(TechnicalErrorDetails.builder().code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build());
            }
            log.info("topUp Response {}", topUpResponse);
        } else {
            ErrorCode errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            builder.error(TechnicalErrorDetails.builder().code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build());
        }
        return builder.build();
    }

    private APBPaytmResponse addMoney(String callbackUrl, double amount, Wallet wallet) {
        try {
            final Transaction transaction = TransactionContext.get();
            final APBPaytmTopUpRequest topUpRequest = APBPaytmTopUpRequest.builder().orderId(transaction.getIdStr())
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
                            .data(APBPaytmRequestData.builder().returnUrl(UriComponentsBuilder.fromHttpUrl(callbackUrl).queryParam(TRANSACTION_ID, transaction.getIdStr()).build().toUriString()).build())
                            .build())
                    .build();
            HttpHeaders headers = generateHeaders(transaction.getClientAlias());
            HttpEntity<APBPaytmTopUpRequest> requestEntity = new HttpEntity<>(topUpRequest, headers);
            APBPaytmResponse response = restTemplate.exchange(apbPaytmBaseUrl + ABP_PAYTM_TOP_UP, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
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
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.warn(APB_PAYTM_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at APBPaytm end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY304);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.warn(APB_PAYTM_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at APBPaytm end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY303);
        }
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().status(HttpStatus.OK).data(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    private APBPaytmResponse getTransactionStatus(Transaction txn) {
        try {
            HttpHeaders headers = generateHeaders(txn.getClientAlias());
            HttpEntity<?> requestEntity = new HttpEntity<>(headers);
            TransactionStatusAPBPaytmResponse statusResponse = restTemplate.exchange(apbPaytmBaseUrl + ABP_PAYTM_TRANSACTION_STATUS + txn.getIdStr(), HttpMethod.GET, requestEntity, TransactionStatusAPBPaytmResponse.class).getBody();
            return APBPaytmResponse.builder().result(statusResponse.isResult()).errorCode(statusResponse.getErrorCode()).errorMessage(statusResponse.getErrorMessage()).data(statusResponse.getData()[0]).build();
        } catch (HttpStatusCodeException e) {
            log.error(APB_PAYTM_CHARGING_STATUS_VERIFICATION, e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, "Error from APBPayTm " + e.getStatusCode().toString());
        } catch (Exception e) {
            log.error(APB_PAYTM_CHARGING_STATUS_VERIFICATION, e.getMessage());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, e.getMessage());
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
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(response.getErrorCode()).description(response.getErrorMessage()).build());
        }
        transaction.setStatus(finalTransactionStatus.name());
    }

    @Override
    public WynkResponseEntity<AutoDebitWalletCallbackResponse> handleCallback(ApbPaytmCallbackRequestPayload callbackRequest) {
        ErrorCode errorCode = null;
        String redirectUrl = null;
        final Transaction transaction = TransactionContext.get();
        final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        WynkResponseEntity.WynkResponseEntityBuilder<AutoDebitWalletCallbackResponse> builder = WynkResponseEntity.builder();
        try {
            Wallet wallet = getWallet(getKey(transaction.getUid(), purchaseDetails.getAppDetails().getDeviceId()));
            final double amountToCharge = transaction.getAmount();
            APBPaytmResponse balanceResponse = this.getBalance(transaction.getClientAlias(), wallet);
            if (balanceResponse.isResult() && balanceResponse.getData().getBalance() >= amountToCharge) {
                AnalyticService.update(ABP_ADD_MONEY_SUCCESS, true);
                APBPaytmWalletPaymentRequest walletPaymentRequest = APBPaytmWalletPaymentRequest.builder()
                        .orderId(transaction.getIdStr())
                        .channel(CHANNEL_WEB)
                        .userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).serviceInstance(wallet.getWalletUserId()).build())
                        .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(((IChargingDetails) purchaseDetails).getPageUrlDetails().getSuccessPageUrl()).channel(AUTH_TYPE_WEB_UNAUTH).build())
                        .paymentInfo(APBPaytmPaymentInfo.builder().lob(WYNK).paymentAmount(amountToCharge).paymentMode(WALLET).wallet(WALLET_PAYTM).currency(CURRENCY_INR).walletLoginId(wallet.getWalletUserId()).encryptedToken(wallet.getAccessToken()).build()).build();
                HttpHeaders headers = generateHeaders(transaction.getClientAlias());
                HttpEntity<APBPaytmWalletPaymentRequest> requestEntity = new HttpEntity<APBPaytmWalletPaymentRequest>(walletPaymentRequest, headers);
                APBPaytmResponse paymentResponse = restTemplate.exchange(
                        apbPaytmBaseUrl + ABP_PAYTM_WALLET_PAYMENT, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();

                if (paymentResponse != null && paymentResponse.isResult() && paymentResponse.getData().getPaymentStatus().equalsIgnoreCase(ABP_PAYTM_PAYMENT_SUCCESS)) {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    redirectUrl = ((IChargingDetails) purchaseDetails).getPageUrlDetails().getSuccessPageUrl();
                    log.info("redirectUrl {}", redirectUrl);
                } else {
                    errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
                }
                MerchantTransactionEvent merchantTransactionEvent = MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(paymentResponse.getData().getPgId()).request(requestEntity).response(paymentResponse).build();
                eventPublisher.publishEvent(merchantTransactionEvent);
            } else {
                AnalyticService.update(ABP_ADD_MONEY_SUCCESS, false);
            }
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(ABP_ADD_MONEY_SUCCESS, false);
            log.error(APB_PAYTM_CHARGE_FAILURE, hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build());
        } catch (Exception e) {
            AnalyticService.update(ABP_ADD_MONEY_SUCCESS, false);
            log.error(APB_PAYTM_CHARGE_FAILURE, e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }

            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = ((IChargingDetails) purchaseDetails).getPageUrlDetails().getFailurePageUrl();
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }

            return WynkResponseUtils.redirectResponse(redirectUrl);
        }
    }

    /**
     * TODO:: Since apb paytm is not supplying wynk transaction id in the callback payload, we would be able to parse it, as a work around we are getting transaction id from session, check with apb paytm to include wynk txn id
     */
    @Override
    public ApbPaytmCallbackRequestPayload parseCallback(Map<String, Object> payload) {
        try {
            return ApbPaytmCallbackRequestPayload.builder().transactionId((String) payload.get(TRANSACTION_ID)).build();
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY006, e);
        }
    }

    private APBPaytmResponse getBalance(String client, Wallet wallet) {
        try {
            APBPaytmBalanceRequest apbPaytmBalanceRequest = APBPaytmBalanceRequest.builder().walletLoginId(wallet.getWalletUserId()).wallet(WALLET_PAYTM).encryptedToken(wallet.getAccessToken()).build();
            HttpHeaders headers = generateHeaders(client);
            HttpEntity<APBPaytmBalanceRequest> requestEntityForBalance = new HttpEntity<>(apbPaytmBalanceRequest, headers);
            APBPaytmResponse balanceResponse = restTemplate.exchange(apbPaytmBaseUrl + ABP_PAYTM_GET_BALANCE, HttpMethod.POST, requestEntityForBalance, APBPaytmResponse.class).getBody();
            return balanceResponse;
        } catch (HttpStatusCodeException e) {
            log.error(APB_PAYTM_GET_BALANCE_FAILURE, e.getResponseBodyAsString());
            return APBPaytmResponse.builder().result(false).build();
        } catch (Exception e) {
            log.error(APB_PAYTM_GET_BALANCE_FAILURE, e.getMessage());
            return APBPaytmResponse.builder().result(false).build();
        }
    }

    @Override
    public WynkResponseEntity<AutoDebitWalletChargingResponse> charge(DefaultChargingRequest<?> request) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        String redirectUrl = null;
        Transaction transaction = TransactionContext.get();
        AutoDebitWalletChargingResponse.AutoDebitWalletChargingResponseBuilder<?, ?> walletResponse = AutoDebitWalletChargingResponse.builder();
        WynkResponseEntity.WynkResponseEntityBuilder<AutoDebitWalletChargingResponse> builder = WynkResponseEntity.builder();
        try {
            Wallet wallet = getWallet(getKey(IdentityUtils.getUidFromUserName(request.getPurchaseDetails().getUserDetails().getMsisdn(), request.getService()), request.getPurchaseDetails().getAppDetails().getDeviceId()));
            HttpHeaders headers = generateHeaders(transaction.getClientAlias());
            final double amountToCharge = transaction.getAmount();
            APBPaytmResponse balanceResponse = this.getBalance(transaction.getClientAlias(), wallet);
            if (balanceResponse.isResult() && balanceResponse.getData().getBalance() < amountToCharge) {
                final double amountToAdd = amountToCharge - balanceResponse.getData().getBalance();
                APBPaytmResponse topUpResponse = this.addMoney(((IChargingDetails) request.getPurchaseDetails()).getCallbackDetails().getCallbackUrl(), amountToAdd, wallet);
                if (topUpResponse.isResult() && topUpResponse.getData().getHtml() != null) {
                    walletResponse.deficit(true).info(EncryptionUtils.encrypt(topUpResponse.getData().getHtml(), paymentEncryptionKey));
                    log.info("topUp Response {}", topUpResponse);
                } else {
                    errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
                }
            } else if (balanceResponse.isResult() && balanceResponse.getData().getBalance() >= amountToCharge) {
                APBPaytmWalletPaymentRequest walletPaymentRequest = APBPaytmWalletPaymentRequest.builder()
                        .orderId(transaction.getIdStr())
                        .channel(CHANNEL_WEB)
                        .userInfo(APBPaytmUserInfo.builder().circleId(CIRCLE_ID).serviceInstance(wallet.getWalletUserId()).build())
                        .channelInfo(APBPaytmChannelInfo.builder().redirectionUrl(((IChargingDetails) request.getPurchaseDetails()).getPageUrlDetails().getSuccessPageUrl()).channel(AUTH_TYPE_WEB_UNAUTH).build())
                        .paymentInfo(APBPaytmPaymentInfo.builder().lob(WYNK).paymentAmount(amountToCharge).paymentMode(WALLET).wallet(WALLET_PAYTM).currency(CURRENCY_INR).walletLoginId(wallet.getWalletUserId()).encryptedToken(wallet.getAccessToken()).build()).build();

                HttpEntity<APBPaytmWalletPaymentRequest> requestEntity = new HttpEntity<APBPaytmWalletPaymentRequest>(walletPaymentRequest, headers);
                APBPaytmResponse paymentResponse = restTemplate.exchange(
                        apbPaytmBaseUrl + ABP_PAYTM_WALLET_PAYMENT, HttpMethod.POST, requestEntity, APBPaytmResponse.class).getBody();
                if (paymentResponse.isResult() && paymentResponse.getData().getPaymentStatus() != null && paymentResponse.getData().getPaymentStatus().equalsIgnoreCase(ABP_PAYTM_PAYMENT_SUCCESS)) {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    redirectUrl = ((IChargingDetails) request.getPurchaseDetails()).getPageUrlDetails().getSuccessPageUrl();
                }
                MerchantTransactionEvent merchantTransactionEvent = MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(paymentResponse.getData().getPgId()).request(requestEntity).response(paymentResponse).build();
                eventPublisher.publishEvent(merchantTransactionEvent);
            }

        } catch (HttpStatusCodeException hex) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            log.error(APB_PAYTM_CHARGE_FAILURE, hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmResponse.class).getErrorCode());
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build());
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            log.error(APB_PAYTM_CHARGE_FAILURE, e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = ((IChargingDetails) request.getPurchaseDetails()).getPageUrlDetails().getFailurePageUrl();
            }
            handleError(errorCode, builder);
            return builder.status(httpStatus).data(walletResponse.redirectUrl(redirectUrl).build()).build();
        }
    }


    private void handleError(ErrorCode errorCode, WynkResponseEntity.WynkResponseEntityBuilder<?> builder) {
        if (Objects.nonNull(errorCode)) {
            if (errorCode == errorCodesCacheServiceImpl.getDefaultUnknownErrorCode()) {
                builder.error(TechnicalErrorDetails.builder().code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build()).success(false);
            } else {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
        }
    }

    @Override
    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<UserWalletDetails> getUserPreferredPayments(PreferredPaymentDetailsRequest<?> request) {
        WynkResponseEntity.WynkResponseEntityBuilder<UserWalletDetails> builder = WynkResponseEntity.builder();
        Wallet wallet = getWallet(request.getPreferredPayment());
        if (Objects.nonNull(wallet)) {
            final double finalPrice = DiscountUtils.compute(request.getCouponId(), request.getProductDetails());
            final APBPaytmResponse balanceResponse = this.getBalance(request.getClientAlias(), wallet);
            if (balanceResponse.isResult()) {
                if (balanceResponse.getData().getBalance() < finalPrice) {
                    double deficitBalance = finalPrice - balanceResponse.getData().getBalance();
                    builder.data(UserWalletDetails.builder()
                            .linked(true)
                            .active(true)
                            .balance(balanceResponse.getData().getBalance())
                            .linkedMobileNo(wallet.getWalletUserId())
                            .deficitBalance(deficitBalance)
                            .build());
                } else {
                    builder.data(UserWalletDetails.builder()
                            .linked(true)
                            .active(true)
                            .balance(balanceResponse.getData().getBalance())
                            .linkedMobileNo(wallet.getWalletUserId())
                            .deficitBalance(0)
                            .build());
                }
            } else {
                builder.error(TechnicalErrorDetails.builder().code(UT022.getErrorCode()).description(UT022.getErrorMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
            }

        } else {
            builder.error(TechnicalErrorDetails.builder().code(UT022.getErrorCode()).description(UT022.getErrorMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
        }
        return builder.build();
    }

}