package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.phonepe.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.phonepe.PhonePeResponseData;
import in.wynk.payment.dto.response.phonepe.PhonePeWalletDetails;
import in.wynk.payment.dto.response.phonepe.PhonePeWalletResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
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
import java.util.Optional;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentCode.PHONEPE_AUTO_DEBIT;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;

@Slf4j
@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE)
public class PhonePeWalletAutoDebitService implements IRenewalMerchantWalletService, IUserPreferredPaymentService {

    private static final String DEBIT_API = "/v3/wallet/debit";
    private static final String TRIGGER_OTP_API = "/v3/merchant/otp/send";
    private static final String VERIFY_OTP_API = "/v3/merchant/otp/verify";
    private static final String UNLINK_API = "/v3/merchant/token/unlink";
    private static final String BALANCE_API = "/v3/wallet/balance";
    private static final String TOPUP_API = "/v3/wallet/topup";
    @Value("${payment.merchant.phonepe.id}")
    private String merchantId;
    @Value("${payment.merchant.phonepe.callback.url}")
    private String phonePeCallBackURL;
    @Value("${payment.merchant.phonepe.api.base.url}")
    private String phonePeBaseUrl;
    @Value("${payment.merchant.phonepe.salt}")
    private String salt;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;
    @Autowired
    private IUserPaymentsManager userPaymentsManager;
    private final Gson gson;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    public PhonePeWalletAutoDebitService(Gson gson,
                                         PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher,
                                         @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        this.gson = gson;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.restTemplate = restTemplate;
    }

    @Override
    public BaseResponse<?> linkRequest(WalletLinkRequest request) {
        return new BaseResponse<>(sendOTP(request.getEncSi()), HttpStatus.OK, null);
    }

    @Override
    public BaseResponse<?> validateLink(WalletValidateLinkRequest request) {
        return new BaseResponse<>(verifyOtp(request.getOtp()), HttpStatus.OK, null);
    }

    @Override
    public BaseResponse<?> unlink() {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String userAuthToken = getAccessToken(uid);
        PhonePeAutoDebitOtpRequest phonePePaymentRequest = PhonePeAutoDebitOtpRequest.builder().merchantId(merchantId).userAuthToken(userAuthToken).build();
        try {
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
            userPaymentsManager.deletePaymentDetails(userPaymentsManager.getPaymentDetails(getKey(uid)));
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
    public BaseResponse<PhonePeWalletResponse> balance(String uid, String planId, String deviceId) {
        String userAuthToken = getAccessToken(uid);
        return new BaseResponse<>(balance(userAuthToken, deviceId), HttpStatus.OK, null);
    }

    @Override
    public BaseResponse<?> addMoney(WalletAddMoneyRequest request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String userAuthToken = getAccessToken(uid);
        return new BaseResponse<>(addMoney(userAuthToken, Double.valueOf(request.getAmountToCredit()).longValue(), request.getPhonePeVersionCode()), HttpStatus.OK, null);
    }

    private Key getKey(String uid) {
        return Key.builder().uid(uid).paymentGroup(WALLET).paymentCode(PHONEPE_AUTO_DEBIT.name()).build();
    }

    private PhonePeWalletResponse sendOTP(String mobileNumber) {
        try {
            PhonePePaymentRequest phonePePaymentRequest = PhonePePaymentRequest.builder().merchantId(merchantId).mobileNumber(mobileNumber).build();
            String requestJson = gson.toJson(phonePePaymentRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + TRIGGER_OTP_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + TRIGGER_OTP_API, requestEntity, PhonePeWalletResponse.class);
            Session<SessionDTO> session = SessionContextHolder.get();
            PhonePeWalletResponse phonePeWalletResponse = response.getBody();
            SessionDTO sessionDTO = session.getBody();
            sessionDTO.put(PHONEPE_OTP_TOKEN, phonePeWalletResponse.getData().getOtpToken());
            sessionDTO.put(WALLET_USER_ID,mobileNumber);
            sessionDTO.put(MSISDN,mobileNumber);
            sessionDTO.put(UID,MsisdnUtils.getUidFromMsisdn(mobileNumber));
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_OTP_SEND_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();
        } catch (Exception e) {
            log.error(PHONEPE_OTP_SEND_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_OTP_SEND_FAILURE, e.getMessage(), e);
        }
    }

    private PhonePeWalletResponse verifyOtp(String otp) {
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String otpToken = sessionDTO.get(PHONEPE_OTP_TOKEN);
            String deviceId = sessionDTO.get(DEVICE_ID);
            PhonePeAutoDebitOtpRequest phonePeAutoDebitOtpRequest = PhonePeAutoDebitOtpRequest.builder().merchantId(merchantId).otp(otp).otpToken(otpToken).build();
            String requestJson = gson.toJson(phonePeAutoDebitOtpRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + VERIFY_OTP_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_DEVICE_ID, deviceId);
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + VERIFY_OTP_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse phonePeWalletResponse = response.getBody();
            saveToken(phonePeWalletResponse.getData().getUserAuthToken());
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();
        } catch (Exception e) {
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_OTP_VERIFICATION_FAILURE, e.getMessage(), e);
        }
    }

    private PhonePeWalletResponse balance(String userAuthToken, String deviceId) {
        PhonePeAutoDebitRequest phonePeAutoDebitRequest = PhonePeAutoDebitRequest.builder().merchantId(merchantId).userAuthToken(userAuthToken).build();
        try {
            String requestJson = gson.toJson(phonePeAutoDebitRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + BALANCE_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_DEVICE_ID, deviceId);
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + BALANCE_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse phonePeWalletResponse = response.getBody();
            PhonePeResponseData data = PhonePeResponseData.builder().wallet(phonePeWalletResponse.getData().getWallet()).linkedUser(phonePeWalletResponse.getData().getLinkedUser()).code(phonePeWalletResponse.getCode()).message(phonePeWalletResponse.getMessage()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_GET_BALANCE_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            return PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build();

        } catch (Exception e) {
            log.error(PHONEPE_GET_BALANCE_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_GET_BALANCE_FAILURE, e.getMessage(), e);
        }
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
            String xVerifyHeader = Utils.encodeBase64(requestJson) + TOPUP_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + X_VERIFY_SUFFIX;
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
            throw new WynkRuntimeException(PHONEPE_GET_BALANCE_FAILURE, e.getMessage(), e);
        }
    }


    private void saveToken(String userAuthToken) {
        if (StringUtils.isNotBlank(userAuthToken)) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = sessionDTO.get(WALLET_USER_ID);
            String uid = sessionDTO.get(UID);
            userPaymentsManager.savePaymentDetails(Wallet.builder()
                    .accessToken(userAuthToken)
                    .walletUserId(walletUserId)
                    .id(getKey(uid))
                    .build());
        }
    }

    private String getAccessToken(String uid) {
        String accessToken;
        UserPreferredPayment userPreferredPayment = userPaymentsManager.getPaymentDetails(getKey(uid));
        if (Objects.nonNull(userPreferredPayment)) {
            Wallet wallet = (Wallet) userPreferredPayment;
            accessToken = wallet.getAccessToken();
            if (org.apache.commons.lang3.StringUtils.isBlank(accessToken)) {
                throw new WynkRuntimeException(WynkErrorType.UT018);
            }
        } else {
            throw new WynkRuntimeException(WynkErrorType.UT022);
        }
        return accessToken;
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
        final Transaction transaction = TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String deviceId = sessionDTO.get(DEVICE_ID);
        String userAuthToken = getAccessToken(uid);
        PhonePeWalletResponse phonePeWalletBalanceResponse = balance(userAuthToken, deviceId);
        if (phonePeWalletBalanceResponse.isSuccess() && phonePeWalletBalanceResponse.getData().getWallet().getAvailableBalance() < (Double.valueOf(transaction.getAmount() * 100).longValue())) {

            return new BaseResponse<>(addMoney(userAuthToken, Double.valueOf(transaction.getAmount() * 100).longValue(), sessionDTO.get("phonePeVersionCode")), HttpStatus.OK, null);
        }
        PhonePeAutoDebitChargeRequest payload = (PhonePeAutoDebitChargeRequest) chargingRequest;
        PhonePeAutoDebitChargeRequest peAutoDebitChargeRequest = PhonePeAutoDebitChargeRequest.builder().merchantId(merchantId).userAuthToken(userAuthToken).amount(Double.valueOf(transaction.getAmount() * 100).longValue()).deviceContext(payload.getDeviceContext()).transactionId(transaction.getId().toString()).build();
        try {
            String requestJson = gson.toJson(peAutoDebitChargeRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + DEBIT_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + X_VERIFY_SUFFIX;
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_DEVICE_ID, deviceId);
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeWalletResponse> response = restTemplate.postForEntity(phonePeBaseUrl + DEBIT_API, requestEntity, PhonePeWalletResponse.class);
            PhonePeWalletResponse phonePeWalletResponse = response.getBody();
            PhonePeResponseData data=null;
            if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.WALLET_TOPUP_DEEPLINK.name())) {
                 data = PhonePeResponseData.builder().redirectUrl(phonePeWalletResponse.getData().getRedirectUrl()).responseType(PhonePeResponseType.WALLET_TOPUP_DEEPLINK.name()).message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            }
            else if (phonePeWalletResponse.isSuccess() && phonePeWalletResponse.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.PAYMENT.name())) {
                 data = PhonePeResponseData.builder().redirectUrl(phonePeWalletResponse.getData().getRedirectUrl()).responseType(PhonePeResponseType.PAYMENT.name()).transactionId(phonePeWalletResponse.getData().getTransactionId()).message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).amount(phonePeWalletResponse.getData()
                        .getAmount()).paymentState(phonePeWalletResponse.getData().getPaymentState()).providerReferenceId(phonePeWalletResponse.getData().getProviderReferenceId()).build();
            }
            return new BaseResponse<>(PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build(), HttpStatus.OK, null);
        } catch (HttpStatusCodeException hex) {

            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_CHARGING_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse phonePeWalletResponse = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = phonePeWalletResponse.getData().builder().message(phonePeWalletResponse.getMessage()).code(phonePeWalletResponse.getCode()).build();
            return new BaseResponse<>(PhonePeWalletResponse.builder().success(phonePeWalletResponse.isSuccess()).data(data).build(), HttpStatus.OK, null);
        } catch (Exception e) {
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
        }
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {

    }

    @Override
    public BaseResponse<?> status(AbstractTransactionStatusRequest transactionStatusRequest) {
        ChargingStatusResponse chargingStatus;
        Transaction transaction = TransactionContext.get();
        switch (transactionStatusRequest.getMode()) {
            case SOURCE:
                chargingStatus = getStatusFromPhonePe(transaction);
                break;
            case LOCAL:
                chargingStatus = fetchChargingStatusFromDataSource(transaction);
                break;
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(chargingStatus).build();
    }

    private ChargingStatusResponse fetchChargingStatusFromDataSource(Transaction transaction) {
        return ChargingStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).planId(transaction.getPlanId()).validity(cachingService.validTillDate(transaction.getPlanId())).build();
    }

    private PhonePeResponse<PhonePeTransactionResponseWrapper> getTransactionStatus(Transaction txn) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(txn.getIdStr());
        try {
            String apiPath = TRANS_STATUS_API_PREFIX + merchantId + SLASH + txn.getIdStr() + TRANS_STATUS_API_SUFFIX;
            String xVerifyHeader = apiPath + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
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

    private ChargingStatusResponse getStatusFromPhonePe(Transaction transaction) {
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "Transaction is still pending at phonepe");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE);
        }
        return ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build();
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
    public PhonePeWalletDetails getUserPreferredPayments(String uid, String planId, String deviceId) {
        PhonePeWalletResponse phonePeWalletResponse = this.balance(uid, planId, deviceId).getBody();
        if (phonePeWalletResponse.isSuccess()) {
            PhonePeWallet phonePeWallet = phonePeWalletResponse.getData().getWallet();
            Wallet wallet = (Wallet) userPaymentsManager.getPaymentDetails(getKey(uid));
            return PhonePeWalletDetails.builder()
                    .linked(true)
                    .active(phonePeWallet.getWalletActive())
                    .linkedMobileNo(wallet.getWalletUserId())
                    .walletCode(phonePeWalletResponse.getCode())
                    .balance(phonePeWallet.getAvailableBalance())
                    .build();
        } else {
            return PhonePeWalletDetails.builder().active(false).build();
        }
    }

}