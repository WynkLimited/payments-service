package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.Status;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.ErrorCode;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.apb.paytm.APBPaytmLinkRequest;
import in.wynk.payment.dto.paytm.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.Apb.paytm.APBPaytmLinkResponse;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.paytm.*;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;
import static in.wynk.logging.BaseLoggingMarkers.HTTP_ERROR;
import static in.wynk.payment.core.constant.PaymentCode.PAYTM_WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET_USER_ID;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY889;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYTM_ERROR;
import static in.wynk.payment.dto.apb.paytm.APBPaytmConstants.ABP_PAYTM_OTP_TOKEN;
import static in.wynk.payment.dto.paytm.PayTmConstants.*;
import static in.wynk.payment.dto.paytm.PayTmConstants.PAYTM_CHECKSUMHASH;

@Slf4j
@Service(BeanConstant.APB_PAYTM_MERCHANT_WALLET_SERVICE)
public class APBPaytmMerchantWalletPaymentService extends AbstractMerchantPaymentStatusService implements IRenewalMerchantWalletService, IUserPreferredPaymentService, IMerchantPaymentRefundService {

    private String SEND_OTP="http://kongqa.airtel.com/preprod-236/pg-service/v1/wallet/initiate-link";
    private String VERIFY_OTP="http://kongqa.airtel.com/preprod-236/pg-service/v1/wallet/initiate-link";
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final IUserPaymentsManager userPaymentsManager;
    private final CheckSumServiceHelper checkSumServiceHelper;
    private final PaymentCachingService paymentCachingService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public APBPaytmMerchantWalletPaymentService(ObjectMapper objectMapper, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, IUserPaymentsManager userPaymentsManager, PaymentCachingService paymentCachingService, ApplicationEventPublisher applicationEventPublisher) {
        super(paymentCachingService);
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.userPaymentsManager = userPaymentsManager;
        this.paymentCachingService = paymentCachingService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override
    public BaseResponse<?> linkRequest(WalletLinkRequest walletLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            APBPaytmLinkRequest linkRequest = APBPaytmLinkRequest.builder().walletLoginId(walletLinkRequest.getEncSi()).
                    loginId(walletLinkRequest.getEncSi()).wallet("PAYTM").authType("AUTH").build();
            Map<String, String> map = new HashMap<>();
            map.put("walletLoginId", walletLinkRequest.getEncSi());
            map.put("loginId", walletLinkRequest.getEncSi());
            map.put("wallet", "PAYTM");
            map.put("authType", "AUTH");
            HttpHeaders headers = new HttpHeaders();
            headers.add("authorization", "Basic cGF5bWVudDpwYXlAcWNrc2x2cg==");
            headers.add("channel-id", "WEB_MOBILE_UNAUTH");
            headers.add("iv-user", walletLinkRequest.getEncSi());
            headers.add("content-type", "application/json");
            headers.add("accept", "application/json");
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(map, headers);
            ResponseEntity<APBPaytmLinkResponse> linkResponse = restTemplate.postForEntity(SEND_OTP, requestEntity, APBPaytmLinkResponse.class);
            APBPaytmLinkResponse response = linkResponse.getBody();
            if (response.isResult()) {
                Session<SessionDTO> session = SessionContextHolder.get();
                SessionDTO sessionDTO = session.getBody();
                sessionDTO.put(ABP_PAYTM_OTP_TOKEN, response.getData().getOtpToken());
                log.info("otp send successfully {} ", response.getData().getOtpToken());

            } else {
//TODO:  Need to take errorCodes list from APB and put all in  ErrorCode  enum to provide ErrorCodes to FE
                errorCode = ErrorCode.getErrorCodesFromExternalCode(response.getCode());
            }
        }
        catch (HttpStatusCodeException hex) {
            log.error("APB_PAYTM_OTP_SEND_FAILURE", hex.getResponseBodyAsString());
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmLinkResponse.class).getCode());
        } catch (Exception e) {
            log.error("APB_PAYTM_OTP_SEND_FAILURE", e.getMessage());
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

            Map<String, String> map = new HashMap<>();
 //           map.put("walletLoginId", sessionDTO.get());
//            map.put("loginId", request.getEncSi());
            map.put("wallet", "PAYTM");
            map.put("authType", "AUTH");
            HttpHeaders headers = new HttpHeaders();
            headers.add("authorization", "Basic cGF5bWVudDpwYXlAcWNrc2x2cg==");
            headers.add("channel-id", "WEB_MOBILE_UNAUTH");
 //           headers.add("iv-user", walletLinkRequest.getEncSi());
            headers.add("content-type", "application/json");
            headers.add("accept", "application/json");
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(map, headers);
            ResponseEntity<APBPaytmLinkResponse> linkResponse = restTemplate.postForEntity(SEND_OTP, requestEntity, APBPaytmLinkResponse.class);
            APBPaytmLinkResponse response = linkResponse.getBody();
            if (response.isResult()) {
 //               Session<SessionDTO> session = SessionContextHolder.get();
 //               SessionDTO sessionDTO = session.getBody();
                sessionDTO.put(ABP_PAYTM_OTP_TOKEN, response.getData().getOtpToken());
                log.info("otp send successfully {} ", response.getData().getOtpToken());

            } else {
//TODO:  Need to take errorCodes list from APB and put all in  ErrorCode  enum to provide ErrorCodes to FE
                errorCode = ErrorCode.getErrorCodesFromExternalCode(response.getCode());
            }
        }
        catch (HttpStatusCodeException hex) {
        log.error("APB_PAYTM_OTP_SEND_FAILURE", hex.getResponseBodyAsString());
        errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(hex.getResponseBodyAsString(), APBPaytmLinkResponse.class).getCode());
    } catch (Exception e) {
        log.error("APB_PAYTM_OTP_SEND_FAILURE", e.getMessage());
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
    public BaseResponse<?> unlink() {
        return null;
    }

    @Override
    public BaseResponse<?> balance(int planId) {
        return null;
    }

    @Override
    public BaseResponse<?> addMoney(WalletAddMoneyRequest request) {
        return null;
    }


    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        return null;
    }

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        return null;
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        return null;
    }

    @Override
    public BaseResponse<?> refund(AbstractPaymentRefundRequest request) {
        return null;
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {

    }

    @Override
    public WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> getUserPreferredPayments(Key key, int planId) {
        return null;
    }
}