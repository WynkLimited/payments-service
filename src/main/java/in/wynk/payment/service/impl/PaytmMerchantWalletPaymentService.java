package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.Status;
import in.wynk.commons.utils.SessionUtils;
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
import in.wynk.payment.logging.LoggingMarkers;
import in.wynk.payment.service.IRenewalMerchantWalletService;
import in.wynk.payment.service.IUserPaymentsManager;
import in.wynk.revenue.util.Utils;
import in.wynk.session.context.SessionContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static in.wynk.commons.constants.Constants.AMOUNT;
import static in.wynk.commons.constants.Constants.UID;
import static in.wynk.payment.core.constant.PaymentCode.PAYTM_WALLET;

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
        String sessionId, partnerProductId, couponId;
        List<String> status = params.getOrDefault(PaymentConstants.PAYTM_STATUS, new ArrayList<>());
        if (CollectionUtils.isEmpty(status)
                || !status.get(0).equalsIgnoreCase(PaymentConstants.PAYTM_STATUS_SUCCESS)) {
            logger.error(LoggingMarkers.APPLICATION_ERROR, "Add money txn at paytm failed");
            throw new RuntimeException("Failed to add money to wallet");
        }
        logger.info("Successfully added money to wallet. Now withdrawing amount");

        sessionId = "sdbfd";//fetch from session
        partnerProductId = "hytf698"; //fetch from session
        couponId = "it87t"; //fetch from session
        ChargingRequest chargingRequest = ChargingRequest.builder().sessionId(sessionId).partnerProductId(partnerProductId).
                couponId(couponId).paymentCode(PAYTM_WALLET).build();

        return doCharging(chargingRequest);
    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        try {
            withdrawFromWallet(chargingRequest);
            //create subscription request
            URI successUrl = new URIBuilder(successPage).build();

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(successUrl);

            return new BaseResponse<>(null, HttpStatus.FOUND, headers);
        } catch (URISyntaxException e) {
            throw new RuntimeException("URI Synatx Exception occurred");
        }
    }

    private BaseResponse<JsonObject> withdrawFromWallet(ChargingRequest chargingRequest) {
        BigDecimal withdrawalAmount = new BigDecimal("1.00"); //fetch from session
        ConsultBalanceResponse consultBalanceResponse = balance().getBody();

        if (consultBalanceResponse.getStatus() != Status.SUCCESS) {
            String responseCode = consultBalanceResponse.getResponseCode();
            ErrorCodes errorCode = ErrorCodes.resolveErrorCode(responseCode);
            JsonObject response = new JsonObject();
            if (errorCode == ErrorCodes.INSUFFICIENT_BALANCE) {
                response.addProperty("amountRequired",
                        consultBalanceResponse.getDeficitAmount() != null ?
                                consultBalanceResponse.getDeficitAmount().toString() : null);
            }
            response.addProperty("success", false);
            response.addProperty("message", errorCode.getMessage());
            response.addProperty("errorCode", errorCode.getCode());
            return new BaseResponse<>(response, HttpStatus.OK, null);
        }

        String msisdn = "9149832387"; //get msisdn from session
        if (StringUtils.isBlank(msisdn)) {
            throw new RuntimeException("Linked Msisdn not found for user");
        }

        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300"; // get access token from session
        String uid = "98998"; // custId fetch fromsession
        String deviceId = "9149832387"; // deviceId fetch from session
        String orderId = Utils.getRandomUUID();
        try {

            URI uri = new URIBuilder(SERVICES_URL + "/paymentservices/HANDLER_FF/withdrawScw").build();

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put("MID", MID);
            parameters.put("ReqType", "WITHDRAW");
            parameters.put("TxnAmount", withdrawalAmount.toString());
            parameters.put("AppIP", "capi-host");
            parameters.put("OrderId", orderId);
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
            RequestEntity<Map<String, String>> requestEntity = new RequestEntity<>(parameters, headers, HttpMethod.POST, uri);

            logger.info("Paytm wallet charging request: {}", requestEntity);
            ResponseEntity<PaytmChargingResponse> responseEntity =
                    restTemplate.exchange(requestEntity, PaytmChargingResponse.class);
            logger.info("Paytm wallet charging response: {}", responseEntity);

            HttpStatus statusCode = responseEntity.getStatusCode();
            PaytmChargingResponse paytmChargingResponse = responseEntity.getBody();

            if (!statusCode.is2xxSuccessful() || paytmChargingResponse == null) {
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                if (paytmChargingResponse != null) {
                    String responseCode = paytmChargingResponse.getResponseCode();
                    errorCode = ErrorCodes.resolveErrorCode(responseCode);
                }
                logger.error(LoggingMarkers.HTTP_ERROR,
                        "Error in charging amount. Reason: [{}]",
                        errorCode.getMessage());
                throw new RuntimeException("Error in charging amount. Reason: " +
                        errorCode.getMessage());
            }

            if (paytmChargingResponse.getStatus().equalsIgnoreCase(PaymentConstants.PAYTM_STATUS_SUCCESS)) {
                paytmChargingResponse.setResponseCode("100");
                logger.info("Charging Successful");
            }

            return new BaseResponse(paytmChargingResponse, HttpStatus.OK, null);
        } catch (URISyntaxException ex) {
            throw new RuntimeException("URISyntax Exception occurred");
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception ex) {
            throw new RuntimeException("Exception Occurred");
        }
    }

    @Override
    public BaseResponse<PaytmChargingResponse> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        ChargingRequest chargingRequest = ChargingRequest.builder().sessionId(paymentRenewalRequest.getSessionId()).partnerProductId(paymentRenewalRequest.getPartnerProductId()).
                couponId(paymentRenewalRequest.getCouponId()).paymentCode(PAYTM_WALLET).build();
        BaseResponse response = withdrawFromWallet(chargingRequest);
        // send subscription request to queue
        return response;
    }

    public BaseResponse<ChargingStatusResponse> status(ChargingStatusRequest chargingStatusRequest) {
        if (chargingStatusRequest.getStatusMode().equals(StatusMode.MERCHANT_CHECK)) {
            PaytmChargingStatusResponse paytmChargingStatusResponse = fetchChargingStatusFromPaytm(chargingStatusRequest);
            return new BaseResponse(paytmChargingStatusResponse, HttpStatus.OK, null);
        } else if (chargingStatusRequest.getStatusMode().equals(StatusMode.LOCAL_CHECK)) {
            // check with the help of transaction id if the entry exists or not
            ChargingStatusResponse chargingStatusResponse = new ChargingStatusResponse();
            return new BaseResponse(chargingStatusResponse, HttpStatus.OK, null);
        }
        return null;
    }

    private PaytmChargingStatusResponse fetchChargingStatusFromPaytm(ChargingStatusRequest chargingStatusRequest) {
        try{
            String orderId = "f052764d-53e1-4c25-9021-f76871f5a329";//fetch from session
            URI uri = new URIBuilder(SERVICES_URL + "/order/status").build();

            MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.putIfAbsent("Content-Type", Arrays.asList("application/json"));
            TreeMap<String, String> paytmParams = new TreeMap<String, String>();

            paytmParams.put("MID", MID);
            paytmParams.put("ORDERID", orderId);

            String checkSum = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, paytmParams);
            logger.info("Generated checksum: {} for payload: {}", checkSum, paytmParams);

            paytmParams.put("CHECKSUMHASH", checkSum);

            RequestEntity requestEntity = new RequestEntity<>(paytmParams, headers, HttpMethod.POST, uri);

            logger.info("Paytm wallet charging status request: {}", requestEntity);
            ResponseEntity<PaytmChargingStatusResponse> responseEntity =
                    restTemplate.exchange(requestEntity, PaytmChargingStatusResponse.class);

            logger.info("Paytm wallet charging status response: {}", responseEntity);

            HttpStatus statusCode = responseEntity.getStatusCode();
            PaytmChargingStatusResponse paytmChargingStatusResponse = responseEntity.getBody();

            return paytmChargingStatusResponse;
        } catch (URISyntaxException e) {
            throw new RuntimeException("URISynatx Exception occurred");
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("HttpStatusCode Exception occurred");
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred");
        }
    }

    @Override
    public BaseResponse<PaytmWalletValidateLinkResponse> validateLink(WalletRequest request) {

        PaytmWalletValidateLinkRequest paytmWalletValidateLinkRequest = (PaytmWalletValidateLinkRequest) request;
        logger.info("Validating OTP: {} for: {}", paytmWalletValidateLinkRequest.getOtp(), paytmWalletValidateLinkRequest.getMsisdn());

        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/signin/validate/otp").build();
            String authHeader =
                    String.format("Basic %s", Utils.encodeBase64(CLIENT_ID + ":" + SECRET));

            MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.putIfAbsent("Authorization", Arrays.asList(authHeader));
            headers.putIfAbsent("Content-Type", Arrays.asList("application/json"));

            RequestEntity<PaytmWalletValidateLinkRequest> requestEntity =
                    new RequestEntity<>(paytmWalletValidateLinkRequest, headers, HttpMethod.POST, uri);
            ResponseEntity<PaytmWalletValidateLinkResponse> responseEntity = null;
            HttpStatus statusCode = null;
            PaytmWalletValidateLinkResponse paytmWalletValidateLinkResponse = null;

            logger.info("Validate paytm otp request: {}", requestEntity);
            try {
                responseEntity = restTemplate.exchange(requestEntity, PaytmWalletValidateLinkResponse.class);
                statusCode = responseEntity.getStatusCode();
                paytmWalletValidateLinkResponse = responseEntity.getBody();
            } catch (Exception e) {
                AnalyticService.update("otpValidated", false);
                logger.error(LoggingMarkers.HTTP_ERROR, "Error in response: {}", responseEntity);
                paytmWalletValidateLinkResponse = PaytmWalletValidateLinkResponse.Builder.walletLinkResponse()
                        .withStatus(Status.FAILURE)
                        .withStatusMessage(null)
                        .withResponseCode(ErrorCodes.GENERIC_ERROR_03.getCode())
                        .withStatusCode(null)
                        .withMessage(ErrorCodes.GENERIC_ERROR_03.getMessage())
                        .withAccess_token(null)
                        .withExpires(0)
                        .withScope(null)
                        .withResourceOwnerId(null)
                        .build();
                return new BaseResponse(paytmWalletValidateLinkResponse, HttpStatus.OK, null);
            }
            logger.info("Validate paytm otp response: {}", responseEntity);

            if (!statusCode.is2xxSuccessful() || paytmWalletValidateLinkResponse == null) {
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                if (paytmWalletValidateLinkResponse != null) {
                    String responseCode = paytmWalletValidateLinkResponse.getResponseCode();
                    errorCode = ErrorCodes.resolveErrorCode(responseCode);
                }
                AnalyticService.update("otpValidated", false);
                logger.error(LoggingMarkers.HTTP_ERROR, "Error in validating otp. Reason: [{}]",
                        errorCode.getMessage());
                throw new RuntimeException("Error in validating otp. Reason: " + errorCode.getMessage());
            }

            String accessToken = paytmWalletValidateLinkResponse.getAccess_token();
            long expiry = DEFAULT_ACCESS_TOKEN_EXPIRY;

            AnalyticService.update("accessToken", accessToken);
            if (StringUtils.isBlank(accessToken)) {
                logger.error(LoggingMarkers.HTTP_ERROR, "Invalid Paytm response. Reason: [{}]",
                        "Access token received is empty");
                paytmWalletValidateLinkResponse = PaytmWalletValidateLinkResponse.Builder.walletLinkResponse()
                        .withStatus(Status.FAILURE)
                        .withStatusMessage(null)
                        .withResponseCode(ErrorCodes.INVALID_AUTH.getCode())
                        .withStatusCode(null)
                        .withMessage(ErrorCodes.INVALID_AUTH.getMessage())
                        .withAccess_token(null)
                        .withExpires(0)
                        .withScope(null)
                        .withResourceOwnerId(null)
                        .build();
            }
            // save access token corresponding to uid
            logger.info("Saving paytm access token to db: {}", accessToken);
            paytmWalletValidateLinkResponse.setStatus(Status.SUCCESS);
            return new BaseResponse(paytmWalletValidateLinkResponse, HttpStatus.OK, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException("URI Exception");
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("General Exception Occurred");
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
        String accessToken = StringUtils.EMPTY;
        Optional<UserPreferredPayment> userPreferredPayment = userPaymentsManager.getPaymentDetails(uid, PAYTM_WALLET);
        if (userPreferredPayment.isPresent()) {
            UserPreferredPayment preferredPayment = userPreferredPayment.get();
            Wallet wallet = (Wallet) preferredPayment.getOption();
            accessToken = wallet.getAccessToken();
            if (!validateAccessToken(uid, sessionDTO, wallet)) {
                WalletBalanceResponse response = WalletBalanceResponse.builder().isLinked(false).build();
                return BaseResponse.<WalletBalanceResponse>builder().body(response).status(HttpStatus.OK).build();
            }
        }

        try {
            URI uri = new URIBuilder(SERVICES_URL + "/paymentservices/pay/consult").build();
            ConsultBalanceRequestBody body = ConsultBalanceRequestBody.builder().userToken(accessToken)
                    .totalAmount(amountInRs).mid(MID).build();

            String jsonPayload = gson.toJson(body);
            logger.info("Generating signature for payload: {}", jsonPayload);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);

            ConsultBalanceRequestHead head = ConsultBalanceRequestHead.builder().clientId(CLIENT_ID).version("v1")
                    .requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();

            ConsultBalanceRequest consultBalanceRequest = ConsultBalanceRequest.builder().head(head).body(body).build();

            RequestEntity<ConsultBalanceRequest> requestEntity = new RequestEntity<>(consultBalanceRequest, HttpMethod.POST, uri);

            logger.info("Paytm wallet balance request: {}", requestEntity);
            ResponseEntity<ConsultBalanceResponse> responseEntity = restTemplate.exchange(requestEntity, ConsultBalanceResponse.class);
            logger.info("Paytm wallet balance response: {}", responseEntity);

            HttpStatus statusCode = responseEntity.getStatusCode();
            ConsultBalanceResponse payTmResponse = responseEntity.getBody();

            if (!statusCode.is2xxSuccessful() || payTmResponse == null) {
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                if (payTmResponse != null) {
                    String responseCode = payTmResponse.getResponseCode();
                    errorCode = ErrorCodes.resolveErrorCode(responseCode);
                }
                logger.error(LoggingMarkers.HTTP_ERROR,
                        "Error in fetching wallet balance. Reason: [{}]",
                        errorCode.getMessage());
                throw new RuntimeException(
                        "Error in fetching wallet balance. Reason: " + errorCode.getMessage());
            }

            if (!payTmResponse.isFundsSufficient()
                    || payTmResponse.getBody().getResultInfo().getResultStatus()
                    == Status.FAILURE) {

                BigDecimal deficitAmount = walletBalanceResponse.getBody().getDeficitAmount();
                String resultMessage = walletBalanceResponse.getBody().getResultInfo().getResultMsg();
                String resultCode = walletBalanceResponse.getBody().getResultInfo().getResultCode();
                ErrorCodes errorCode = ErrorCodes.INSUFFICIENT_BALANCE;

                if (!walletBalanceResponse.getBody().isAddMoneyAllowed()) {
                    errorCode = ErrorCodes.USER_NOT_FOUND;
                    //userDAO.expireThirdPartyAccessTokenHash(txnLog.getUid());
                }

                ConsultBalanceResponse consultBalanceResponse = new ConsultBalanceResponse(Status.FAILURE, resultMessage, errorCode.getCode(),
                        resultCode, errorCode.getMessage(), deficitAmount);
                return new BaseResponse(consultBalanceResponse, HttpStatus.OK, null);
            }

            ConsultBalanceResponse consultBalanceResponse = new ConsultBalanceResponse(Status.SUCCESS,
                    walletBalanceResponse.getStatusMessage(),
                    null, walletBalanceResponse.getStatusCode(), null, null);

            return new BaseResponse(consultBalanceResponse, HttpStatus.OK, null);
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Exception encountered");
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("Unknown Exception Occurred");
        }

    }

    private boolean validateAccessToken(String uid, SessionDTO sessionDTO, Wallet wallet) {
        String accessToken = wallet.getAccessToken();
        String msisdn = wallet.getWalletUserId();// Fetch from session
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

            HttpStatus statusCode = responseEntity.getStatusCode();
            ValidateTokenResponse validateTokenResponse = responseEntity.getBody();

            if (!statusCode.is2xxSuccessful() || validateTokenResponse == null) {
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                if (validateTokenResponse != null) {
                    String responseCode = validateTokenResponse.getResponseCode();
                    errorCode = ErrorCodes.resolveErrorCode(responseCode);
                }
                logger.error(LoggingMarkers.HTTP_ERROR,
                        "Error in validating access token. Reason: [{}]",
                        errorCode.getMessage());
                throw new RuntimeException("Error in access token. Reason: " + errorCode.getMessage());
            }

            if (validateTokenResponse.getStatus() != Status.FAILURE) {
                // Paytm doesn't respond with success in its payload. we need to populate it.
                validateTokenResponse.setStatus(Status.SUCCESS);
            }
            return validateTokenResponse;
        } catch (URISyntaxException exception) {
            throw new RuntimeException("Exception throw due to URI");
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("General Exception occurred");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public BaseResponse<Void> addMoney(WalletRequest request) {
        PaytmWalletAddMoneyRequest paytmWalletAddMoneyRequest = (PaytmWalletAddMoneyRequest) request;
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = SessionUtils.getString(sessionDTO, UID);
        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300"; //fetch from session
        String txnId = "76757657667";//get from session
        if (StringUtils.isBlank(accessToken)) {
            logger.info("Access token is expired or not present for: {}", accessToken);
            throw new RuntimeException("Access token is not present");
        }

        try {
            String wynkCallbackURL = callBackUrl + txnId;

            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put(PaymentConstants.PAYTM_REQUEST_TYPE, PaymentConstants.ADD_MONEY);
            parameters.put(PaymentConstants.PAYTM_MID, MID);
            parameters.put(PaymentConstants.PAYTM_REQUST_ORDER_ID, txnId);
            parameters.put(PaymentConstants.PAYTM_CHANNEL_ID, PaymentConstants.PAYTM_WEB);
            parameters.put(PaymentConstants.PAYTM_INDUSTRY_TYPE_ID, PaymentConstants.RETAIL);
            parameters.put(PaymentConstants.PAYTM_REQUEST_CUST_ID, uid);
            parameters.put(PaymentConstants.PAYTM_REQUEST_TXN_AMOUNT, String.valueOf(paytmWalletAddMoneyRequest.getAmountToCredit()));
            parameters.put(PaymentConstants.PAYTM_REQUESTING_WEBSITE, paytmRequestingWebsite);
            parameters.put(PaymentConstants.PAYTM_SSO_TOKEN, accessToken);
            parameters.put(PaymentConstants.PAYTM_REQUEST_CALLBACK, wynkCallbackURL);
            parameters.put(PaymentConstants.PAYTM_CHECKSUMHASH, checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters));

            JsonObject json = gson.toJsonTree(parameters).getAsJsonObject();

            URI returnUrl = new URIBuilder(addMoneyPage).addParameter("payTMSubscriptionRequest", json.toString()).build();
            List<NameValuePair> params = URLEncodedUtils.parse(returnUrl, StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(returnUrl);

            logger.info("Redirecting user, uid: {}, return URL: {} params: {}", uid, returnUrl.getHost(), params);

            return new BaseResponse(null, HttpStatus.FOUND, headers);
        } catch (URISyntaxException e) {
            throw new RuntimeException("URISyntax Exception");
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
                logger.error(LoggingMarkers.HTTP_ERROR, "Error in sending otp. Reason: [{}]",
                        errorCode.getMessage());
            }

            if (paytmWalletLinkResponse.getStatus() == Status.FAILURE) {
                ErrorCodes errorCode;
                String responseCode = paytmWalletLinkResponse.getResponseCode();
                errorCode = ErrorCodes.resolveErrorCode(responseCode);
                logger.error(LoggingMarkers.HTTP_ERROR, "Error in sending otp. Reason: [{}]",
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
