package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.Status;
import in.wynk.commons.utils.EncryptionUtils;
import in.wynk.commons.utils.SessionUtils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.PaymentConstants;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.entity.UserPreferredPayment;
import in.wynk.payment.core.entity.Wallet;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.ConsultBalanceRequest;
import in.wynk.payment.dto.request.ConsultBalanceRequest.ConsultBalanceRequestBody;
import in.wynk.payment.dto.request.ConsultBalanceRequest.ConsultBalanceRequestHead;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.request.WalletRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletAddMoneyRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletLinkRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletSendOtpRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletValidateLinkRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.ConsultBalanceResponse;
import in.wynk.payment.dto.response.ValidateTokenResponse;
import in.wynk.payment.dto.response.WalletBalanceResponse;
import in.wynk.payment.dto.response.paytm.PaytmChargingResponse;
import in.wynk.payment.dto.response.paytm.PaytmChargingStatusResponse;
import in.wynk.payment.dto.response.paytm.PaytmWalletLinkResponse;
import in.wynk.payment.dto.response.paytm.PaytmWalletValidateLinkResponse;
import in.wynk.payment.enums.paytm.StatusMode;
import in.wynk.payment.errors.ErrorCodes;
import in.wynk.payment.logging.PaymentLoggingMarkers;
import in.wynk.payment.service.IRenewalMerchantWalletService;
import in.wynk.payment.service.IUserPaymentsManager;
import in.wynk.revenue.util.Utils;
import in.wynk.session.context.SessionContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static in.wynk.commons.constants.Constants.*;
import static in.wynk.commons.enums.Status.SUCCESS;
import static in.wynk.payment.constant.PaymentConstants.WALLET_USER_ID;
import static in.wynk.payment.core.constant.PaymentCode.PAYTM_WALLET;
import static in.wynk.payment.logging.PaymentLoggingMarkers.PAYTM_ERROR;

@Service(BeanConstant.PAYTM_MERCHANT_WALLET_SERVICE)
public class PaytmMerchantWalletPaymentService implements IRenewalMerchantWalletService {

    private static final Logger logger = LoggerFactory.getLogger(PaytmMerchantWalletPaymentService.class);

    private static final Long DEFAULT_ACCESS_TOKEN_EXPIRY = 3600000 * 24 * 10L; // 10 Days

    @Value("${paytm.native.clientId}")
    private String CLIENT_ID;

    @Value("${paytm.native.merchantKey}")
    private String MERCHANT_KEY;

    @Value("${paytm.native.merchantId}")
    private String MID;

    @Value("${paytm.native.secret}")
    private String SECRET;

    @Value("${paytm.native.accounts.baseUrl}")
    private String ACCOUNTS_URL;

    @Value("${paytm.native.services.baseUrl}")
    private String SERVICES_URL;

    @Value("${paytm.native.wcf.addMoneyUrl}")
    private String addMoneyPage;

    @Value("${paytm.native.wcf.callbackUrl}")
    private String callBackUrl;

    @Value("${paytm.requesting.website}")
    private String paytmRequestingWebsite;

    @Value("${redirect.success.page}")
    private String successPage;

    @Value("${redirect.failure.page}")
    private String failurePage;

    @Value("{payment.encryption.key}")
    private String paymentEncryptionKey;
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Gson gson;

    @Autowired
    private IUserPaymentsManager userPaymentsManager;

    private CheckSumServiceHelper checkSumServiceHelper;

    @PostConstruct
    public void init() {
        checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        Map<String, List<String>> params = (Map<String, List<String>>) callbackRequest.getBody();
        List<String> status = params.getOrDefault(PaymentConstants.PAYTM_STATUS, new ArrayList<>());
        if (CollectionUtils.isEmpty(status)
                || !status.get(0).equalsIgnoreCase(PaymentConstants.PAYTM_STATUS_SUCCESS)) {
            logger.error(PaymentLoggingMarkers.APPLICATION_ERROR, "Add money txn at paytm failed");
            throw new RuntimeException("Failed to add money to wallet");
        }
        logger.info("Successfully added money to wallet. Now withdrawing amount");
        return withdrawFromWallet();
    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        //create subscription request
        String sessionId = SessionContextHolder.get().getId().toString();
        String txnId = UUID.randomUUID().toString(); //TODO create transaction and pass

        return withdrawFromWallet();
    }

    private BaseResponse<Void> withdrawFromWallet() {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Double amount = SessionUtils.getDouble(sessionDTO, AMOUNT);
        WalletBalanceResponse walletBalanceResponse = balance().getBody();
        if (!walletBalanceResponse.isFundsSufficient()) {
            throw new WynkRuntimeException(WynkErrorType.UT001);
        }

        String msisdn = SessionUtils.getString(sessionDTO, MSISDN); //get msisdn from session
        String uid = SessionUtils.getString(sessionDTO, UID); // custId fetch fromsession
        if (StringUtils.isBlank(msisdn) || StringUtils.isBlank(uid)) {
            throw new WynkRuntimeException(WynkErrorType.UT001, "Linked Msisdn or UID not found for user");
        }
        String accessToken;
        Optional<UserPreferredPayment> userPreferredPayment = userPaymentsManager.getPaymentDetails(uid, PAYTM_WALLET);
        if (userPreferredPayment.isPresent()) {
            Wallet wallet = (Wallet) userPreferredPayment.get().getOption();
            accessToken = wallet.getAccessToken();
            if (wallet.getTokenValidity() < System.currentTimeMillis()) {
                //throw token expired error
                throw new WynkRuntimeException(WynkErrorType.UT018);
            }
        } else {
            //throw link wallet error
            throw new WynkRuntimeException(WynkErrorType.UT022);
        }
        String deviceId = SessionUtils.getString(sessionDTO, DEVICE_ID); // deviceId fetch from session
        String txnId = Utils.getRandomUUID();

        PaytmChargingResponse paytmChargingResponse = withdraw(uid, txnId, String.valueOf(amount), accessToken, deviceId);
        if (paytmChargingResponse != null && paytmChargingResponse.getStatus().equalsIgnoreCase(PaymentConstants.PAYTM_STATUS_SUCCESS)) {
            String sessionId = SessionContextHolder.get().getId().toString();
            String successUrl = String.format(successPage, sessionId, txnId);
            return BaseResponse.redirectResponse(successUrl);
        }
        return BaseResponse.redirectResponse(failurePage);
    }

    private PaytmChargingResponse withdraw(String uid, String txnId, String amount, String accessToken, String deviceId) {
        try {
            URI uri = new URIBuilder(SERVICES_URL + "/paymentservices/HANDLER_FF/withdrawScw").build();
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put("MID", MID);
            parameters.put("ReqType", "WITHDRAW");
            parameters.put("TxnAmount", String.valueOf(amount));
            parameters.put("AppIP", "capi-host");
            parameters.put("OrderId", txnId);
            parameters.put("Currency", "INR");
            parameters.put("DeviceId", deviceId);
            parameters.put("SSOToken", accessToken);
            parameters.put("PaymentMode", "PPI");
            parameters.put("CustId", uid);
            parameters.put("IndustryType", "Retail");
            parameters.put("Channel", "WEB");
            parameters.put("AuthMode", "USRPWD");

            String checkSum = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters);
            parameters.put("CheckSum", checkSum);
            logger.info("Generated checksum: {} for payload: {}", checkSum, parameters);
            RequestEntity<Map<String, String>> requestEntity = new RequestEntity<>(parameters, HttpMethod.POST, uri);
            logger.info("Paytm wallet charging request: {}", requestEntity);
            ResponseEntity<PaytmChargingResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmChargingResponse.class);
            logger.info("Paytm wallet charging response: {}", responseEntity);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception ex) {
            throw new RuntimeException("Exception Occurred");
        }
    }

    @Override
    public BaseResponse<PaytmChargingResponse> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        //Session not present.
        String uid = null;
        String txnId = null;
        double amount = 0;
        String accessToken = null;
        Optional<UserPreferredPayment> userPreferredPayment = userPaymentsManager.getPaymentDetails(uid, PAYTM_WALLET);
        if (userPreferredPayment.isPresent()) {
            Wallet wallet = (Wallet) userPreferredPayment.get().getOption();
            accessToken = wallet.getAccessToken();
        }
        String deviceId = null;
        PaytmChargingResponse response = withdraw(uid, txnId, String.valueOf(amount), accessToken, deviceId);
        // send subscription request to queue
        return BaseResponse.<PaytmChargingResponse>builder().body(response).build();
    }

    public BaseResponse<ChargingStatusResponse> status(ChargingStatusRequest chargingStatusRequest) {
        if (chargingStatusRequest.getStatusMode().equals(StatusMode.MERCHANT_CHECK)) {
            PaytmChargingStatusResponse paytmChargingStatusResponse = fetchChargingStatusFromPaytm(chargingStatusRequest);
            return new BaseResponse<>(paytmChargingStatusResponse, HttpStatus.OK, null);
        } else if (chargingStatusRequest.getStatusMode().equals(StatusMode.LOCAL_CHECK)) {
            // check with the help of transaction id if the entry exists or not
            ChargingStatusResponse chargingStatusResponse = new ChargingStatusResponse();
            return new BaseResponse<>(chargingStatusResponse, HttpStatus.OK, null);
        }
        return null;
    }

    private PaytmChargingStatusResponse fetchChargingStatusFromPaytm(ChargingStatusRequest chargingStatusRequest) {
        try {
            String orderId = "f052764d-53e1-4c25-9021-f76871f5a329";//fetch from session
            URI uri = new URIBuilder(SERVICES_URL + "/order/status").build();

            TreeMap<String, String> paytmParams = new TreeMap<>();
            paytmParams.put("MID", MID);
            paytmParams.put("ORDERID", orderId);
            String checkSum = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, paytmParams);
            logger.info("Generated checksum: {} for payload: {}", checkSum, paytmParams);
            paytmParams.put("CHECKSUMHASH", checkSum);
            RequestEntity<Map<String, String>> requestEntity = new RequestEntity<>(paytmParams, HttpMethod.POST, uri);

            logger.info("Paytm wallet charging status request: {}", requestEntity);
            ResponseEntity<PaytmChargingStatusResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmChargingStatusResponse.class);

            logger.info("Paytm wallet charging status response: {}", responseEntity);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("HttpStatusCode Exception occurred");
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred");
        }
    }

    @Override
    public BaseResponse<Void> validateLink(WalletRequest request) {

        PaytmWalletValidateLinkRequest paytmWalletValidateLinkRequest = (PaytmWalletValidateLinkRequest) request;
        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/signin/validate/otp").build();
            String authHeader = String.format("Basic %s", Utils.encodeBase64(CLIENT_ID + ":" + SECRET));
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", authHeader);
            RequestEntity<PaytmWalletValidateLinkRequest> requestEntity = new RequestEntity<>(paytmWalletValidateLinkRequest, headers, HttpMethod.POST, uri);
            PaytmWalletValidateLinkResponse paytmWalletValidateLinkResponse = null;

            logger.info("Validate paytm otp request: {}", requestEntity);

            ResponseEntity<PaytmWalletValidateLinkResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletValidateLinkResponse.class);
            paytmWalletValidateLinkResponse = responseEntity.getBody();
            if (paytmWalletValidateLinkResponse != null && paytmWalletValidateLinkResponse.getStatus().equals(SUCCESS)) {
                saveToken(paytmWalletValidateLinkResponse);
                return new BaseResponse<>(null, HttpStatus.OK, null);
            }
        } catch (HttpStatusCodeException e) {
            AnalyticService.update("otpValidated", false);
            logger.error(PAYTM_ERROR, "Error in response: {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(WynkErrorType.UT777, e, e.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error(PAYTM_ERROR, "Error in response: {}", ex.getMessage(), ex);
        }
        throw new WynkRuntimeException(WynkErrorType.UT777);
    }

    private void saveToken(PaytmWalletValidateLinkResponse tokenResponse) {
        if (StringUtils.isBlank(tokenResponse.getAccess_token())) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = SessionUtils.getString(sessionDTO, WALLET_USER_ID);
            String uid = SessionUtils.getString(sessionDTO, UID);
            Wallet wallet = new Wallet.Builder().paymentCode(PAYTM_WALLET).walletUserId(walletUserId)
                    .accessToken(tokenResponse.getAccess_token()).tokenValidity(tokenResponse.getExpires()).build();
            userPaymentsManager.saveWalletToken(uid, wallet);
        }
    }

    @Override
    public <R> BaseResponse<R> unlink(WalletRequest request) {
        // remove entry from DB
        return null;
    }

    @Override
    public BaseResponse<WalletBalanceResponse> balance() {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Double amountInRs = SessionUtils.getDouble(sessionDTO, AMOUNT);
        String uid = SessionUtils.getString(sessionDTO, UID);
        String accessToken;
        Optional<UserPreferredPayment> userPreferredPayment = userPaymentsManager.getPaymentDetails(uid, PAYTM_WALLET);
        if (userPreferredPayment.isPresent()) {
            UserPreferredPayment preferredPayment = userPreferredPayment.get();
            Wallet wallet = (Wallet) preferredPayment.getOption();
            accessToken = wallet.getAccessToken();
            if (!validateAccessToken(uid, sessionDTO, wallet)) {
                WalletBalanceResponse response = WalletBalanceResponse.builder().isLinked(false).build();
                return BaseResponse.<WalletBalanceResponse>builder().body(response).status(HttpStatus.OK).build();
            }
        } else {
            return BaseResponse.<WalletBalanceResponse>builder().body(WalletBalanceResponse.defaultUnlinkResponse())
                    .status(HttpStatus.OK).build();
        }

        try {
            URI uri = new URIBuilder(SERVICES_URL + "/paymentservices/pay/consult").build();
            ConsultBalanceRequestBody body = ConsultBalanceRequestBody.builder().userToken(accessToken)
                    .totalAmount(amountInRs).mid(MID).build();
            String jsonPayload = gson.toJson(body);
            logger.debug("Generating signature for payload: {}", jsonPayload);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
            ConsultBalanceRequestHead head = ConsultBalanceRequestHead.builder().clientId(CLIENT_ID).version("v1")
                    .requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
            ConsultBalanceRequest consultBalanceRequest = ConsultBalanceRequest.builder().head(head).body(body).build();
            RequestEntity<ConsultBalanceRequest> requestEntity = new RequestEntity<>(consultBalanceRequest, HttpMethod.POST, uri);
            logger.info("Paytm wallet balance request: {}", requestEntity);
            ResponseEntity<ConsultBalanceResponse> responseEntity = restTemplate.exchange(requestEntity, ConsultBalanceResponse.class);
            logger.info("Paytm wallet balance response: {}", responseEntity);
            AnalyticService.update("PAYTM_RESPONSE_CODE", responseEntity.getStatusCodeValue());
            ConsultBalanceResponse payTmResponse = responseEntity.getBody();
            //add a null check on body.
            if (payTmResponse != null && payTmResponse.getBody() != null && payTmResponse.getBody().getResultInfo().getResultStatus() == SUCCESS) {
                WalletBalanceResponse response = WalletBalanceResponse.builder().isLinked(true)
                        .deficitBalance(payTmResponse.getBody().getDeficitAmount()).fundsSufficient(payTmResponse.getBody().isFundsSufficient()).build();
                return new BaseResponse<>(response, HttpStatus.OK, null);
            }

        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("Unknown Exception Occurred");
        }
        return new BaseResponse<>(WalletBalanceResponse.defaultUnlinkResponse(), HttpStatus.OK, null);
    }

    private boolean validateAccessToken(String uid, SessionDTO sessionDTO, Wallet wallet) {
        String accessToken = wallet.getAccessToken();
        String msisdn = wallet.getWalletUserId();
        logger.info("Validating access token for msisdn: {}, uid: {} with PayTM", msisdn, uid);
        if (StringUtils.isBlank(accessToken) || wallet.getTokenValidity() < System.currentTimeMillis()) {
            logger.info("Access token is expired or not present for: {}", msisdn);
            return false;
        }
        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/user/details").build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("session_token", accessToken);
            RequestEntity<ValidateTokenResponse> requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);
            logger.info("Validate paytm access token request: {}", requestEntity);
            ResponseEntity<ValidateTokenResponse> responseEntity = restTemplate.exchange(requestEntity, ValidateTokenResponse.class);
            ValidateTokenResponse validateTokenResponse = responseEntity.getBody();
            if (validateTokenResponse != null) {
                //option to capture user's email.
                return validateTokenResponse.getExpires() > System.currentTimeMillis();
            }
        } catch (HttpStatusCodeException e) {
            logger.error(PAYTM_ERROR, "Error from paytm: {} , response: {}", e.getMessage(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("General Exception occurred");
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public BaseResponse<Map<String, String>> addMoney(WalletRequest request) {
        PaytmWalletAddMoneyRequest paytmWalletAddMoneyRequest = (PaytmWalletAddMoneyRequest) request;
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = SessionUtils.getString(sessionDTO, UID);
        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300"; //fetch from session
        String txnId = "76757657667";//get from session
        if (StringUtils.isBlank(accessToken)) {
            logger.info("Access token is expired or not present for: {}", accessToken);
            throw new RuntimeException("Access token is not present");
        }
        String sessionId = SessionContextHolder.get().getId().toString();
        return addMoney(sessionId, uid, accessToken, String.valueOf(paytmWalletAddMoneyRequest.getAmountToCredit()));
    }

    private BaseResponse<Map<String, String>> addMoney(String sid, String uid, String accessToken, String amount) {
        try {
            String wynkCallbackURL = callBackUrl + sid;
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put(PaymentConstants.PAYTM_REQUEST_TYPE, PaymentConstants.ADD_MONEY);
            parameters.put(PaymentConstants.PAYTM_MID, MID);
            parameters.put(PaymentConstants.PAYTM_REQUST_ORDER_ID, sid);
            parameters.put(PaymentConstants.PAYTM_CHANNEL_ID, PaymentConstants.PAYTM_WEB);
            parameters.put(PaymentConstants.PAYTM_INDUSTRY_TYPE_ID, PaymentConstants.RETAIL);
            parameters.put(PaymentConstants.PAYTM_REQUEST_CUST_ID, uid);
            parameters.put(PaymentConstants.PAYTM_REQUEST_TXN_AMOUNT, amount);
            parameters.put(PaymentConstants.PAYTM_REQUESTING_WEBSITE, paytmRequestingWebsite);
            parameters.put(PaymentConstants.PAYTM_SSO_TOKEN, accessToken);
            parameters.put(PaymentConstants.PAYTM_REQUEST_CALLBACK, wynkCallbackURL);
            parameters.put(PaymentConstants.PAYTM_CHECKSUMHASH, checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters));
            String payTmRequestParams = gson.toJson(parameters);
            payTmRequestParams = EncryptionUtils.encrypt(payTmRequestParams, paymentEncryptionKey);
            Map<String, String> params = new HashMap<>();
            params.put(INFO, payTmRequestParams);
            return new BaseResponse<>(params, HttpStatus.OK, null);
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred");
        }
    }

    @Override
    public BaseResponse<PaytmWalletLinkResponse> linkRequest(WalletRequest walletRequest) {
        PaytmWalletLinkRequest paytmWalletLinkRequest = (PaytmWalletLinkRequest) walletRequest;
        String phone = paytmWalletLinkRequest.getEncSi();
        logger.info("Sending OTP to {} via PayTM", phone);
        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/signin/otp").build();

            RequestEntity<PaytmWalletSendOtpRequest> requestEntity =
                    new RequestEntity<>(PaytmWalletSendOtpRequest.Builder.paytmWalletSendOtpRequest()
                            .withEmail(null)
                            .withPhone(phone)
                            .withClientId(CLIENT_ID)
                            .withScope("wallet")
                            .withResponseType("token")
                            .build(), HttpMethod.POST, uri);

            logger.info("Paytm OTP request: {}", requestEntity);

            ResponseEntity<PaytmWalletLinkResponse> responseEntity =
                    restTemplate.exchange(requestEntity, PaytmWalletLinkResponse.class);

            logger.info("Paytm OTP response: {}", responseEntity);

            HttpStatus statusCode = responseEntity.getStatusCode();
            PaytmWalletLinkResponse paytmWalletLinkResponse = responseEntity.getBody();

            if (!statusCode.is2xxSuccessful() || paytmWalletLinkResponse == null) {
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                if (paytmWalletLinkResponse != null) {
                    String responseCode = paytmWalletLinkResponse.getResponseCode();
                    errorCode = ErrorCodes.resolveErrorCode(responseCode);
                }
                logger.error(PaymentLoggingMarkers.HTTP_ERROR, "Error in sending otp. Reason: [{}]",
                        errorCode.getMessage());
            }

            if (paytmWalletLinkResponse.getStatus() == Status.FAILURE) {
                ErrorCodes errorCode;
                String responseCode = paytmWalletLinkResponse.getResponseCode();
                errorCode = ErrorCodes.resolveErrorCode(responseCode);
                logger.error(PaymentLoggingMarkers.HTTP_ERROR, "Error in sending otp. Reason: [{}]",
                        errorCode.getMessage());
                return new BaseResponse<>(paytmWalletLinkResponse, HttpStatus.OK, responseEntity.getHeaders());
            }

            String state = paytmWalletLinkResponse.getState(); // add state in session
            if (StringUtils.isBlank(state)) {
                throw new RuntimeException("Paytm responded with empty state for OTP response");
            }

            logger.info("Otp sent successfully. Status: {}", paytmWalletLinkResponse.getStatus());
            return new BaseResponse(paytmWalletLinkResponse, HttpStatus.OK, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error with URI");
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("General Exception Occurred");
        }
    }
}
