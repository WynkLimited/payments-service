package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.payment.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.request.paytm.PaytmWalletAddMoneyRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletLinkRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletSendOtpRequest;
import in.wynk.payment.dto.request.paytm.PaytmWalletValidateLinkRequest;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.paytm.PaytmChargingResponse;
import in.wynk.payment.dto.response.paytm.PaytmWalletLinkResponse;
import in.wynk.payment.dto.response.paytm.PaytmWalletValidateLinkResponse;
import in.wynk.payment.enums.Status;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.errors.ErrorCodes;
import in.wynk.payment.logging.LoggingMarkers;
import in.wynk.payment.service.IRenewalMerchantWalletService;

import in.wynk.revenue.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import com.google.gson.*;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

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

    private CheckSumServiceHelper checkSumServiceHelper;

    @PostConstruct
    public void init() {
        checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override
    public BaseResponse<PaytmChargingResponse> handleCallback(CallbackRequest callbackRequest) {
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
                couponId(couponId).paymentCode(PaymentCode.PAYTM_WALLET).build();

        return doCharging(chargingRequest);
    }

    @Override
    public BaseResponse doCharging(ChargingRequest chargingRequest) {
        try {
            BaseResponse response = withdrawFromWallet(chargingRequest);
            //create subscription request
            URI successUrl = new URIBuilder(successPage).build();

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(successUrl);

            return new BaseResponse(null, HttpStatus.FOUND, headers);
        } catch(URISyntaxException e) {
            throw new RuntimeException("URI Synatx Exception occurred");
        }
    }

    private BaseResponse<PaytmChargingResponse> withdrawFromWallet(ChargingRequest chargingRequest) {
        BigDecimal withdrawalAmount = new BigDecimal("1.00"); //fetch from session
        ConsultBalanceResponse consultBalanceResponse = (ConsultBalanceResponse)balance().getBody();

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
            return new BaseResponse(response,HttpStatus.OK,null);
        }

        String msisdn = "9149832387"; //get msisdn from session
        if (StringUtils.isBlank(msisdn)) {
            throw new RuntimeException("Linked Msisdn not found for user");
        }

        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300"; // get access token from session
        String uid = "23456"; // custId fetch fromsession
        String deviceId = "tydtd7566"; // deviceId fetch from session
        try {

            URI uri = new URIBuilder(SERVICES_URL + "/HANDLER_FF/withdrawScw").build();

            MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.putIfAbsent("Content-Type", Arrays.asList("application/json"));

            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put("MID", MID);
            parameters.put("ReqType", "WITHDRAW");
            parameters.put("TxnAmount", withdrawalAmount.toString());
            parameters.put("AppIP", "capi-host");
            parameters.put("OrderId", Utils.getRandomUUID());
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
            RequestEntity requestEntity =
                    new RequestEntity<>(parameters, headers, HttpMethod.POST, uri);

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

            return new BaseResponse(paytmChargingResponse, HttpStatus.OK, null) ;
        } catch(URISyntaxException ex) {
            throw new RuntimeException("URISyntax Exception occurred");
        } catch(HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch(Exception ex) {
            throw new RuntimeException("Exception Occurred");
        }
    }
    @Override
    public BaseResponse<PaytmChargingResponse> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        ChargingRequest chargingRequest = ChargingRequest.builder().sessionId(paymentRenewalRequest.getSessionId()).partnerProductId(paymentRenewalRequest.getPartnerProductId()).
                couponId(paymentRenewalRequest.getCouponId()).paymentCode(PaymentCode.PAYTM_WALLET).build();
        BaseResponse response = withdrawFromWallet(chargingRequest);
        // send subscription request to queue
        return response;
    }

    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        // check with the help of transaction id if the entry exists or not
        return null;
    }

    @Override
    public <T> BaseResponse<T> validateLink(WalletRequest request) {

        PaytmWalletValidateLinkRequest paytmWalletValidateLinkRequest = (PaytmWalletValidateLinkRequest)request;
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
        } catch(URISyntaxException e) {
            throw new RuntimeException("URI Exception");
        }catch(HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        }catch(Exception e) {
            throw new RuntimeException("General Exception Occurred");
        }
    }

    @Override
    public <T> BaseResponse<T> unlink(WalletRequest request) {
        // remove entry from DB
        return null;
    }

    @Override
    public BaseResponse<ConsultBalanceResponse> balance() {
        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300"; // fetch access token from session
        ValidateAccessTokenResponse validateAccessTokenResponse = validateAccessToken();
        if (validateAccessTokenResponse.getStatus() != Status.SUCCESS) {
            ConsultBalanceResponse consultBalanceResponse = new ConsultBalanceResponse(Status.FAILURE, null,
                    ErrorCodes.INVALID_TOKEN.getCode(), null,
                    ErrorCodes.INVALID_TOKEN.getMessage(), null);
            return new BaseResponse(consultBalanceResponse, HttpStatus.OK, null);
        }

        BigDecimal amountInRs = new BigDecimal("0.50");

        try {
            URI uri = new URIBuilder(SERVICES_URL + "/pay/consult").build();
            MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.putIfAbsent("session_token", Arrays.asList(accessToken));
            headers.putIfAbsent("Content-Type", Arrays.asList("application/json"));

            Map<String, BigDecimal> amountDetails = new HashMap<>();
            amountDetails.put("others", amountInRs);
            ConsultBalanceRequest.ConsultBalanceRequestBody body = ConsultBalanceRequest.ConsultBalanceRequestBody.builder()
                    .userToken(accessToken)
                    .totalAmount(amountInRs)
                    .mid(MID)
                    .amountDetails(amountDetails)
                    .build();

            String jsonPayload = new GsonBuilder().create().toJson(body);
            logger.info("Generating signature for payload: {}", jsonPayload);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);

            ConsultBalanceRequest.ConsultBalanceRequestHead head = ConsultBalanceRequest.ConsultBalanceRequestHead.builder()
                    .clientId(CLIENT_ID)
                    .requestTimestamp(System.currentTimeMillis() + "")
                    .signature(signature)
                    .version("v1")
                    .channelId("WEB")
                    .build();

            ConsultBalanceRequest consultBalanceRequest =
                    ConsultBalanceRequest.builder()
                            .head(head)
                            .body(body)
                            .build();

            RequestEntity requestEntity =
                    new RequestEntity<>(consultBalanceRequest, headers, HttpMethod.POST, uri);

            logger.info("Paytm wallet balance request: {}", requestEntity);
            ResponseEntity<WalletBalanceResponse> responseEntity =
                    restTemplate.exchange(requestEntity, WalletBalanceResponse.class);
            logger.info("Paytm wallet balance response: {}", responseEntity);

            HttpStatus statusCode = responseEntity.getStatusCode();
            WalletBalanceResponse walletBalanceResponse = responseEntity.getBody();

            if (!statusCode.is2xxSuccessful() || walletBalanceResponse == null) {
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                if (walletBalanceResponse != null) {
                    String responseCode = walletBalanceResponse.getResponseCode();
                    errorCode = ErrorCodes.resolveErrorCode(responseCode);
                }
                logger.error(LoggingMarkers.HTTP_ERROR,
                        "Error in fetching wallet balance. Reason: [{}]",
                        errorCode.getMessage());
                throw new RuntimeException(
                        "Error in fetching wallet balance. Reason: " + errorCode.getMessage());
            }

            if (!walletBalanceResponse.getBody().isFundsSufficient()
                    || walletBalanceResponse.getBody().getResultInfo().getResultStatus()
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
        }catch(URISyntaxException ex) {
            throw new RuntimeException("Exception encountered");
        }catch(HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch(Exception e) {
            throw new RuntimeException("Unknown Exception Occurred");
        }

    }

    private ValidateAccessTokenResponse validateAccessToken() {
        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300";// Fetch access token from session
        String msisdn = "9149832387";// Fetch from session
        String uid = "6787687"; // Fetch from session
        logger.info("Validating access token for msisdn: {}, uid: {}", msisdn,
                uid);

        if (StringUtils.isBlank(accessToken)) {
            logger.info("Access token is expired or not present for: {}", msisdn);
            ValidateAccessTokenResponse validateAccessTokenResponse =
                    ValidateAccessTokenResponse.Builder.validateAccessTokenResponse()
                    .withStatus(Status.FAILURE)
                    .withStatusMessage(null)
                    .withResponseCode(ErrorCodes.INVALID_TOKEN.getCode())
                    .withStatusCode(null)
                    .withMessage(ErrorCodes.INVALID_TOKEN.getMessage())
                    .withId(null)
                    .withEmail(null)
                    .withMobile(null)
                    .withExpires(0)
                    .build();
        }

        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/user/details").build();

            MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.putIfAbsent("session_token", Arrays.asList(accessToken));

            RequestEntity requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);

            logger.info("Validate paytm access token request: {}", requestEntity);
            ResponseEntity<ValidateAccessTokenResponse> responseEntity =
                    restTemplate.exchange(requestEntity, ValidateAccessTokenResponse.class);

            HttpStatus statusCode = responseEntity.getStatusCode();
            ValidateAccessTokenResponse validateAccessTokenResponse = responseEntity.getBody();

            if (!statusCode.is2xxSuccessful() || validateAccessTokenResponse == null) {
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                if (validateAccessTokenResponse != null) {
                    String responseCode = validateAccessTokenResponse.getResponseCode();
                    errorCode = ErrorCodes.resolveErrorCode(responseCode);
                }
                logger.error(LoggingMarkers.HTTP_ERROR,
                        "Error in validating access token. Reason: [{}]",
                        errorCode.getMessage());
                throw new RuntimeException("Error in access token. Reason: " + errorCode.getMessage());
            }

            if (validateAccessTokenResponse.getStatus() != Status.FAILURE) {
                // Paytm doesn't respond with success in its payload. we need to populate it.
                validateAccessTokenResponse.setStatus(Status.SUCCESS);
            }
            return validateAccessTokenResponse;
        } catch(URISyntaxException exception) {
            throw new RuntimeException("Exception throw due to URI");
        } catch(HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch(Exception e) {
            throw new RuntimeException("General Exception occurred");
        }
    }

    @Override
    public BaseResponse addMoney(WalletRequest request) {
        PaytmWalletAddMoneyRequest paytmWalletAddMoneyRequest = (PaytmWalletAddMoneyRequest) request;
        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300"; //fetch from session
        String txnId = "76757657667";//get from session
        if (StringUtils.isBlank(accessToken)) {
            logger.info("Access token is expired or not present for: {}", accessToken);
            throw new RuntimeException("Access token is not present");
        }

        try {
            URI callbackUri =
                    new URIBuilder(callBackUrl).addParameter("tid", txnId).build();

            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put(PaymentConstants.PAYTM_REQUEST_TYPE, PaymentConstants.ADD_MONEY);
            parameters.put(PaymentConstants.PAYTM_MID, MID);
            parameters.put(PaymentConstants.PAYTM_REQUST_ORDER_ID, paytmWalletAddMoneyRequest.getOrderId());
            parameters.put(PaymentConstants.PAYTM_CHANNEL_ID, PaymentConstants.PAYTM_WEB);
            parameters.put(PaymentConstants.PAYTM_INDUSTRY_TYPE_ID, PaymentConstants.RETAIL);
            parameters.put(PaymentConstants.PAYTM_REQUEST_CUST_ID, paytmWalletAddMoneyRequest.getUid());
            parameters.put(PaymentConstants.PAYTM_REQUEST_TXN_AMOUNT, paytmWalletAddMoneyRequest.getAmountToCredit().toString());
            parameters.put(PaymentConstants.PAYTM_REQUESTING_WEBSITE, paytmRequestingWebsite);
            parameters.put(PaymentConstants.PAYTM_SSO_TOKEN, accessToken);
            parameters.put(PaymentConstants.PAYTM_REQUEST_CALLBACK, callbackUri.toString());
            parameters.put(PaymentConstants.PAYTM_CHECKSUMHASH,
                    checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters));

            JsonObject json = new GsonBuilder().create().toJsonTree(parameters).getAsJsonObject();

            URI returnUrl = new URIBuilder(addMoneyPage).addParameter("payTMSubscriptionRequest",
                    json.toString()).build();
            List<NameValuePair> params = URLEncodedUtils.parse(returnUrl, "UTF-8");

            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(returnUrl);

            logger.info("Redirecting user, uid: {}, return URL: {} params: {}", paytmWalletAddMoneyRequest.getUid(),
                    returnUrl.getHost(), params);

            return new BaseResponse(null, HttpStatus.FOUND, headers);
        } catch(URISyntaxException e) {
            throw new RuntimeException("URISyntax Exception");
        } catch(HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        }catch(Exception e) {
            throw new RuntimeException("Exception occurred");
        }
    }

    @Override
    public BaseResponse<PaytmWalletLinkResponse> linkRequest(WalletRequest walletRequest) {
        PaytmWalletLinkRequest paytmWalletLinkRequest = (PaytmWalletLinkRequest)walletRequest;
        String phone =  paytmWalletLinkRequest.getEncSi();
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
                ErrorCodes errorCode = ErrorCodes.UNKNOWN;
                String responseCode = paytmWalletLinkResponse.getResponseCode();
                errorCode = ErrorCodes.resolveErrorCode(responseCode);
                logger.error(LoggingMarkers.HTTP_ERROR, "Error in sending otp. Reason: [{}]",
                        errorCode.getMessage());
                return new BaseResponse(paytmWalletLinkResponse, HttpStatus.OK,  responseEntity.getHeaders());
            }

            String state = paytmWalletLinkResponse.getState(); // add state in session
            if (StringUtils.isBlank(state)) {
                throw new RuntimeException("Paytm responded with empty state for OTP response");
            }

            logger.info("Otp sent successfully. Status: {}", paytmWalletLinkResponse.getStatus());
            return new BaseResponse(paytmWalletLinkResponse, HttpStatus.OK, null);
        } catch(URISyntaxException e) {
            throw new RuntimeException("Error with URI");
        } catch(HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch(Exception e) {
            throw new RuntimeException("General Exception Occurred");
        }
    }
}
