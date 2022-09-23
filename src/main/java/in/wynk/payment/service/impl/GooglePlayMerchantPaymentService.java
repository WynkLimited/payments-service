package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.lock.WynkRedisLockService;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.GooglePlayReceiptDetails;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.GooglePlayCallbackRequest;

import in.wynk.payment.dto.gpbs.GooglePlayLatestReceiptInfo;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.receipt.GooglePlayReceiptResponse;
import in.wynk.payment.dto.gpbs.request.GooglePlayAppDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.gpbs.view.GooglePlayReview;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.IapVerificationResponse;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.dto.response.gpbs.GooglePlayBillingResponse;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.util.Pair;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
//import static in.wynk.common.constant.BaseConstants.BUILD_NO;
import static in.wynk.payment.dto.gpbs.GooglePlayConstant.PURCHASE_NOTIFICATION_TYPE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE;
import static in.wynk.payment.dto.gpbs.GooglePlayConstant.*;

/**
 * @author Nishesh Pandey
 */

@Slf4j
@Service(BeanConstant.GOOGLE_PLAY_BILLING)
public class GooglePlayMerchantPaymentService
        implements GooglePlayService, IMerchantIapPaymentVerificationService, IPaymentNotificationService<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>>,
        IReceiptDetailService<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>, GooglePlayCallbackRequest> {

    private static final List<String> REFUND_NOTIFICATION = Collections.singletonList("CANCEL");

    @Value("${payment.googlePlay.baseUrl}")
    private String baseUrl;

    @Value("${payment.googlePlay.purchaseUrl}")
    private String purchaseUrl;

    @Value("${payment.googlePlay.key}")
    private String key;

    @Value("${payment.googlePlay.rajTv.privateKey}")
    private String privateKey;
    @Value("${payment.googlePlay.rajTv.privateKeyId}")
    private String privateKeyId;
    @Value("${payment.googlePlay.rajTv.clientEmail}")
    private String clientEmail;

    private final Gson gson;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final WynkRedisLockService wynkRedisLockService;
    private GooglePlayReview googlePlayReview;
    private GooglePlayCacheService googlePlayCacheService;


    public GooglePlayMerchantPaymentService (@Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, Gson gson, ObjectMapper mapper,
                                             ApplicationEventPublisher eventPublisher, WynkRedisLockService wynkRedisLockService,
                                             GooglePlayReview googlePlayReview, GooglePlayCacheService googlePlayCacheService, PaymentCachingService cachingService) {
        this.gson = gson;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.wynkRedisLockService = wynkRedisLockService;
        this.googlePlayReview = googlePlayReview;
        this.googlePlayCacheService = googlePlayCacheService;
    }

    @Deprecated
    @Override
    public GooglePlayLatestReceiptResponse getLatestReceiptResponse (IapVerificationRequest iapVerificationRequest) {
        return null;
    }

    //for event notification
    @Override
    public void handleNotification (Transaction transaction, UserPlanMapping<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>> mapping) {

    }

    //for Notification
    @Override
    public UserPlanMapping<Pair<GooglePlayLatestReceiptInfo, ReceiptDetails>> getUserPlanMapping (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        return null;
    }

    //handle notification
    @Override
    public DecodedNotificationWrapper<GooglePlayCallbackRequest> isNotificationEligible (String requestPayload) {
        return null;
    }

    //for notification
    @Override
    public PaymentEvent getPaymentEvent (DecodedNotificationWrapper<GooglePlayCallbackRequest> wrapper) {
        return null;
    }

    @Override
    public BaseResponse<?> verifyReceipt (LatestReceiptResponse latestReceiptResponse) {

        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final IapVerificationResponse.IapVerification.IapVerificationBuilder builder = IapVerificationResponse.IapVerification.builder();

        try {
            final Transaction transaction = TransactionContext.get();
            final GooglePlayLatestReceiptResponse response = (GooglePlayLatestReceiptResponse) latestReceiptResponse;

            fetchAndUpdateFromReceipt(transaction, response);

            final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT));
            if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {

                if (latestReceiptResponse.getSuccessUrl() != null) {
                    builder.url(new StringBuilder(latestReceiptResponse.getSuccessUrl()).toString());
                } else {
                    String success_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"), "${payment.success.page}");

                    builder.url(new StringBuilder(success_url).append(SessionContextHolder.getId())
                            .append(SLASH)
                            .append(sessionDTO.<String>get(OS))
                            .append(QUESTION_MARK)
                            .append(SERVICE)
                            .append(EQUAL)
                            .append(sessionDTO.<String>get(SERVICE))
                            .append(AND)
                            .append(BUILD_NO)
                            .append(EQUAL)
                            .append(sessionDTO.<Integer>get(BUILD_NO))
                            .toString());
                }


            } else {

                if (latestReceiptResponse.getFailureUrl() != null) {
                    builder.url(new StringBuilder(latestReceiptResponse.getFailureUrl()).toString());

                } else {
                    String failure_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}");
                    builder.url(new StringBuilder(failure_url).append(SessionContextHolder.getId())
                            .append(SLASH)
                            .append(sessionDTO.<String>get(OS))
                            .append(QUESTION_MARK)
                            .append(SERVICE)
                            .append(EQUAL)
                            .append(sessionDTO.<String>get(SERVICE))
                            .append(AND)
                            .append(BUILD_NO)
                            .append(EQUAL)
                            .append(sessionDTO.<Integer>get(BUILD_NO))
                            .toString());
                }
            }
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT));
            final String failure_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}");
            builder.url(new StringBuilder(failure_url).append(SessionContextHolder.getId())
                    .append(SLASH)
                    .append(sessionDTO.<String>get(OS))
                    .append(QUESTION_MARK)
                    .append(SERVICE)
                    .append(EQUAL)
                    .append(sessionDTO.<String>get(SERVICE))
                    .append(AND)
                    .append(BUILD_NO)
                    .append(EQUAL)
                    .append(sessionDTO.<Integer>get(BUILD_NO))
                    .toString());
            log.error(GOOGLE_PLAY_VERIFICATION_FAILURE, e.getMessage(), e);
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().message(e.getMessage()).success(false).data(builder.build()).build())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public LatestReceiptResponse getLatestReceiptResponse (IapVerificationRequestV2 iapVerificationRequest) {
        final GooglePlayVerificationRequest request = (GooglePlayVerificationRequest) iapVerificationRequest;
        return getReceiptObjForUser(request, request.getAppDetails());
    }

    private void fetchAndUpdateFromReceipt (Transaction transaction, GooglePlayLatestReceiptResponse response) {
    }

    private LatestReceiptResponse getReceiptObjForUser (GooglePlayVerificationRequest request, GooglePlayAppDetails appDetails) {
        GooglePlayPaymentDetails paymentDetails = request.getPaymentDetails();
        //GooglePlayReceiptResponse googlePlayReceiptResponse = googlePlayResponse(paymentDetails.getPurchaseToken(), paymentDetails.getOrderId(), appDetails.getPackageName(), appDetails.getService());

        /*return GooglePlayLatestReceiptResponse.builder()
                .freeTrial(false)
                .autoRenewal(googlePlayReceiptResponse.isAutoRenewing())
                .googlePlayResponse(googlePlayReceiptResponse)
                .planId(cachingService.getPlanFromSku(request.getProductDetails().getPlanId()).getId())
                .purchaseToken(paymentDetails.getPurchaseToken())
                .extTxnId(paymentDetails.getPurchaseToken())
                .couponCode(googlePlayReceiptResponse.getPromotionCode())
                .build();*/

        GooglePlayReceiptResponse googlePlayReceiptResponse1= new GooglePlayReceiptResponse();
        googlePlayReceiptResponse1.setAutoRenewing(true);
        googlePlayReceiptResponse1.setLinkedPurchaseToken("abc");
        googlePlayReceiptResponse1.setAcknowledgementState(12);
        googlePlayReceiptResponse1.setCancelReason(14);
        return GooglePlayLatestReceiptResponse.builder()
                .freeTrial(false)
                .autoRenewal(true)
                .googlePlayResponse(googlePlayReceiptResponse1)
                .planId(420)
                .purchaseToken("abc")
                .extTxnId("abc")
                .couponCode("GOOGLE_IAP")
                .build();
    }

    private GooglePlayReceiptResponse googlePlayResponse (String purchaseToken, String orderId, String packageName, String service) {
        try {
            String url = baseUrl.concat(packageName).concat(purchaseUrl).concat(orderId).concat(TOKEN).concat(purchaseToken).concat(API_KEY_PARAM).concat(key);
            HttpHeaders headers = getHeaders(service);
            return getPlayStoreResponse(url, service, headers);

        } catch (HttpStatusCodeException e) {
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE, "Exception while getting data from google Play: {}", e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY011, e);
        } catch (Exception e) {
            log.error(GOOGLE_PLAY_VERIFICATION_FAILURE, "failed to execute googlePlayResponse due to ", e);
            throw new WynkRuntimeException(WynkErrorType.UT999, e);
        }
    }

    private GooglePlayReceiptResponse getPlayStoreResponse (String url, String service, HttpHeaders headers) {
        ResponseEntity<GooglePlayReceiptResponse> responseEntity = null;
        try {
           responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlayReceiptResponse.class);
        }catch (Exception e){
            log.error(PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE, "Exception while getting data from google Play API: {}", e.getMessage());
        }

        if (HttpStatus.UNAUTHORIZED.equals(responseEntity.getStatusCode())) {
            googlePlayCacheService.generateJwtTokenAndGetAccessToken(service, clientEmail, privateKeyId, privateKey);
            headers = getHeaders(service);
            responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), GooglePlayReceiptResponse.class);
        }
        if (responseEntity.getBody() != null) {
            return responseEntity.getBody();
        } else {
            throw new WynkRuntimeException(PaymentErrorType.PAY027);
        }
    }

    private HttpHeaders getHeaders (String service) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.set(HttpHeaders.AUTHORIZATION, AUTH_TOKEN_PREFIX.concat(googlePlayCacheService.get(service)));
        return headers;
    }

    @Override
    public BaseResponse<GooglePlayBillingResponse> verifyReceiptDetails (GooglePlayVerificationRequest googlePlayVerificationRequest) {

        final GooglePlayBillingResponse.GooglePlayBillingData.GooglePlayBillingDataBuilder builder = GooglePlayBillingResponse.GooglePlayBillingData.builder();

        String id = googlePlayVerificationRequest.getPaymentDetails().getPurchaseToken();
        Integer planId = Objects.nonNull(googlePlayVerificationRequest.getProductDetails().getPlanId()) ? Integer.parseInt(googlePlayVerificationRequest.getProductDetails().getPlanId()) : 0;
        ReceiptDetails response = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class)
                .findByPlanIdAndId(planId, id);
        if (response == null) {
            return null;
        }
        GooglePlayReceiptDetails receiptDetails = (GooglePlayReceiptDetails) response;
        GooglePlayPaymentDetails paymentDetails = GooglePlayPaymentDetails
                .builder()
                .valid(true)
                .orderId(receiptDetails.getOrderId())
                .purchaseToken(receiptDetails.getPurchaseToken()).build();

        PageResponseDetails pageDetails = PageResponseDetails
                .builder()
                .pageUrl("successUrl")
                .build();//Add success url

        builder.paymentDetails(paymentDetails)
                        .pageDetails(pageDetails);

        return BaseResponse.<GooglePlayBillingResponse>builder().body(GooglePlayBillingResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
    }
}
