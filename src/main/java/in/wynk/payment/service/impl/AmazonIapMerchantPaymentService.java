package in.wynk.payment.service.impl;

import in.wynk.audit.IAuditableListener;
import in.wynk.audit.constant.AuditConstants;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.Utils;
import in.wynk.data.enums.State;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.WynkUserExtUserDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.amazonIap.*;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.request.UserMappingRequest;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.payment.utils.SnsSignatureVerifier;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import static in.wynk.payment.core.constant.PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE;

@Slf4j
@Service(BeanConstant.AMAZON_IAP_PAYMENT_SERVICE)
public class AmazonIapMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantIapPaymentPreVerificationService, IMerchantIapSubscriptionAcknowledgementService, IMerchantIapPaymentVerificationService, IReceiptDetailService<AmazonIapReceiptResponse, AmazonNotificationRequest>, IUserMappingService, IPaymentNotificationService<AmazonIapReceiptResponse>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> {

    private static final List<String> SUBSCRIBED_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_MODIFIED_IMMEDIATE", "SUBSCRIPTION_PURCHASED");
    private static final List<String> RENEW_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_RENEWED", "SUBSCRIPTION_CONVERTED_FREE_TRIAL_TO_PAID");
    private static final List<String> PURCHASE_NOTIFICATIONS = Arrays.asList("CONSUMABLE_PURCHASED", "ENTITLEMENT_PURCHASED");
    private static final List<String> FIRST_TIME_NOTIFICATIONS = Arrays.asList("CONSUMABLE_PURCHASED", "ENTITLEMENT_PURCHASED", "SUBSCRIPTION_PURCHASED");
    private static final List<String> UNSUBSCRIBE_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_AUTO_RENEWAL_OFF", "SUBSCRIPTION_EXPIRED");
    private static final List<String> CANCELLED_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_CANCELLED", "ENTITLEMENT_CANCELLED", "CONSUMABLE_CANCELLED");
    private static final List<String> SUB_MODIFICATION_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_RENEWED", "SUBSCRIPTION_CONVERTED_FREE_TRIAL_TO_PAID", "SUBSCRIPTION_CANCELLED", "SUBSCRIPTION_AUTO_RENEWAL_OFF", "SUBSCRIPTION_EXPIRED", "SUBSCRIPTION_MODIFIED_IMMEDIATE", "ENTITLEMENT_CANCELLED", "CONSUMABLE_CANCELLED");
    private static final List<String> AMAZON_SNS_CONFIRMATION = Arrays.asList("SubscriptionConfirmation", "UnsubscribeConfirmation");

    private static final Map<Integer, String> CANCELLATION_REASON = new HashMap<Integer, String>() {{
        put(0, "The cancel reason is currently unavailable and will render at a later time.");
        put(1, "Your customer canceled the order.");
        put(2, "The purchase was canceled by Amazon's system.");
    }};
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService cachingService;
    @Value("${payment.merchant.AmazonIap.secret}")
    private String amazonIapSecret;
    @Value("${payment.merchant.AmazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;
    private final RestTemplate restTemplate;
    private final SnsSignatureVerifier signatureVerifier;
    private final IAuditableListener auditingListener;

    public AmazonIapMerchantPaymentService(ApplicationEventPublisher eventPublisher, PaymentCachingService cachingService, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, IErrorCodesCacheService errorCodesCacheServiceImpl, SnsSignatureVerifier signatureVerifier, @Qualifier(AuditConstants.MONGO_AUDIT_LISTENER) IAuditableListener auditingListener) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.signatureVerifier = signatureVerifier;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.restTemplate = restTemplate;
        this.auditingListener = auditingListener;
    }

    private static String buildNotificationStringToSign(AmazonNotificationRequest msg) {
        String stringToSign = "Message\n";
        stringToSign += msg.getMessage() + "\n";
        stringToSign += "MessageId\n";
        stringToSign += msg.getMessageId() + "\n";
        if (msg.getSubject() != null) {
            stringToSign += "Subject\n";
            stringToSign += msg.getSubject() + "\n";
        }
        stringToSign += "Timestamp\n";
        stringToSign += msg.getTimestamp() + "\n";
        stringToSign += "TopicArn\n";
        stringToSign += msg.getTopicArn() + "\n";
        stringToSign += "Type\n";
        stringToSign += msg.getNotificationType() + "\n";
        return stringToSign;
    }

    private static byte[] getMessageBytesToSign(AmazonNotificationRequest msg) {
        if (AMAZON_SNS_CONFIRMATION.contains(msg.getNotificationType())) {
            return buildSubscriptionStringToSign(msg).getBytes();
        }
        return buildNotificationStringToSign(msg).getBytes();
    }

    private static void verifyMessageSignatureURL(AmazonNotificationRequest msg, URL endpoint) {
        URI certUri = URI.create(msg.getSigningCertURL());

        if (!"https".equals(certUri.getScheme())) {
            throw new SecurityException("SigningCertURL was not using HTTPS: " + certUri);
        }

        if (!endpoint.getHost().equals(certUri.getHost())) {
            throw new SecurityException(
                    String.format("SigningCertUrl does not match expected endpoint. " +
                                    "Expected %s but received endpoint was %s.",
                            endpoint, certUri.getHost()));

        }
    }

    public static String buildSubscriptionStringToSign(AmazonNotificationRequest msg) {
        String stringToSign = "Message\n";
        stringToSign += msg.getMessage() + "\n";
        stringToSign += "MessageId\n";
        stringToSign += msg.getMessageId() + "\n";
        stringToSign += "SubscribeURL\n";
        stringToSign += msg.getSubscribeUrl() + "\n";
        stringToSign += "Timestamp\n";
        stringToSign += msg.getTimestamp() + "\n";
        stringToSign += "Token\n";
        stringToSign += msg.getToken() + "\n";
        stringToSign += "TopicArn\n";
        stringToSign += msg.getTopicArn() + "\n";
        stringToSign += "Type\n";
        stringToSign += msg.getNotificationType() + "\n";
        return stringToSign;
    }

    private boolean isMessageSignatureValid(AmazonNotificationRequest msg) {
        try {
            URL url = new URL(msg.getSigningCertURL());
            verifyMessageSignatureURL(msg, url);
//            InputStream inStream = url.openStream();
            RequestEntity<String> requestEntity = new RequestEntity<>(HttpMethod.GET, url.toURI());
            ResponseEntity<Resource> responseEntity = restTemplate.exchange(requestEntity, Resource.class);
            InputStream inStream = Objects.requireNonNull(responseEntity.getBody()).getInputStream();
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);
            inStream.close();

            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(cert.getPublicKey());
            sig.update(getMessageBytesToSign(msg));
            return sig.verify(Base64.decodeBase64(msg.getSignature()));
        } catch (Exception e) {
            throw new SecurityException("Verify method failed.", e);
        }
    }

    @Override
    public BaseResponse<IapVerificationResponse> verifyReceipt(LatestReceiptResponse latestReceiptResponse) {
        final String sid = SessionContextHolder.getId();
        final String os = SessionContextHolder.<SessionDTO>getBody().get(BaseConstants.OS);
        final IapVerificationResponse.IapVerification.IapVerificationBuilder builder = IapVerificationResponse.IapVerification.builder();
        try {
            final Transaction transaction = TransactionContext.get();
            final AmazonLatestReceiptResponse response = (AmazonLatestReceiptResponse) latestReceiptResponse;
            fetchAndUpdateTransaction(transaction, response);
            final String clientPagePlaceHolder = PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT));
            if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                final String success_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"), "${payment.success.page}");
                builder.url(success_url + sid + BaseConstants.SLASH + os);
            } else {
                final String failure_url = EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.success.page}");
                builder.url(failure_url + sid + BaseConstants.SLASH + os);
            }
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            log.error(AMAZON_IAP_VERIFICATION_FAILURE, e.getMessage(), e);
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().success(false).data(builder.build()).build()).status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public LatestReceiptResponse getLatestReceiptResponse(IapVerificationRequest iapVerificationRequest) {
        boolean autoRenewal = false;
        AmazonIapVerificationRequest amazonIapVerificationRequest = (AmazonIapVerificationRequest) iapVerificationRequest;
        AmazonIapReceiptResponse amazonIapReceiptResponse = getReceiptStatus(amazonIapVerificationRequest.getReceipt().getReceiptId(), amazonIapVerificationRequest.getUserData().getUserId());
        if (amazonIapReceiptResponse.getRenewalDate() != null && amazonIapReceiptResponse.getRenewalDate() > System.currentTimeMillis()) {
            autoRenewal = true;
        }
        String skuId = amazonIapReceiptResponse.getTermSku();
        if (StringUtils.isEmpty(skuId)) {
            skuId = amazonIapVerificationRequest.getReceipt().getSku();
        }
        return AmazonLatestReceiptResponse.builder()
                .freeTrial(Objects.nonNull(amazonIapReceiptResponse.getFreeTrialEndDate()))
                .autoRenewal(autoRenewal)
                .amazonIapReceiptResponse(amazonIapReceiptResponse)
                .planId(cachingService.getPlanFromSku(skuId).getId())
                .service(iapVerificationRequest.getService())
                .extTxnId(amazonIapVerificationRequest.getReceipt().getReceiptId())
                .amazonUserId(amazonIapVerificationRequest.getUserData().getUserId())
                .build();
    }

    //Needs to be implemented for v2 request
    @Override
    public LatestReceiptResponse getLatestReceiptResponse(IapVerificationRequestV2 iapVerificationRequest) {
        return null;
    }

    @SneakyThrows
    private ChargingStatusResponse fetchChargingStatusFromAmazonIapSource(Transaction transaction, String extTxnId) {
        if (EnumSet.of(TransactionStatus.FAILURE).contains(transaction.getStatus())) {
            fetchAndUpdateTransaction(transaction, AmazonLatestReceiptResponse.builder().extTxnId(extTxnId).build());
        }
        ChargingStatusResponse.ChargingStatusResponseBuilder<?, ?> responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId(),transaction.getMsisdn()));
        }
        return responseBuilder.build();
    }

    private void fetchAndUpdateTransaction(Transaction transaction, AmazonLatestReceiptResponse amazonLatestReceiptResponse) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        PaymentErrorEvent.Builder errorBuilder = PaymentErrorEvent.builder(transaction.getIdStr());
        Optional<ReceiptDetails> mapping = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(amazonLatestReceiptResponse.getExtTxnId());
        try {
            if ((!mapping.isPresent() || mapping.get().getState() != State.ACTIVE) & EnumSet.of(TransactionStatus.INPROGRESS).contains(transaction.getStatus())) {
                saveReceipt(transaction.getUid(), transaction.getMsisdn(), transaction.getPlanId(), amazonLatestReceiptResponse.getExtTxnId(), amazonLatestReceiptResponse.getAmazonUserId(), transaction.getIdStr(), amazonLatestReceiptResponse.getService());
                AmazonIapReceiptResponse amazonIapReceipt = amazonLatestReceiptResponse.getAmazonIapReceiptResponse();
                if (amazonIapReceipt != null) {
                    if (amazonIapReceipt.getCancelDate() == null) {
                        finalTransactionStatus = TransactionStatus.SUCCESS;
                    } else {
                        transaction.setType(PaymentEvent.CANCELLED.getValue());
                    }
                } else {
                    errorBuilder.code(AmazonIapStatusCode.ERR_002.getCode()).description(AmazonIapStatusCode.ERR_002.getDescription());
                    throw new WynkRuntimeException(PaymentErrorType.PAY012, "Unable to verify amazon iap receipt for payment response received from client");
                }
            } else if (((mapping.isPresent() && mapping.get().getState() == State.ACTIVE) & EnumSet.of(TransactionStatus.FAILURE).contains(transaction.getStatus()))) { // added for recon
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else {
                log.warn("Receipt is already present for uid: {}, planId: {} and receiptId: {}", transaction.getUid(), transaction.getPlanId(), mapping.get().getId());
                errorBuilder.code(AmazonIapStatusCode.ERR_001.getCode()).description(AmazonIapStatusCode.ERR_001.getDescription());
                finalTransactionStatus = TransactionStatus.FAILUREALREADYSUBSCRIBED;
            }
        } catch (HttpStatusCodeException e) {
            errorBuilder.code(AmazonIapStatusCode.ERR_003.getCode()).description(AmazonIapStatusCode.ERR_003.getDescription());
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE, "failed to execute fetchAndUpdateTransaction for amazonIap due to ", e);
            errorBuilder.code(AmazonIapStatusCode.ERR_004.getCode()).description(AmazonIapStatusCode.ERR_004.getDescription());
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        } finally {
            transaction.setStatus(finalTransactionStatus.name());
            if (transaction.getStatus() != TransactionStatus.SUCCESS)
                eventPublisher.publishEvent(errorBuilder.build());
        }
    }

    private void saveReceipt(String uid, String msisdn, int planId, String receiptId, String amzUserId, String transactionId, String service) {
        final AmazonReceiptDetails amazonReceiptDetails = AmazonReceiptDetails.builder().paymentTransactionId(transactionId).receiptTransactionId(receiptId).amazonUserId(amzUserId).msisdn(msisdn).planId(planId).id(receiptId).uid(uid).service(service).build();
        auditingListener.onBeforeSave(amazonReceiptDetails);
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).save(amazonReceiptDetails);
    }

    private AmazonIapReceiptResponse getReceiptStatus(String receiptId, String userId) {
        final String iapSecret = ClientContext.getClient().map((client) -> {
            try {
                return PropertyResolverUtils.resolve(client.getAlias(), BeanConstant.AMAZON_IAP_PAYMENT_SERVICE, PaymentConstants.MERCHANT_SECRET);
            } catch (Exception e) {
                return null;
            }
        }).orElse(amazonIapSecret);
        String requestUrl = amazonIapStatusUrl + iapSecret + "/user/" + userId + "/receiptId/" + receiptId;
        RequestEntity<String> requestEntity = new RequestEntity<>(HttpMethod.GET, URI.create(requestUrl));
        ResponseEntity<AmazonIapReceiptResponse> responseEntity = restTemplate.exchange(requestEntity, AmazonIapReceiptResponse.class);
        if (responseEntity.getBody() != null)
            return responseEntity.getBody();
        else
            throw new WynkRuntimeException(PaymentErrorType.PAY012);
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchChargingStatusFromAmazonIapSource(TransactionContext.get(), transactionStatusRequest.getExtTxnId());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(statusResponse).build();
    }

    @Override
    public void addUserMapping(UserMappingRequest request) {
        WynkUserExtUserMapping mapping = WynkUserExtUserMapping.builder().id(request.getWynkUserId())
                .externalUserId(request.getExternalUserId())
                .msisdn(request.getMsisdn()).build();
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), WynkUserExtUserDao.class).save(mapping);
    }

    @Override
    public UserPlanMapping<AmazonIapReceiptResponse> getUserPlanMapping(DecodedNotificationWrapper<AmazonNotificationRequest> wrapper) {
        AmazonNotificationMessage message = Utils.getData(wrapper.getDecodedNotification().getMessage(), AmazonNotificationMessage.class);
        AmazonIapReceiptResponse receiptResponse = getReceiptStatus(message.getReceiptId(), message.getAppUserId());
        PlanDTO planDTO = cachingService.getPlanFromSku(receiptResponse.getTermSku());
        if (Objects.isNull(planDTO)) {
            throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid sku " + receiptResponse.getTermSku());
        }
        Optional<ReceiptDetails> optionalReceiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(message.getReceiptId());
        final UserPlanMapping.UserPlanMappingBuilder<AmazonIapReceiptResponse> builder = UserPlanMapping.<AmazonIapReceiptResponse>builder()
                .message(AmazonIapReceiptResponseWrapper.builder().receiptID(message.getReceiptId()).appUserId(message.getAppUserId()).decodedResponse(receiptResponse).build());
        if (receiptResponse.getFreeTrialEndDate() != null) {
            if (planDTO.getLinkedFreePlanId() != -1) {
                builder.planId(planDTO.getLinkedFreePlanId());
            } else {
                log.error("No Free Trial mapping present for planId {}", planDTO.getId());
                throw new WynkRuntimeException(PaymentErrorType.PAY035);
            }
        } else {
            builder.planId(planDTO.getId());
        }
        if (optionalReceiptDetails.isPresent()) {
            final ReceiptDetails details = optionalReceiptDetails.get();
            builder.uid(details.getUid()).msisdn(details.getMsisdn()).linkedTransactionId(details.getPaymentTransactionId());
            return builder.service(planDTO.getService()).build();
        }
        WynkUserExtUserMapping mapping = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), WynkUserExtUserDao.class)
                .findByExternalUserId(message.getAppUserId());
        return builder.uid(mapping.getId()).msisdn(mapping.getMsisdn()).service(planDTO.getService()).build();
    }

    @Override
    public DecodedNotificationWrapper<AmazonNotificationRequest> isNotificationEligible(String requestPayload) {
        AmazonNotificationRequest request = Utils.getData(requestPayload, AmazonNotificationRequest.class);
        if (signatureVerifier.verifySignature(requestPayload)) {
            if (AMAZON_SNS_CONFIRMATION.contains(request.getNotificationType())) {
                subscribeToSns(request);
            } else {
                try {
                    AmazonNotificationMessage message = Utils.getData(request.getMessage(), AmazonNotificationMessage.class);
                    //TODO: check for eligibility cases.
                    final boolean existsById = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).existsById(message.getReceiptId());
                    if ((!existsById && FIRST_TIME_NOTIFICATIONS.contains(message.getNotificationType()) && RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), WynkUserExtUserDao.class).countByExternalUserId(message.getAppUserId()) == 1)
                            || (existsById && SUB_MODIFICATION_NOTIFICATIONS.contains(message.getNotificationType()))) {
                        return DecodedNotificationWrapper.<AmazonNotificationRequest>builder().eligible(true).decodedNotification(request).build();
                    }
                } catch (Exception e) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid message");
                }
            }
        }
        return DecodedNotificationWrapper.<AmazonNotificationRequest>builder().eligible(false).decodedNotification(request).build();
    }

//
//    @Override
//    public UserPlanMapping<AmazonIapReceiptResponse> getUserPlanMapping(DecodedNotificationWrapper<IAPNotification> wrapper) {
//        AmazonNotificationMessage message = Utils.getData(wrapper.getDecodedNotification().getMessage(), AmazonNotificationMessage.class);
//        AmazonIapReceiptResponse receiptResponse = getReceiptStatus(message.getReceiptId(), message.getAppUserId());
//        PlanDTO planDTO = cachingService.getPlanFromSku(receiptResponse.getTermSku());
//        if (Objects.isNull(planDTO)) {
//            throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid sku " + receiptResponse.getTermSku());
//        }
//        WynkUserExtUserMapping mapping = userMappingDao.findByExternalUserId(message.getAppUserId());
//        return UserPlanMapping.<AmazonIapReceiptResponse>builder().uid(mapping.getId()).msisdn(mapping.getMsisdn()).message(receiptResponse).planId(planDTO.getId()).build();
//    }

    private void subscribeToSns(AmazonNotificationRequest request) {
        restTemplate.getForEntity(request.getSubscribeUrl(), String.class);
    }

    @Override
    public void handleNotification(Transaction transaction, UserPlanMapping<AmazonIapReceiptResponse> mapping) {
        final AmazonIapReceiptResponseWrapper wrapper = (AmazonIapReceiptResponseWrapper) mapping.getMessage();
        saveReceipt(mapping.getUid(), mapping.getMsisdn(), transaction.getPlanId(), wrapper.getReceiptID(), wrapper.getAppUserId(), transaction.getIdStr(), mapping.getService());
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        AmazonIapReceiptResponse amazonIapReceipt = mapping.getMessage();
        if (amazonIapReceipt.getCancelDate() == null || transaction.getType() == PaymentEvent.CANCELLED) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
        }
        transaction.setStatus(finalTransactionStatus.name());
    }

    @Override
    public PaymentEvent getPaymentEvent(DecodedNotificationWrapper<AmazonNotificationRequest> wrapper, String productType) {
        final AmazonNotificationMessage message = Utils.getData(wrapper.getDecodedNotification().getMessage(), AmazonNotificationMessage.class);
        if (SUBSCRIBED_NOTIFICATIONS.contains(message.getNotificationType())) {
            return PaymentEvent.SUBSCRIBE;
        } else if (PURCHASE_NOTIFICATIONS.contains(message.getNotificationType())) {
            return PaymentEvent.PURCHASE;
        } else if (CANCELLED_NOTIFICATIONS.contains(message.getNotificationType())) {
            return PaymentEvent.CANCELLED;
        } else if (UNSUBSCRIBE_NOTIFICATIONS.contains(message.getNotificationType())) {
            return PaymentEvent.UNSUBSCRIBE;
        } else if (RENEW_NOTIFICATIONS.contains(message.getNotificationType())) {
            return PaymentEvent.RENEW;
        } else {
            throw new WynkRuntimeException(WynkErrorType.UT001);
        }
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        TransactionStatus status = TransactionStatus.FAILURE;
        final Transaction transaction = TransactionContext.get();
        final AmazonReceiptDetails receipt = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findByPaymentTransactionId(paymentRenewalChargingRequest.getId());
        try {
            final AmazonIapReceiptResponse response = getReceiptStatus(receipt.getId(), receipt.getAmazonUserId());
            if (Objects.nonNull(response)) {
                if (Objects.isNull(response.getCancelDate())) {
                    if (response.getRenewalDate() > System.currentTimeMillis()) {
                        status = TransactionStatus.SUCCESS;
                        return WynkResponseEntity.<Void>builder().build();
                    }
                }
                if (Objects.nonNull(response.getCancelDate())) {
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(PaymentErrorType.APS012.name()).description(CANCELLATION_REASON.get(response.getCancelReason())).build());
                }
            }
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(PaymentErrorType.PAY045.name()).description(PaymentErrorType.PAY045.getErrorMessage()).build());
            throw new WynkRuntimeException(PaymentErrorType.PAY045);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY045, e);
        } finally {
            transaction.setStatus(status.getValue());
            saveReceipt(transaction.getUid(), transaction.getMsisdn(), transaction.getPlanId(), receipt.getId(), receipt.getAmazonUserId(), transaction.getIdStr(), receipt.getService());
        }
    }

    @Override
    public String getIdAndUpdateReceiptDetails(DecodedNotificationWrapper<AmazonNotificationRequest> wrapper) {
        AmazonNotificationMessage message = Utils.getData(wrapper.getDecodedNotification().getMessage(), AmazonNotificationMessage.class);
        Optional<ReceiptDetails> optionalReceiptDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).findById(message.getReceiptId());
        AmazonReceiptDetails amazonReceiptDetails = (AmazonReceiptDetails) optionalReceiptDetails.get();
        amazonReceiptDetails.setExpiry(System.currentTimeMillis());
        auditingListener.onBeforeSave(amazonReceiptDetails);
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), ReceiptDetailsDao.class).save(amazonReceiptDetails);
        return amazonReceiptDetails.getPaymentTransactionId();
    }

    public boolean supportsRenewalReconciliation() {
        return false;
    }

    @Override
    public void verifyRequest(IapVerificationRequestV2Wrapper iapVerificationRequestV2Wrapper) {
        throw new WynkRuntimeException("Method is not implemented");
    }

    @Override
    public void acknowledgeSubscription(AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        throw new WynkRuntimeException("Method is not implemented");
    }

    @Override
    public void publishAsync(AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest) {
        throw new WynkRuntimeException("Method is not implemented");
    }

}
