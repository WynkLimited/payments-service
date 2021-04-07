package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.dto.paytm.WalletAddMoneyRequest;
import in.wynk.payment.dto.paytm.WalletLinkRequest;
import in.wynk.payment.dto.paytm.WalletValidateLinkRequest;
import in.wynk.payment.dto.phonepe.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.phonepe.PhonePeResponseData;
import in.wynk.payment.dto.response.phonepe.PhonePeWalletResponse;
import in.wynk.payment.service.IMerchantWalletService;
import in.wynk.payment.service.IUserPaymentsManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.DEVICE_ID;
import static in.wynk.common.constant.BaseConstants.UID;
import static in.wynk.payment.core.constant.PaymentCode.PHONEPE_WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.paytm.PayTmConstants.WALLET_USER_ID;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;
@Slf4j
@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_AUTO_DEBIT_SERVICE)

public class PhonePeWalletAutoDebitService implements IMerchantWalletService {


    private static final String DEBIT_API = "/v3/wallet/debit";
    private static final String TRIGGER_OTP_API = "/v3/merchant/otp/send";
    private static final String VERIFY_OTP_API = "/v3/merchant/otp/verify";
    private static final String UNLINK_API = "/v3/merchant/token/unlink";
    private static final String BALANCE_API = "/v3/wallet/balance";
    private static final String TOPUP_API="/v3/wallet/topup";
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
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            userPaymentsManager.deletePaymentDetails(uid, PHONEPE_WALLET);
            return new BaseResponse<>(response2,HttpStatus.OK,null);

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_UNLINK_FAILURE,hex.getResponseBodyAsString());
            String errorResponse=hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return new BaseResponse<>(response2,HttpStatus.OK,null);

        } catch (Exception e) {
            log.error(PHONEPE_UNLINK_FAILURE, e.getMessage(),e);
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
       return new BaseResponse<>(addMoney(userAuthToken,request.getTxnAmount(),request.getPhonePeVersionCode()),HttpStatus.OK,null);
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
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
           log.error(PHONEPE_OTP_SEND_FAILURE,hex.getResponseBodyAsString());
           String errorResponse=hex.getResponseBodyAsString();
           PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
           PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
           PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;
        } catch (Exception e) {
            log.error(PHONEPE_OTP_SEND_FAILURE, e.getMessage(),e);
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
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE,hex.getResponseBodyAsString());
            String errorResponse=hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;
        } catch (Exception e) {
            log.error(PHONEPE_OTP_VERIFICATION_FAILURE, e.getMessage(),e);
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
            PhonePeResponseData data=PhonePeResponseData.builder().wallet(response1.getData().getWallet()).linkedUser(response1.getData().getLinkedUser()).build();
            PhonePeWalletResponse response2 =PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_GET_BALANCE_FAILURE,hex.getResponseBodyAsString());
            String errorResponse=hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (Exception e) {
            log.error(PHONEPE_GET_BALANCE_FAILURE, e.getMessage(),e);
            throw new WynkRuntimeException(PHONEPE_GET_BALANCE_FAILURE, e.getMessage(), e);
        }
    }


    private PhonePeWalletResponse addMoney(String userAuthToken,long txnAmount, long phonePeVersionCode) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String deviceId = sessionDTO.get(DEVICE_ID);
        PhonePeAutoDebitTopupRequest phonePePaymentRequest = PhonePeAutoDebitTopupRequest.builder()
                .merchantId(merchantId).userAuthToken(userAuthToken)
                .amount(txnAmount).adjustAmount(true).linkType("WALLET_TOPUP_DEEPLINK")
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
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).redirectUrl(response1.getData().getRedirectUrl()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_ADD_MONEY_FAILURE,hex.getResponseBodyAsString());
            String errorResponse=hex.getResponseBodyAsString();
            PhonePeWalletResponse response1 = gson.fromJson(errorResponse, PhonePeWalletResponse.class);
            PhonePeResponseData data=PhonePeResponseData.builder().message(response1.getMessage()).build();
            PhonePeWalletResponse response2 = PhonePeWalletResponse.builder().success(response1.isSuccess()).data(data).build();
            return response2;

        } catch (Exception e) {
            log.error(PHONEPE_ADD_MONEY_FAILURE, e.getMessage(),e);
            throw new WynkRuntimeException(PHONEPE_GET_BALANCE_FAILURE, e.getMessage(), e);
        }
    }


    private String debit(String userAuthToken, long txnAmount,long phonePeVersionCode) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String deviceId = sessionDTO.get(DEVICE_ID);
        PhonePeAutoDebitTopupRequest phonePePaymentRequest = PhonePeAutoDebitTopupRequest.builder().merchantId(merchantId).userAuthToken(userAuthToken).amount(txnAmount).deviceContext(new DeviceContext(phonePeVersionCode)).build();
        try {
            String requestJson = gson.toJson(phonePePaymentRequest);
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
            if (response.getBody() != null) {
                PhonePeWalletResponse response1 = response.getBody();
                return response1.toString();
            }
            else {
                throw new WynkRuntimeException(PaymentErrorType.PAY008);
            }
        } catch (HttpStatusCodeException hex) {

            throw hex;

        } catch (Exception e) {
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
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
}
