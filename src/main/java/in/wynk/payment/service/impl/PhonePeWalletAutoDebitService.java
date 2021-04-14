package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.paytm.WalletAddMoneyRequest;
import in.wynk.payment.dto.paytm.WalletLinkRequest;
import in.wynk.payment.dto.paytm.WalletValidateLinkRequest;
import in.wynk.payment.dto.phonepe.*;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.phonepe.PhonePeResponseData;
import in.wynk.payment.dto.response.phonepe.PhonePeWalletResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.IMerchantWalletService;
import in.wynk.payment.service.IRenewalMerchantWalletService;
import in.wynk.payment.service.IUserPaymentsManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentCode.PHONEPE_WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.paytm.PayTmConstants.WALLET_USER_ID;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;
@Slf4j
@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE)

public class PhonePeWalletAutoDebitService implements IMerchantWalletService, IRenewalMerchantWalletService {


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

        WalletLinkRequest request1 = request;
        log.info(request1.getEncSi());
        return new BaseResponse<>(sendOTP(request1.getEncSi()), HttpStatus.OK, null);
    }

    @Override
    public BaseResponse<?> validateLink(WalletValidateLinkRequest request) {
        WalletValidateLinkRequest request1 = request;
        return new BaseResponse<>(verifyOtp(request1.getOtp()), HttpStatus.OK, null);
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
            PhonePeWalletResponse response1 = response.getBody();
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            userPaymentsManager.deletePaymentDetails(uid, PHONEPE_WALLET);
            return new BaseResponse<>(response2, HttpStatus.OK, null);

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_UNLINK_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return new BaseResponse<>(response2, HttpStatus.OK, null);

        } catch (Exception e) {
            log.error(PHONEPE_UNLINK_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_UNLINK_FAILURE, e.getMessage(), e);
        }
    }

    @Override
    public BaseResponse<?> balance() {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String userAuthToken = getAccessToken(uid);
        return new BaseResponse<>(balance(userAuthToken), HttpStatus.OK, null);
    }

    @Override
    public BaseResponse<?> addMoney(WalletAddMoneyRequest request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String userAuthToken = getAccessToken(uid);
        return new BaseResponse<>(addMoney(userAuthToken, Double.valueOf(request.getAmountToCredit()).longValue(), request.getPhonePeVersionCode()), HttpStatus.OK, null);
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
            PhonePeWalletResponse response1 = response.getBody();
            SessionDTO sessionDTO = session.getBody();
            sessionDTO.put(SessionKeys.PHONEPE_OTP_TOKEN, response1.getData().getOtpToken());
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_OTP_SEND_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;
        } catch (Exception e) {
            log.error(PHONEPE_OTP_SEND_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_OTP_SEND_FAILURE, e.getMessage(), e);
        }
    }

    private PhonePeWalletResponse verifyOtp(String otp) {
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String otpToken = sessionDTO.get(SessionKeys.PHONEPE_OTP_TOKEN);
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
            PhonePeWalletResponse response1 = response.getBody();
            saveToken(response1.getData().getUserAuthToken());
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;
        } catch (Exception e) {
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_OTP_VERIFICATION_FAILURE, e.getMessage(), e);
        }
    }

    private PhonePeWalletResponse balance(String userAuthToken) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String deviceId = sessionDTO.get(DEVICE_ID);
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
            PhonePeWalletResponse response1 = response.getBody();
            PhonePeResponseData data = PhonePeResponseData.builder().wallet(response1.getData().getWallet()).linkedUser(response1.getData().getLinkedUser()).code(response1.getCode()).message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_GET_BALANCE_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

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
            PhonePeWalletResponse response1 = response.getBody();
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).redirectUrl(response1.getData().getRedirectUrl()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_ADD_MONEY_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = PhonePeResponseData.builder().message(response1.getMessage()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (Exception e) {
            log.error(PHONEPE_ADD_MONEY_FAILURE, e.getMessage(), e);
            throw new WynkRuntimeException(PHONEPE_GET_BALANCE_FAILURE, e.getMessage(), e);
        }
    }


    private void saveToken(String userAuthToken) {
        if (org.apache.commons.lang3.StringUtils.isNotBlank(userAuthToken)) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = sessionDTO.get(WALLET_USER_ID);
            String uid = sessionDTO.get(UID);
            Wallet wallet = new Wallet.Builder().paymentCode(PHONEPE_WALLET).walletUserId(walletUserId).accessToken(userAuthToken).build();
            userPaymentsManager.saveWalletToken(uid, wallet);
        }
    }

    private String getAccessToken(String uid) {
        String accessToken;
        UserPreferredPayment userPreferredPayment = userPaymentsManager.getPaymentDetails(uid, PHONEPE_WALLET);
        if (Objects.nonNull(userPreferredPayment)) {
            Wallet wallet = (Wallet) userPreferredPayment.getOption();
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
        String returnUrl = processCallback(callbackRequest);
        return BaseResponse.redirectResponse(returnUrl);
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        final Transaction transaction = TransactionContext.get();
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String deviceId = sessionDTO.get(DEVICE_ID);
        String userAuthToken = getAccessToken(uid);
        PhonePeAutoDebitChargeRequest payload= (PhonePeAutoDebitChargeRequest) chargingRequest;
        PhonePeAutoDebitChargeRequest peAutoDebitChargeRequest = PhonePeAutoDebitChargeRequest.builder().merchantId(merchantId).userAuthToken(userAuthToken).amount(Double.valueOf(transaction.getAmount()).longValue()).deviceContext(payload.getDeviceContext()).build();
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
            PhonePeWalletResponse response1 = response.getBody();
            PhonePeWalletResponse response2=null;
            if(response1.isSuccess() && response1.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.WALLET_TOPUP_DEEPLINK.name())){
                PhonePeResponseData data = PhonePeResponseData.builder().redirectUrl(response1.getData().getRedirectUrl()).responseType(PhonePeResponseType.WALLET_TOPUP_DEEPLINK.name()).message(response1.getMessage()).code(response1.getCode()).build();
                response2=PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            }
            else if(response1.isSuccess() && response1.getData().getResponseType().equalsIgnoreCase(PhonePeResponseType.PAYMENT.name())){
                PhonePeResponseData data = PhonePeResponseData.builder().redirectUrl(response1.getData().getRedirectUrl()).responseType(PhonePeResponseType.PAYMENT.name()).transactionId(response1.getData().getTransactionId()).message(response1.getMessage()).code(response1.getCode()).amount(response1.getData()
                        .getAmount()).paymentState(response1.getData().getPaymentState()).providerReferenceId(response1.getData().getProviderReferenceId()).build();
                response2=PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            }
            return new BaseResponse<>(response2, HttpStatus.OK, null);

        } catch (HttpStatusCodeException hex) {

            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_CHARGING_FAILURE, hex.getResponseBodyAsString());
            String errorResponse = hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data = response1.getData().builder().message(response1.getMessage()).code(response1.getCode()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return new BaseResponse<>(response2, HttpStatus.OK, null);
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


    private PhonePeTransactionResponse getTransactionStatus(Transaction txn) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(txn.getIdStr());
        try {
            String prefixStatusApi = "/v3/transaction/" + merchantId + "/";
            String suffixStatusApi = "/status";
            String apiPath = prefixStatusApi + txn.getIdStr() + suffixStatusApi;
            String xVerifyHeader = apiPath + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            merchantTransactionEventBuilder.request(entity);
            ResponseEntity<PhonePeTransactionResponse> responseEntity = restTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, PhonePeTransactionResponse.class, new HashMap<>());
            PhonePeTransactionResponse phonePeTransactionResponse = responseEntity.getBody();
            if (phonePeTransactionResponse != null && phonePeTransactionResponse.getCode() != null) {
                log.info("PhonePe txn response for transaction Id {} :: {}", txn.getIdStr(), phonePeTransactionResponse);
            }
            if (phonePeTransactionResponse.getData() != null)
                merchantTransactionEventBuilder.externalTransactionId(phonePeTransactionResponse.getData().providerReferenceId);
            merchantTransactionEventBuilder.response(phonePeTransactionResponse);
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
            return phonePeTransactionResponse;
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
        PhonePeTransactionResponse phonePeTransactionStatusResponse = getTransactionStatus(transaction);
        if (phonePeTransactionStatusResponse.getSuccess()) {
            PhonePeTransactionStatus statusCode = phonePeTransactionStatusResponse.getCode();
            if (statusCode == PhonePeTransactionStatus.PAYMENT_SUCCESS) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    statusCode == PhonePeTransactionStatus.PAYMENT_PENDING) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }

        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(phonePeTransactionStatusResponse.getCode().name()).description(phonePeTransactionStatusResponse.getMessage()).build());
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
        final Transaction transaction = TransactionContext.get();
        try {
            Map<String, String> requestPayload = (Map<String, String>) callbackRequest.getBody();
            PhonePeTransactionResponse phonePeTransactionResponse = new PhonePeTransactionResponse(requestPayload);

            String errorCode = phonePeTransactionResponse.getCode().name();
            String errorMessage = phonePeTransactionResponse.getMessage();

            Boolean validChecksum = validateChecksum(requestPayload);
            if (validChecksum && phonePeTransactionResponse.getCode() != null) {
                this.fetchAndUpdateTransactionFromSource(transaction);
                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY301);
                } else if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                    SessionDTO sessionDTO = SessionContextHolder.getBody();
                    return SUCCESS_PAGE + SessionContextHolder.getId() +
                            SLASH +
                            sessionDTO.<String>get(OS) +
                            QUESTION_MARK +
                            SERVICE +
                            EQUAL +
                            sessionDTO.<String>get(SERVICE) +
                            AND +
                            BUILD_NO +
                            EQUAL +
                            sessionDTO.<Integer>get(BUILD_NO);
                } else {
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302);
                }
            } else {
                log.error(PHONEPE_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with Wynk transactionId: {}, PhonePe transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        transaction.getIdStr(),
                        phonePeTransactionResponse.getData().getProviderReferenceId(),
                        errorCode,
                        errorMessage,
                        transaction.getUid());
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found for transactionId:" + transaction.getIdStr());
            }
        } catch (PaymentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentRuntimeException(PHONEPE_CHARGING_CALLBACK_FAILURE, e.getMessage(), e);
        }
    }

    private Boolean validateChecksum(Map<String, String> requestParams) {
        String checksum = StringUtils.EMPTY;
        boolean validated = false;
        StringBuilder validationString = new StringBuilder();
        try {
            for (String key : requestParams.keySet()) {
                if (!key.equals("checksum") && !key.equals("tid")) {
                    validationString.append(URLDecoder.decode(requestParams.get(key), "UTF-8"));
                } else if (key.equals("checksum")) {
                    checksum = URLDecoder.decode(requestParams.get(key), "UTF-8");
                }
            }
            String calculatedChecksum = DigestUtils.sha256Hex(validationString + salt) + "###1";
            if (StringUtils.equals(checksum, calculatedChecksum)) {
                validated = true;
            }

        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_CALLBACK_FAILURE, "Exception while Checksum validation");
        }
        return validated;
    }

}