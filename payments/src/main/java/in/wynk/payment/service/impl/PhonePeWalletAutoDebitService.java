package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.Utils;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.phonepe.PhonePeResponse;
import in.wynk.payment.dto.phonepe.PhonePeResponseType;
import in.wynk.payment.dto.phonepe.PhonePeStatusEnum;
import in.wynk.payment.dto.phonepe.PhonePeTransactionResponseWrapper;
import in.wynk.payment.dto.phonepe.autodebit.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.phonepe.PhonePeResponseData;
import in.wynk.payment.dto.response.phonepe.PhonePeWalletResponse;
import in.wynk.payment.dto.response.phonepe.auto.AutoDebitWalletCallbackResponse;
import in.wynk.payment.dto.response.phonepe.auto.PhonePeChargingRequest;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.DiscountUtils;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.ONE_DAY_IN_MILLI;
import static in.wynk.common.constant.BaseConstants.SLASH;
import static in.wynk.error.codes.core.constant.ErrorCodeConstants.*;
import static in.wynk.exception.WynkErrorType.UT022;
import static in.wynk.payment.constant.WalletConstants.WALLET;
import static in.wynk.payment.core.constant.BeanConstant.PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;

@Slf4j
@Service(PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE)
public class PhonePeWalletAutoDebitService extends AbstractMerchantPaymentStatusService implements IMerchantPaymentCallbackService<AutoDebitWalletCallbackResponse, PhonePeAutoDebitCallbackRequest>, IMerchantPaymentChargingService<AutoDebitWalletChargingResponse, PhonePeChargingRequest<?>>, IWalletLinkService<WalletLinkResponse, WalletLinkRequest>, IWalletValidateLinkService<Void, WalletValidateLinkRequest>, IWalletDeLinkService<Void, WalletDeLinkRequest>, IWalletBalanceService<UserWalletDetails, WalletBalanceRequest>, IWalletTopUpService<WalletTopUpResponse, PhonePeAutoDebitTopUpRequest<?>>, IUserPreferredPaymentService<UserWalletDetails, PreferredPaymentDetailsRequest<?>> {

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IUserPaymentsManager userPaymentsManager;
    private final IErrorCodesCacheService errorCodesCacheServiceImpl;

    @Value("${payment.merchant.phonepe.api.base.url}")
    private String phonePeBaseUrl;
    @Value("${payment.encKey}")
    private String paymentEncryptionKey;

    public PhonePeWalletAutoDebitService(@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, Gson gson, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper, IUserPaymentsManager userPaymentsManager, IErrorCodesCacheService errorCodesCacheServiceImpl) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.userPaymentsManager = userPaymentsManager;
        this.errorCodesCacheServiceImpl = errorCodesCacheServiceImpl;
    }

    @Override
    public WynkResponseEntity<WalletLinkResponse> link(WalletLinkRequest request) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<WalletLinkResponse> builder = WynkResponseEntity.builder();
        final WalletLinkResponse.WalletLinkResponseBuilder responseBuilder = WalletLinkResponse.builder().walletUserId(request.getWalletUserId());
        try {
            String phone = request.getWalletUserId();
            log.info("Sending OTP to {} via PhonePe", phone);
            final String merchantId = PropertyResolverUtils.resolve(request.getClient(), PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);
            PhonePeAutoDebitLinkRequest phonePeAutoDebitLinkRequest = PhonePeAutoDebitLinkRequest.builder().merchantId(merchantId).mobileNumber(phone).build();
            String requestJson = objectMapper.writeValueAsString(phonePeAutoDebitLinkRequest);
            HttpEntity<Map<String, String>> requestEntity = generatePayload(request.getClient(), request.getDeviceId(), requestJson, TRIGGER_OTP_API);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + TRIGGER_OTP_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse linkRequestResponse = response.getBody();
            if (linkRequestResponse != null && linkRequestResponse.isSuccess() && linkRequestResponse.getCode().equalsIgnoreCase(SUCCESS) && linkRequestResponse.getData().getOtpToken() != null) {
                responseBuilder.otpToken(linkRequestResponse.getData().getOtpToken());
                log.info("Otp sent successfully. Status: {}", linkRequestResponse.getCode());
            } else {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(linkRequestResponse.getCode());
            }
        } catch (HttpStatusCodeException hex) {
            log.error(PHONEPE_OTP_SEND_FAILURE, hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), PhonePeWalletResponse.class).getCode());
        } catch (Exception e) {
            log.error(PHONEPE_OTP_SEND_FAILURE, e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return builder.status(httpStatus).data(responseBuilder.build()).build();
        }

    }

    @Override
    public WynkResponseEntity<Void> validate(WalletValidateLinkRequest request) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<Void> builder = WynkResponseEntity.builder();
        try {
            final String merchantId = PropertyResolverUtils.resolve(request.getClient(), PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);
            PhonePeAutoDebitValidateOtpRequest phonePeAutoDebitOtpValidateRequest = PhonePeAutoDebitValidateOtpRequest.builder().merchantId(merchantId).otp(request.getOtp()).otpToken(request.getOtpToken()).build();
            String requestJson = objectMapper.writeValueAsString(phonePeAutoDebitOtpValidateRequest);
            HttpEntity<Map<String, String>> requestEntity = generatePayload(request.getClient(), request.getDeviceId(), requestJson, VERIFY_OTP_API);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + VERIFY_OTP_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse verifyOtpResponse = response.getBody();
            if (verifyOtpResponse != null && verifyOtpResponse.isSuccess() && verifyOtpResponse.getCode().equalsIgnoreCase(SUCCESS) && verifyOtpResponse.getData().getUserAuthToken() != null) {
                saveToken(request.getUid(), request.getDeviceId(), request.getWalletUserId(), verifyOtpResponse.getData().getUserAuthToken());
                log.info("Otp validated successfully. Status: {}", verifyOtpResponse.getCode());
            } else {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(verifyOtpResponse.getCode());
            }
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update("otpValidated", false);
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, "Error in response while verifying otp: {}", hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), PhonePeWalletResponse.class).getCode());
        } catch (Exception e) {
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, "Error in response while verifying otp: {}", e.getMessage(), e);
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return builder.status(httpStatus).build();
        }
    }

    @Override
    public WynkResponseEntity<UserWalletDetails> balance(WalletBalanceRequest request) {
        final double finalAmount = DiscountUtils.compute(null, PlanDetails.builder().planId(request.getPlanId()).build());
        return balance(request.getClient(), finalAmount, getWallet(getKey(request.getUid(), request.getDeviceId())));
    }

    private WynkResponseEntity<UserWalletDetails> balance(String client, double finalAmount, Wallet wallet) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        UserWalletDetails.UserWalletDetailsBuilder userWalletDetailsBuilder = UserWalletDetails.builder();
        WynkResponseEntity.WynkResponseEntityBuilder<UserWalletDetails> builder = WynkResponseEntity.builder();
        try {
            userWalletDetailsBuilder.linked(true).linkedMobileNo(wallet.getWalletUserId());
            final String merchantId = PropertyResolverUtils.resolve(client, PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);

            PhoneAutoDebitBalanceRequest phoneAutoDebitBalanceRequest = PhoneAutoDebitBalanceRequest.builder().merchantId(merchantId).userAuthToken(wallet.getAccessToken()).txnAmount(Double.valueOf(finalAmount).longValue()).build();
            String requestJson = objectMapper.writeValueAsString(phoneAutoDebitBalanceRequest);
            HttpEntity<Map<String, String>> requestEntity = generatePayload(client, wallet.getId().getDeviceId(), requestJson, BALANCE_API);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + BALANCE_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse walletResponse = response.getBody();
            if (walletResponse != null && walletResponse.isSuccess() && walletResponse.getCode().equalsIgnoreCase(SUCCESS) && walletResponse.getData().getWallet().getWalletActive()) {
                double deficitBalance = 0;
                double usableBalance = walletResponse.getData().getWallet().getUsableBalance() / 100d;
                if (usableBalance < finalAmount) {
                    deficitBalance = finalAmount - usableBalance;
                }
                AnalyticService.update("PHONEPE_CODE", walletResponse.getCode());
                userWalletDetailsBuilder
                        .active(true)
                        .balance(usableBalance)
                        .deficitBalance(deficitBalance)
                        .addMoneyAllowed(walletResponse.getData().getWallet().getWalletTopupSuggested());
            } else {

                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(walletResponse.getCode());
                if (!walletResponse.getData().getWallet().getWalletActive()) {
                    errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(PHONEPE023);
                }
            }
        } catch (HttpStatusCodeException hex) {
            log.error(PHONEPE_GET_BALANCE_FAILURE, "Error in response: {}", hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), PhonePeWalletResponse.class).getCode());
            if (errorCode.getInternalCode().equalsIgnoreCase(PHONEPE027)) {
                userWalletDetailsBuilder.linked(false);
            }
        } catch (WynkRuntimeException e) {
            log.error(PHONEPE_GET_BALANCE_FAILURE, "Error in response: {}", e.getMessage());
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            log.error(PHONEPE_GET_BALANCE_FAILURE, "Error in response: {}", e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            builder.data(userWalletDetailsBuilder.build());
            handleError(errorCode, builder);
            return builder.status(httpStatus).build();
        }
    }

    @Override
    public WynkResponseEntity<Void> deLink(WalletDeLinkRequest request) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<Void> builder = WynkResponseEntity.builder();
        try {
            Wallet wallet = getWallet(getKey(request.getUid(), request.getDeviceId()));
            final String merchantId = PropertyResolverUtils.resolve(request.getClient(), PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);
            PhonePeAutoDebitUnlinkRequest phonePeAutoDebitUnlinkRequest = PhonePeAutoDebitUnlinkRequest.builder().merchantId(merchantId).userAuthToken(wallet.getAccessToken()).build();
            String requestJson = objectMapper.writeValueAsString(phonePeAutoDebitUnlinkRequest);
            HttpEntity<Map<String, String>> requestEntity = generatePayload(request.getClient(), wallet.getId().getDeviceId(), requestJson, UNLINK_API);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + UNLINK_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse unlinkResponse = response.getBody();
            if (unlinkResponse != null && unlinkResponse.isSuccess() && unlinkResponse.getCode().equalsIgnoreCase(SUCCESS)) {
                userPaymentsManager.delete(wallet);
                log.info("Wallet unliked successfully. Status: {}", unlinkResponse.getCode());
            } else {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(unlinkResponse.getCode());
            }
        } catch (HttpStatusCodeException hex) {
            log.error(PHONEPE_UNLINK_FAILURE, hex.getResponseBodyAsString());
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), PhonePeWalletResponse.class).getCode());
        } catch (Exception e) {
            log.error(PHONEPE_UNLINK_FAILURE, e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return builder.status(httpStatus).build();
        }
    }


    @Override
    public WynkResponseEntity<WalletTopUpResponse> topUp(PhonePeAutoDebitTopUpRequest<?> request) {
        final Transaction transaction = TransactionContext.get();
        final Wallet wallet = getWallet(getKey(IdentityUtils.getUidFromUserName(request.getPurchaseDetails().getUserDetails().getMsisdn(), request.getService()), request.getPurchaseDetails().getAppDetails().getDeviceId()));
        final long finalAmountToAdd = Double.valueOf(transaction.getAmount() * 100).longValue();
        final WynkResponseEntity.WynkResponseEntityBuilder<WalletTopUpResponse> builder = WynkResponseEntity.builder();
        final PhonePeWalletResponse phonePeWalletResponse = addMoney(transaction.getClientAlias(), wallet.getAccessToken(), finalAmountToAdd, request.getPhonePeVersionCode(), wallet.getId().getDeviceId());
        if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getCode().equalsIgnoreCase(SUCCESS) && phonePeWalletResponse.getData().getRedirectUrl() != null) {
            try {
                builder.data(WalletTopUpResponse.builder().info(EncryptionUtils.encrypt(phonePeWalletResponse.getData().getRedirectUrl(), paymentEncryptionKey)).build());
            } catch (Exception e) {
                ErrorCode code = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
                builder.success(false).error(TechnicalErrorDetails.builder().code(code.getInternalCode()).description(code.getInternalMessage()).build());
            }
        } else {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            ErrorCode errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(phonePeWalletResponse.getData().getCode());
            builder.success(false).error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getInternalMessage()).build());
        }
        return builder.build();
    }

    private PhonePeWalletResponse addMoney(String client, String userAuthToken, long amount, long phonePeVersionCode, String deviceId) {
        final String merchantId = PropertyResolverUtils.resolve(client, PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);
        PhonePeAutoDebitTopUpExternalRequest phonePeAutoDebitTopUpExternalRequest = PhonePeAutoDebitTopUpExternalRequest.builder()
                .merchantId(merchantId).userAuthToken(userAuthToken)
                .amount(amount).adjustAmount(true).linkType(PhonePeResponseType.WALLET_TOPUP_DEEPLINK.name())
                .deviceContext(new DeviceContext(phonePeVersionCode)).build();
        try {
            String requestJson = objectMapper.writeValueAsString(phonePeAutoDebitTopUpExternalRequest);
            HttpEntity<Map<String, String>> requestEntity = generatePayload(client, deviceId, requestJson, TOPUP_API);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + TOPUP_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse phonePeWalletResponse = response.getBody();
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).redirectUrl(phonePeWalletResponse.getData().getRedirectUrl()).code(phonePeWalletResponse.getCode()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_ADD_MONEY_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();

        } catch (Exception e) {
            log.error(PHONEPE_ADD_MONEY_FAILURE, e.getMessage(), e);
            return PhonePeWalletResponse.builder().success(false).data(PhonePeResponseData.builder().code(errorCodesCacheServiceImpl.getDefaultUnknownErrorCode().getInternalCode()).build()).build();
        }
    }


    private void saveToken(String uid, String deviceId, String walletUserId, String userAuthToken) {
        if (StringUtils.isNotBlank(userAuthToken)) {
            userPaymentsManager.save(Wallet.builder().accessToken(userAuthToken).walletUserId(walletUserId).id(getKey(uid, deviceId)).build());
        }
    }

    @Override
    public WynkResponseEntity<AutoDebitWalletCallbackResponse> handleCallback(PhonePeAutoDebitCallbackRequest request) {
        AutoDebitWalletCallbackResponse.AutoDebitWalletCallbackResponseBuilder<?, ?> callbackResponseBuilder = AutoDebitWalletCallbackResponse.builder();
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        String redirectUrl = null;
        final Transaction transaction = TransactionContext.get();
        final IChargingDetails chargingDetails = (IChargingDetails) TransactionContext.getPurchaseDetails().get();
        transaction.setStatus(TransactionStatus.FAILURE.getValue());
        WynkResponseEntity.WynkResponseEntityBuilder<AutoDebitWalletCallbackResponse> builder = WynkResponseEntity.builder();
        try {
            Wallet wallet = getWallet(getKey(IdentityUtils.getUidFromUserName(chargingDetails.getUserDetails().getMsisdn(), chargingDetails.getAppDetails().getService()), chargingDetails.getAppDetails().getDeviceId()));
            UserWalletDetails userWalletDetails = this.balance(transaction.getClientAlias(), transaction.getAmount(), wallet).getBody().getData();
            if (userWalletDetails.getBalance() >= Double.valueOf(transaction.getAmount())) {
                final long finalAmountToCharge = Double.valueOf(transaction.getAmount() * 100).longValue();
                final String merchantId = PropertyResolverUtils.resolve(transaction.getClientAlias(), PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);
                PhonePeAutoDebitDoChargingRequest phonePeAutoDebitDoChargingRequest = PhonePeAutoDebitDoChargingRequest.builder().merchantId(merchantId).userAuthToken(wallet.getAccessToken()).amount(finalAmountToCharge).deviceContext(new DeviceContext(request.getPhonePeVersionCode())).transactionId(transaction.getId().toString()).build();
                String requestJson = objectMapper.writeValueAsString(phonePeAutoDebitDoChargingRequest);
                HttpEntity<Map<String, String>> requestEntity = generatePayload(transaction.getClientAlias(), wallet.getId().getDeviceId(), requestJson, AUTO_DEBIT_API);
                ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + AUTO_DEBIT_API, requestEntity, PhonePeWalletResponse.class);
                PhonePeWalletResponse phonePeWalletResponse = response.getBody();
                if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.PAYMENT.name())) {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    redirectUrl = chargingDetails.getPageUrlDetails().getSuccessPageUrl();
                }
            }
            log.info("redirect url: {}", redirectUrl);
        } catch (HttpStatusCodeException hex) {
            log.error(PHONEPE_CHARGING_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = objectMapper.readValue(errorResponse, PhonePeWalletResponse.class);
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(phonePeWalletResponse.getCode());
        } catch (WynkRuntimeException e) {
            log.error(PHONEPE_CHARGING_FAILURE, e.getMessage());
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_FAILURE, e.getMessage());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
        } finally {
            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = chargingDetails.getPageUrlDetails().getFailurePageUrl();
            }
            handleError(errorCode, builder);
            return builder.status(httpStatus).data(callbackResponseBuilder.redirectUrl(redirectUrl).build()).build();
        }
    }

    @Override
    public PhonePeAutoDebitCallbackRequest parseCallback(Map<String, Object> payload) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(payload), PhonePeAutoDebitCallbackRequest.class);
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY006, e);
        }
    }

    @Override
    public WynkResponseEntity<AutoDebitWalletChargingResponse> charge(PhonePeChargingRequest<?> payload) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        AutoDebitWalletChargingResponse.AutoDebitWalletChargingResponseBuilder<?, ?> chargingResponseBuilder = AutoDebitWalletChargingResponse.builder();
        WynkResponseEntity.WynkResponseEntityBuilder<AutoDebitWalletChargingResponse> builder = WynkResponseEntity.builder();
        final Transaction transaction = TransactionContext.get();
        String redirectUrl = null;
        boolean deficit = false;
        boolean deeplinkGenerated = false;
        try {
            Wallet wallet = getWallet(getKey(IdentityUtils.getUidFromUserName(payload.getPurchaseDetails().getUserDetails().getMsisdn(), payload.getService()), payload.getPurchaseDetails().getAppDetails().getDeviceId()));
            UserWalletDetails userWalletDetails = this.balance(transaction.getClientAlias(), transaction.getAmount(), wallet).getBody().getData();
            final long finalAmountToCharge = Double.valueOf(transaction.getAmount() * 100).longValue();
            if (!userWalletDetails.isLinked()) {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(PHONEPE024);
                log.info("your wallet is not linked. please link your wallet");
            } else if (!userWalletDetails.isActive()) {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(PHONEPE023);
                log.info("your phonePe account is not active. please try another payment method");
            } else if (userWalletDetails.isLinked() && userWalletDetails.isActive() && userWalletDetails.getDeficitBalance() > 0d) {
                if (userWalletDetails.isAddMoneyAllowed()) {
                    deficit = true;
                    log.info("Your balance is low.Sending deeplink to add money to your wallet");
                    PhonePeWalletResponse phonePeWalletResponse = addMoney(transaction.getClientAlias(), wallet.getAccessToken(), finalAmountToCharge, payload.getPhonePeVersionCode(), wallet.getId().getDeviceId());
                    if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getCode().equalsIgnoreCase(SUCCESS) && phonePeWalletResponse.getData().getRedirectUrl() != null) {
                        transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                        deficit = true;
                        deeplinkGenerated = true;
                        chargingResponseBuilder.deficit(true).info(EncryptionUtils.encrypt(phonePeWalletResponse.getData().getRedirectUrl(), paymentEncryptionKey));
                    } else {
                        deficit = true;
                        transaction.setStatus(TransactionStatus.FAILURE.getValue());
                        errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(phonePeWalletResponse.getData().getCode());
                    }
                } else {
                    deficit = true;
                    errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(PHONEPE026);
                    log.info("your balance is low and phonePe is not allowing to add money to you wallet");
                }
            } else {
                final String merchantId = PropertyResolverUtils.resolve(transaction.getClientAlias(), PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);
                PhonePeAutoDebitDoChargingRequest phonePeAutoDebitDoChargingRequest = PhonePeAutoDebitDoChargingRequest.builder().merchantId(merchantId).userAuthToken(wallet.getAccessToken()).amount(finalAmountToCharge).deviceContext(new DeviceContext(payload.getPhonePeVersionCode())).transactionId(transaction.getId().toString()).build();
                String requestJson = objectMapper.writeValueAsString(phonePeAutoDebitDoChargingRequest);
                HttpEntity<Map<String, String>> requestEntity = generatePayload(transaction.getClientAlias(), wallet.getId().getDeviceId(), requestJson, AUTO_DEBIT_API);
                ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + AUTO_DEBIT_API, requestEntity, PhonePeWalletResponse.class);
                PhonePeWalletResponse phonePeWalletResponse = response.getBody();
                //TODO:  Will discuss and check with PhonePe, if PhonePe  is handling balance and deeplink flow in its debit api then two api(balance and addMoney) hits will reduce.

                if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.PAYMENT.name())) {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    redirectUrl = ((IChargingDetails) payload.getPurchaseDetails()).getPageUrlDetails().getSuccessPageUrl();
                }
            }

        } catch (HttpStatusCodeException hex) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            log.error(PHONEPE_CHARGING_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = objectMapper.readValue(errorResponse, PhonePeWalletResponse.class);
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(phonePeWalletResponse.getCode());
        } catch (WynkRuntimeException e) {
            log.error(PHONEPE_CHARGING_FAILURE, e.getMessage());
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_FAILURE, e.getMessage());
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = ((IChargingDetails) payload.getPurchaseDetails()).getPageUrlDetails().getFailurePageUrl();
            }
            if (deficit && !deeplinkGenerated) {
                chargingResponseBuilder.deficit(true);
            }
            handleError(errorCode, builder);
            return WynkResponseEntity.<AutoDebitWalletChargingResponse>builder().status(httpStatus).data(chargingResponseBuilder.redirectUrl(redirectUrl).build()).build();
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.warn(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "Transaction is still pending at phonepe");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.warn(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE);
        }
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    private PhonePeResponse<PhonePeTransactionResponseWrapper> getTransactionStatus(Transaction txn) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(txn.getIdStr());
        try {
            final String merchantId = PropertyResolverUtils.resolve(txn.getClientAlias(), PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_ID);
            final String salt = PropertyResolverUtils.resolve(txn.getClientAlias(), PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_SECRET);
            String apiPath = TRANS_STATUS_API_PREFIX + merchantId + SLASH + txn.getIdStr() + TRANS_STATUS_API_SUFFIX;
            String xVerifyHeader = DigestUtils.sha256Hex(apiPath + salt) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(PaymentConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            merchantTransactionEventBuilder.request(entity);
            ResponseEntity<PhonePeResponse<PhonePeTransactionResponseWrapper>> responseEntity = restTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, new ParameterizedTypeReference<PhonePeResponse<PhonePeTransactionResponseWrapper>>() {
            });
            PhonePeResponse<PhonePeTransactionResponseWrapper> response = responseEntity.getBody();
            if (response != null && response.getData() != null) {
                merchantTransactionEventBuilder.externalTransactionId(response.getData().getProviderReferenceId());
            }
            merchantTransactionEventBuilder.response(objectMapper.writeValueAsString(response));
            return response;
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Error from phonepe: {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, "Error from PhonePe " + e.getStatusCode());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Unable to verify status from Phonepe");
            throw new WynkRuntimeException(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, e.getMessage(), e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        TransactionStatus finalTransactionStatus;
        PhonePeResponse<PhonePeTransactionResponseWrapper> response = getTransactionStatus(transaction);
        if (response.isSuccess()) {
            PhonePeStatusEnum statusCode = response.getCode();
            if (statusCode == PhonePeStatusEnum.PAYMENT_SUCCESS) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    statusCode == PhonePeStatusEnum.PAYMENT_PENDING) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }
        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(response.getCode().name()).description(response.getMessage()).build());
        }
        transaction.setStatus(finalTransactionStatus.name());
    }

    private String processCallback(CallbackRequest callbackRequest) {
        try {
            // TODO:: create your own APB callback request that inherit CallbackRequest and update the impl accordingly
            Map<String, String> requestPayload = (Map<String, String>) callbackRequest;
            return Utils.getStringParameter(requestPayload, PHONEPE_VERSION_CODE);
        } catch (Exception e) {
            throw new PaymentRuntimeException(PHONEPE_CHARGING_CALLBACK_FAILURE, e.getMessage(), e);
        }
    }

    @Override
    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<UserWalletDetails> getUserPreferredPayments(PreferredPaymentDetailsRequest<?> request) {
        try {
            final double finalAmount = DiscountUtils.compute(request.getCouponId(), request.getProductDetails());
            return this.balance(request.getClientAlias(), finalAmount, getWallet(request.getPreferredPayment()));
        } catch (WynkRuntimeException e) {
            return WynkResponseEntity.<UserWalletDetails>builder().error(TechnicalErrorDetails.builder().code(e.getErrorCode()).description(e.getMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
        }
    }

    private Wallet getWallet(UserPreferredPayment userPreferredPayment) {
        try {
            Wallet wallet = (Wallet) userPreferredPayment;
            if (StringUtils.isBlank(wallet.getAccessToken())) {
                throw new WynkRuntimeException(UT022);
            } else {
                return wallet;
            }
        } catch (Exception e) {
            throw new WynkRuntimeException(UT022);
        }
    }

    private Wallet getWallet(SavedDetailsKey key) {
        Map<SavedDetailsKey, UserPreferredPayment> userPreferredPaymentMap = userPaymentsManager.get(key.getUid()).stream().collect(Collectors.toMap(UserPreferredPayment::getId, Function.identity()));
        return getWallet(userPreferredPaymentMap.getOrDefault(key, null));
    }

    private SavedDetailsKey getKey(String uid, String deviceId) {
        return SavedDetailsKey.builder().uid(uid).deviceId(deviceId).paymentGroup(WALLET).paymentCode("PHONEPE_AUTO_DEBIT").build();
    }

    private ErrorCode handleWynkRunTimeException(WynkRuntimeException e) {
        return errorCodesCacheServiceImpl.getDefaultUnknownErrorCode(e.getErrorCode(), e.getErrorTitle());
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

    private HttpEntity<Map<String, String>> generatePayload(String client, String deviceId, String requestJson, String api) {
        Map<String, String> requestMap = new HashMap<>();
        requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
        final String salt = PropertyResolverUtils.resolve(client, PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String xVerifyHeader = DigestUtils.sha256Hex(Utils.encodeBase64(requestJson) + api + salt) + X_VERIFY_SUFFIX;
        HttpHeaders headers = new HttpHeaders();
        headers.add(X_DEVICE_ID, deviceId);
        headers.add(X_VERIFY, xVerifyHeader);
        headers.add(PaymentConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(requestMap, headers);
    }

}