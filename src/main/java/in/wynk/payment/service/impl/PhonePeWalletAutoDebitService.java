package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.*;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.SavedDetailsKey;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.ErrorCode;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.phonepe.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.dto.response.ChargingResponse;
import in.wynk.payment.dto.response.phonepe.PhonePeResponseData;
import in.wynk.payment.dto.response.phonepe.PhonePeWalletResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.exception.WynkErrorType.UT022;
import static in.wynk.payment.core.constant.PaymentCode.PHONEPE_AUTO_DEBIT;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.PHONEPE_OTP_TOKEN;

@Slf4j
@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE)
public class PhonePeWalletAutoDebitService extends AbstractMerchantPaymentStatusService implements IRenewalMerchantWalletService, IUserPreferredPaymentService {

    private static final String DEBIT_API = "/v3/wallet/debit";
    private static final String TRIGGER_OTP_API = "/v3/merchant/otp/send";
    private static final String VERIFY_OTP_API = "/v3/merchant/otp/verify";
    private static final String UNLINK_API = "/v3/merchant/token/unlink";
    private static final String BALANCE_API = "/v3/wallet/balance";
    private static final String TOPUP_API = "/v3/wallet/topup";
    @Value("{payment.encryption.key}")
    private String paymentEncryptionKey;
    @Value("${payment.merchant.phonepe.id}")
    private String merchantId;
    @Value("${payment.merchant.phonepe.callback.url}")
    private String phonePeCallBackURL;
    @Value("${payment.merchant.phonepe.api.base.url}")
    private String phonePeBaseUrl;
    @Value("${payment.merchant.phonepe.salt}")
    private String salt;
    @Value("${payment.success.page}")
    private String successPage;
    @Value("${payment.failure.page}")
    private String failurePage;
    @Autowired
    private IUserPaymentsManager userPaymentsManager;
    private final Gson gson;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentCachingService paymentCachingService;

    public PhonePeWalletAutoDebitService(Gson gson,
                                         PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher,
                                         @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, ObjectMapper objectMapper, PaymentCachingService paymentCachingService) {
        super(cachingService);
        this.gson = gson;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.paymentCachingService = paymentCachingService;
    }

    @Override
    public BaseResponse<?> linkRequest(WalletLinkRequest request) {

        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            String phone = request.getEncSi();
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            sessionDTO.put(WALLET_USER_ID, phone);
            log.info("Sending OTP to {} via PhonePe", phone);
            PhonePePaymentRequest phonePePaymentRequest = PhonePePaymentRequest.builder().merchantId(merchantId).mobileNumber(phone).build();
            String requestJson = gson.toJson(phonePePaymentRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = DigestUtils.sha256Hex(Utils.encodeBase64(requestJson) + TRIGGER_OTP_API + salt) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + TRIGGER_OTP_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse linkRequestResponse = response.getBody();
            if (linkRequestResponse!=null && linkRequestResponse.isSuccess() && linkRequestResponse.getCode().equalsIgnoreCase(SUCCESS) && linkRequestResponse.getData().getOtpToken()!=null) {
                sessionDTO.put(PHONEPE_OTP_TOKEN, linkRequestResponse.getData().getOtpToken());
                log.info("Otp sent successfully. Status: {}", linkRequestResponse.getCode());
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(linkRequestResponse.getCode());
            }
        } catch (HttpStatusCodeException hex) {
            log.error(PHONEPE_OTP_SEND_FAILURE, hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), PhonePeWalletResponse.class).getCode());
        } catch (Exception e) {
            log.error(PHONEPE_OTP_SEND_FAILURE, e.getMessage());
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
    public BaseResponse<?> validateLink(WalletValidateLinkRequest request) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String otpToken = sessionDTO.get(PHONEPE_OTP_TOKEN);
            String deviceId = sessionDTO.get(DEVICE_ID);
            PhonePeAutoDebitOtpRequest phonePeAutoDebitOtpRequest = PhonePeAutoDebitOtpRequest.builder().merchantId(merchantId).otp(request.getOtp()).otpToken(otpToken).build();
            String requestJson = gson.toJson(phonePeAutoDebitOtpRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = DigestUtils.sha256Hex(Utils.encodeBase64(requestJson) + VERIFY_OTP_API + salt) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_DEVICE_ID, deviceId);
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + VERIFY_OTP_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse  verifyOtpResponse = response.getBody();
            if (verifyOtpResponse!=null && verifyOtpResponse.isSuccess() && verifyOtpResponse.getCode().equalsIgnoreCase(SUCCESS) && verifyOtpResponse.getData().getUserAuthToken()!=null) {
                saveToken(verifyOtpResponse.getData().getUserAuthToken());
                log.info("Otp validated successfully. Status: {}", verifyOtpResponse.getCode());
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(verifyOtpResponse.getCode());
            }
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update("otpValidated", false);
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, "Error in response while verifying otp: {}", hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), PhonePeWalletResponse.class).getCode());
        } catch (Exception e) {
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, "Error in response while verifying otp: {}", e.getMessage(), e);
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
    public BaseResponse<?> balance(int planId) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        return balance(planId, getWallet(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID))));
    }

    public BaseResponse<WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails>> balance(int planId, Wallet wallet) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        UserWalletDetails.UserWalletDetailsBuilder userWalletDetailsBuilder = UserWalletDetails.builder();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<UserWalletDetails>builder();
        try {
            PhonePeAutoDebitRequest walletRequest = PhonePeAutoDebitRequest.builder().merchantId(merchantId).userAuthToken(wallet.getAccessToken()).build();
            String requestJson = gson.toJson(walletRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + BALANCE_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_DEVICE_ID, wallet.getId().getDeviceId());
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + BALANCE_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse walletResponse = response.getBody();
            if (walletResponse!=null && walletResponse.isSuccess() && walletResponse.getCode().equalsIgnoreCase(SUCCESS) && walletResponse.getData().getWallet().getWalletActive()){
                double deficitBalance=0;
                double finalAmount=paymentCachingService.getPlan(planId).getFinalPrice();
                double usableBalance=walletResponse.getData().getWallet().getUsableBalance()/100d;
                if (usableBalance<finalAmount){
                    deficitBalance=finalAmount-walletResponse.getData().getWallet().getUsableBalance();
                }
                AnalyticService.update("PHONEPE_CODE", walletResponse.getCode());
                builder.data(userWalletDetailsBuilder
                        .linked(true)
                        .active(true)
                        .balance(usableBalance)
                        .deficitBalance(deficitBalance)
                        .addMoneyAllowed(walletResponse.getData().getWallet().getWalletTopupSuggested())
                        .linkedMobileNo(wallet.getWalletUserId())
                        .build());
            }
            else {

                errorCode = ErrorCode.getErrorCodesFromExternalCode(walletResponse.getCode());
                if(!walletResponse.getData().getWallet().getWalletActive()){
                    errorCode= ErrorCode.getErrorCodesFromExternalCode(ErrorCode.PHONEPE023.name());
                }
                builder.data(userWalletDetailsBuilder.linked(true).build());
            }
        } catch (HttpStatusCodeException hex) {
            builder.data(userWalletDetailsBuilder.linked(true).build());
            log.error(PHONEPE_GET_BALANCE_FAILURE, "Error in response: {}", hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), PhonePeWalletResponse.class).getCode());
        } catch (WynkRuntimeException e) {
            log.error(PHONEPE_GET_BALANCE_FAILURE, "Error in response: {}", e.getMessage());
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            log.error(PHONEPE_GET_BALANCE_FAILURE, "Error in response: {}", e.getMessage());
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            handleError(errorCode, builder);
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse<UserWalletDetails>>builder().status(httpStatus).body(builder.build()).build();
        }
    }

    @Override
    public BaseResponse<?> unlink() {
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            Wallet wallet = getWallet(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)));
            PhonePeAutoDebitOtpRequest phonePePaymentRequest = PhonePeAutoDebitOtpRequest.builder().merchantId(merchantId).userAuthToken(wallet.getAccessToken()).build();
            String requestJson = gson.toJson(phonePePaymentRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + UNLINK_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + UNLINK_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse phonePeWalletResponse = response.getBody();
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).build();
            userPaymentsManager.delete(wallet);
            return new BaseResponse<>(PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build(), HttpStatus.OK, null);

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_UNLINK_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).build();
            return new BaseResponse<>(PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build(), HttpStatus.OK, null);

        } catch (Exception e) {
            log.error(PHONEPE_UNLINK_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_UNLINK_FAILURE, e.getMessage(), e);
        }
    }


    @Override
    public BaseResponse<?> addMoney(WalletAddMoneyRequest request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Wallet wallet = getWallet(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)));
        return new BaseResponse<>(addMoney(wallet.getAccessToken(), Double.valueOf(request.getAmountToCredit()).longValue(), request.getPhonePeVersionCode()), HttpStatus.OK, null);
    }

    private PhonePeWalletResponse addMoney(String userAuthToken, long amount, long phonePeVersionCode) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String deviceId = sessionDTO.get(DEVICE_ID);
        PhonePeAutoDebitTopupRequest phonePePaymentRequest = PhonePeAutoDebitTopupRequest.builder()
                .merchantId(merchantId).userAuthToken(userAuthToken)
                .amount(amount).adjustAmount(true).linkType(PhonePeResponseType.WALLET_TOPUP_DEEPLINK.name())
                .deviceContext(new DeviceContext(phonePeVersionCode)).build();
        try {
            String requestJson = gson.toJson(phonePePaymentRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = DigestUtils.sha256Hex(Utils.encodeBase64(requestJson) + TOPUP_API + salt) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_DEVICE_ID, deviceId);
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
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
            throw new WynkRuntimeException(PHONEPE_ADD_MONEY_FAILURE, e.getMessage(), e);
        }
    }


    private void saveToken(String userAuthToken) {
        if (StringUtils.isNotBlank(userAuthToken)) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = sessionDTO.get(WALLET_USER_ID);
            userPaymentsManager.save(Wallet.builder()
                    .accessToken(userAuthToken)
                    .walletUserId(walletUserId)
                    .id(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)))
                    .build());
        }
    }

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        try {
            PhonePeAutoDebitChargeRequest request =PhonePeAutoDebitChargeRequest.builder().deviceContext(new DeviceContext(Long.parseLong(processCallback(callbackRequest)))).build();
            return doCharging(request);
        }
      catch (Exception e) {
            throw new PaymentRuntimeException(PHONEPE_CHARGING_CALLBACK_FAILURE, e.getMessage(), e);
        }
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        ChargingResponse.ChargingResponseBuilder chargingResponseBuilder = ChargingResponse.builder();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<ChargingResponse>builder();
        final Transaction transaction = TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String sid = SessionContextHolder.getId();
        String redirectUrl = null;
        boolean deficit=false;
        boolean deeplinkGenerated=false;
        try {
            PhonePeAutoDebitChargeRequest payload = (PhonePeAutoDebitChargeRequest) chargingRequest;
            Wallet wallet = getWallet(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)));
            UserWalletDetails userWalletDetails = (UserWalletDetails) this.balance(transaction.getPlanId(), wallet).getBody().getData();
            PhonePeAutoDebitChargeRequest peAutoDebitChargeRequest = PhonePeAutoDebitChargeRequest.builder().merchantId(merchantId).userAuthToken(wallet.getAccessToken()).amount(Double.valueOf(transaction.getAmount() * 100).longValue()).deviceContext(payload.getDeviceContext()).transactionId(transaction.getId().toString()).build();
            if (!userWalletDetails.isLinked()) {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(ErrorCode.PHONEPE023.name());
                log.info("your wallet is not linked. please link your wallet");
            } else if (!userWalletDetails.isActive()) {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(ErrorCode.PHONEPE024.name());
                log.info("your phonePe account is not active. please try another payment method");
            } else if (userWalletDetails.isLinked() && userWalletDetails.isActive() && userWalletDetails.getDeficitBalance() > 0d) {
                if (userWalletDetails.isAddMoneyAllowed()) {
                    errorCode = ErrorCode.getErrorCodesFromExternalCode(ErrorCode.PHONEPE025.name());
                    deficit=true;
                    log.info("Your balance is low.Sending deeplink to add money to your wallet");
                    PhonePeWalletResponse phonePeWalletResponse = addMoney(wallet.getAccessToken(), Double.valueOf(transaction.getAmount() * 100).longValue(), payload.getDeviceContext().getPhonePeVersionCode());
                    if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getCode().equalsIgnoreCase(SUCCESS) && phonePeWalletResponse.getData().getRedirectUrl() != null) {
                        transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                        deficit=true;
                        deeplinkGenerated=true;
                        chargingResponseBuilder.deficit(true).info(EncryptionUtils.encrypt(phonePeWalletResponse.getData().getRedirectUrl(), paymentEncryptionKey));
//                        chargingResponse = ChargingResponse.builder().deficit(true).info(EncryptionUtils.encrypt(phonePeWalletResponse.getData().getRedirectUrl(), paymentEncryptionKey)).build();
                    } else {
                         deficit=true;
                         transaction.setStatus(TransactionStatus.FAILURE.getValue());
                        errorCode = ErrorCode.getErrorCodesFromExternalCode(phonePeWalletResponse.getData().getCode());
                    }
                } else {
                    deficit=true;
                    errorCode = ErrorCode.getErrorCodesFromExternalCode(ErrorCode.PHONEPE026.name());
                    log.info("your balance is low and phonePe is not allowing to add money to you wallet");
                }
            } else {
                String requestJson = gson.toJson(peAutoDebitChargeRequest);
                Map<String, String> requestMap = new HashMap<>();
                requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
                String xVerifyHeader = DigestUtils.sha256Hex(Utils.encodeBase64(requestJson) + DEBIT_API + salt) + X_VERIFY_SUFFIX;
                HttpHeaders headers = new HttpHeaders();
                headers.add(X_DEVICE_ID, wallet.getId().getDeviceId());
                headers.add(X_VERIFY, xVerifyHeader);
                headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
                ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + DEBIT_API, requestEntity, PhonePeWalletResponse.class);
                PhonePeWalletResponse phonePeWalletResponse = response.getBody();
 //TODO:  Will discuss and check with PhonePe, if PhonePe  is handling balance and deeplink flow in its debit api then two api(balance and addMoney) hits will reduce.
                /*if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.WALLET_TOPUP_DEEPLINK.name())) {
                    transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                    deficit=true;
                    deeplinkGenerated=true;
                    errorCode = ErrorCode.getErrorCodesFromExternalCode(ErrorCode.PHONEPE025.name());
                    chargingResponse = ChargingResponse.builder().deficit(true).info(EncryptionUtils.encrypt(phonePeWalletResponse.getData().getRedirectUrl(), paymentEncryptionKey)).build();
                } */
                  if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.PAYMENT.name())) {
                     transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    redirectUrl = successPage + sid;
                }
            }

        }
        catch (HttpStatusCodeException hex) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            log.error(PHONEPE_CHARGING_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            errorCode = ErrorCode.getErrorCodesFromExternalCode(phonePeWalletResponse.getCode());
        }
        catch (WynkRuntimeException e) {
            log.error(PHONEPE_CHARGING_FAILURE, e.getMessage());
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_FAILURE, e.getMessage());
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = failurePage+sid;
            }
            if(deficit && !deeplinkGenerated){
                chargingResponseBuilder.deficit(true);
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

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "Transaction is still pending at phonepe");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE);
        }
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    private PhonePeResponse<PhonePeTransactionResponseWrapper> getTransactionStatus(Transaction txn) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(txn.getIdStr());
        try {
            String apiPath = TRANS_STATUS_API_PREFIX + merchantId + SLASH + txn.getIdStr() + TRANS_STATUS_API_SUFFIX;
            String xVerifyHeader = DigestUtils.sha256Hex(apiPath + salt) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            merchantTransactionEventBuilder.request(entity);
            ResponseEntity<PhonePeResponse<PhonePeTransactionResponseWrapper>> responseEntity = restTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, new ParameterizedTypeReference<PhonePeResponse<PhonePeTransactionResponseWrapper>>() {
            });
            PhonePeResponse<PhonePeTransactionResponseWrapper> response = responseEntity.getBody();
            if (response != null && response.getData() != null) {
                merchantTransactionEventBuilder.externalTransactionId(response.getData().getProviderReferenceId());
            }
            merchantTransactionEventBuilder.response(gson.toJson(response));
            return response;
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Error from phonepe: {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, "Error from PhonePe " + e.getStatusCode().toString());
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
            Map<String, String> requestPayload = (Map<String, String>) callbackRequest.getBody();
            String phonePeVersion = Utils.getStringParameter(requestPayload, "phonePeVersionCode");
            return phonePeVersion;
        } catch (Exception e) {
            throw new PaymentRuntimeException(PHONEPE_CHARGING_CALLBACK_FAILURE, e.getMessage(), e);
        }
    }

    @Override
    public WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> getUserPreferredPayments(UserPreferredPayment userPreferredPayment, int planId) {
        try {
            return this.balance(planId, getWallet(userPreferredPayment)).getBody();
        } catch (WynkRuntimeException e) {
            return WynkResponseEntity.WynkBaseResponse.<AbstractPaymentDetails>builder().error(TechnicalErrorDetails.builder().code(e.getErrorCode()).description(e.getMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
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
        return SavedDetailsKey.builder().uid(uid).deviceId(deviceId).paymentGroup(WALLET).paymentCode(PHONEPE_AUTO_DEBIT.name()).build();
    }

    private ErrorCode handleWynkRunTimeException(WynkRuntimeException e) {
        ErrorCode errorCode = ErrorCode.UNKNOWN;
        errorCode.setInternalCode(e.getErrorCode());
        errorCode.setInternalMessage(e.getErrorTitle());
        return errorCode;
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