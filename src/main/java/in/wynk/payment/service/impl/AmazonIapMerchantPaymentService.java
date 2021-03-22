package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.Utils;
import in.wynk.data.enums.State;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.WynkUserExtUserDao;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.UserPlanMapping;
import in.wynk.payment.dto.amazonIap.*;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.request.UserMappingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.IapVerificationResponse;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
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
public class AmazonIapMerchantPaymentService implements IMerchantIapPaymentVerificationService, IMerchantPaymentStatusService, IReceiptDetailService, IUserMappingService, IPaymentNotificationService {

    private static final List<String> SUBSCRIBED_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_MODIFIED_IMMEDIATE", "SUBSCRIPTION_RENEWED", "SUBSCRIPTION_PURCHASED");
    private static final List<String> PURCHASE_NOTIFICATIONS = Arrays.asList("CONSUMABLE_PURCHASED", "ENTITLEMENT_PURCHASED", "SUBSCRIPTION_PURCHASED");
    private static final List<String> UNSUBSCRIBE_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_AUTO_RENEWAL_OFF", "SUBSCRIPTION_EXPIRED");
    private static final List<String> CANCELLED_NOTIFICATIONS = Arrays.asList("SUBSCRIPTION_CANCELLED", "ENTITLEMENT_CANCELLED", "CONSUMABLE_CANCELLED");
    private final ObjectMapper mapper;
    private final ReceiptDetailsDao receiptDetailsDao;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService cachingService;
    private final WynkUserExtUserDao userMappingDao;
    @Value("${payment.merchant.amazonIap.secret}")
    private String amazonIapSecret;
    @Value("${payment.merchant.amazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;
    @Autowired
    @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;
    @Value("${payment.failure.page}")
    private String FAILURE_PAGE;

    public AmazonIapMerchantPaymentService(ObjectMapper mapper, WynkUserExtUserDao userMappingDao, ReceiptDetailsDao receiptDetailsDao, ApplicationEventPublisher eventPublisher, PaymentCachingService cachingService) {
        this.mapper = mapper;
        this.receiptDetailsDao = receiptDetailsDao;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.userMappingDao = userMappingDao;
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
        stringToSign += msg.getType() + "\n";
        return stringToSign;
    }

    private static byte[] getMessageBytesToSign(AmazonNotificationRequest msg) {
        return buildNotificationStringToSign(msg).getBytes();
    }

    private static void verifyMessageSignatureURL(AmazonNotificationRequest msg, URL endpoint) {
        URI certUri = URI.create(msg.getSigningCertURL());

        if (!"https".equals(certUri.getScheme())) {
            throw new SecurityException("SigningCertURL was not using HTTPS: " + certUri.toString());
        }

        if (!endpoint.toString().equals(certUri.getHost())) {
            throw new SecurityException(
                    String.format("SigningCertUrl does not match expected endpoint. " +
                                    "Expected %s but received endpoint was %s.",
                            endpoint, certUri.getHost()));

        }
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
    public BaseResponse<IapVerificationResponse> verifyReceipt(IapVerificationRequest iapVerificationRequest) {
        final String sid = SessionContextHolder.getId();
        final String os = SessionContextHolder.<SessionDTO>getBody().get(BaseConstants.OS);
        final IapVerificationResponse.IapVerification.IapVerificationBuilder builder = IapVerificationResponse.IapVerification.builder();
        try {
            final Transaction transaction = TransactionContext.get();
            final AmazonIapVerificationRequest request = (AmazonIapVerificationRequest) iapVerificationRequest;
            transaction.putValueInPaymentMetaData("amazonIapVerificationRequest", request);
            fetchAndUpdateTransaction(transaction);
            if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                builder.url(SUCCESS_PAGE + sid + BaseConstants.SLASH + os);
            } else {
                builder.url(FAILURE_PAGE + sid + BaseConstants.SLASH + os);
            }
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().data(builder.build()).build()).status(HttpStatus.OK).build();
        } catch (Exception e) {
            log.error(AMAZON_IAP_VERIFICATION_FAILURE, e.getMessage(), e);
            return BaseResponse.<IapVerificationResponse>builder().body(IapVerificationResponse.builder().success(false).data(builder.build()).build()).status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ChargingStatusResponse fetchChargingStatusFromAmazonIapSource(Transaction transaction) {
        if (EnumSet.of(TransactionStatus.FAILURE).contains(transaction.getStatus())) {
            reconcileReceipt(transaction);
        }
        ChargingStatusResponse.ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
    }

    private void reconcileReceipt(Transaction transaction) {
        final MerchantTransaction merchantTransaction = transaction.getValueFromPaymentMetaData(PaymentConstants.MERCHANT_TRANSACTION);
        transaction.putValueInPaymentMetaData("amazonIapVerificationRequest", mapper.convertValue(merchantTransaction.getRequest(), AmazonIapVerificationRequest.class));
        fetchAndUpdateTransaction(transaction);
    }

    private void fetchAndUpdateTransaction(Transaction transaction) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        PaymentErrorEvent.Builder errorBuilder = PaymentErrorEvent.builder(transaction.getIdStr());
        AmazonIapVerificationRequest request = transaction.getValueFromPaymentMetaData("amazonIapVerificationRequest");
        Optional<ReceiptDetails> mapping = receiptDetailsDao.findById(request.getReceipt().getReceiptId());
        try {
            AmazonIapReceiptResponse amazonIapReceipt = getReceiptStatus(request.getReceipt().getReceiptId(), request.getUserData().getUserId());
            builder.request(request).externalTransactionId(request.getReceipt().getReceiptId()).response(amazonIapReceipt);
            if ((!mapping.isPresent() || mapping.get().getState() != State.ACTIVE) & EnumSet.of(TransactionStatus.INPROGRESS).contains(transaction.getStatus())) {
                saveReceipt(transaction, request.getReceipt().getReceiptId(), request.getUserData().getUserId());
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
            builder.response(e.getResponseBodyAsString());
            errorBuilder.code(AmazonIapStatusCode.ERR_003.getCode()).description(AmazonIapStatusCode.ERR_003.getDescription());
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE, "failed to execute fetchAndUpdateTransaction for amazonIap due to ", e);
            errorBuilder.code(AmazonIapStatusCode.ERR_004.getCode()).description(AmazonIapStatusCode.ERR_004.getDescription());
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        } finally {
            transaction.setStatus(finalTransactionStatus.name());
            eventPublisher.publishEvent(builder.build());
            if (transaction.getStatus() != TransactionStatus.SUCCESS)
                eventPublisher.publishEvent(errorBuilder.build());
        }
    }

    private void saveReceipt(Transaction transaction, String receiptId, String amzUserId) {
        AmazonReceiptDetails amazonReceiptDetails = AmazonReceiptDetails.builder()
                .receiptId(receiptId)
                .id(receiptId).amzUserId(amzUserId)
                .uid(transaction.getUid())
                .msisdn(transaction.getMsisdn())
                .planId(transaction.getPlanId())
                .build();
        receiptDetailsDao.save(amazonReceiptDetails);
    }

    private AmazonIapReceiptResponse getReceiptStatus(String receiptId, String userId) {
        String requestUrl = amazonIapStatusUrl + amazonIapSecret + "/user/" + userId + "/receiptId/" + receiptId;
        RequestEntity<String> requestEntity = new RequestEntity<>(HttpMethod.GET, URI.create(requestUrl));
        ResponseEntity<AmazonIapReceiptResponse> responseEntity = restTemplate.exchange(requestEntity, AmazonIapReceiptResponse.class);
        if (responseEntity.getBody() != null)
            return responseEntity.getBody();
        else
            throw new WynkRuntimeException(PaymentErrorType.PAY012);
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionStatusRequest chargingStatusRequest) {
        ChargingStatusResponse statusResponse;
        Transaction transaction = TransactionContext.get();
        switch (chargingStatusRequest.getMode()) {
            case SOURCE:
                statusResponse = fetchChargingStatusFromAmazonIapSource(transaction);
                break;
            case LOCAL:
                statusResponse = fetchChargingStatusFromDataSource(transaction);
                break;
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return BaseResponse.<ChargingStatusResponse>builder()
                .status(HttpStatus.OK)
                .body(statusResponse)
                .build();
    }

    private ChargingStatusResponse fetchChargingStatusFromDataSource(Transaction transaction) {
        ChargingStatusResponse.ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
    }

    @Override
    public void addUserMapping(UserMappingRequest request) {
        WynkUserExtUserMapping mapping = WynkUserExtUserMapping.builder().id(request.getWynkUserId())
                .externalUserId(request.getExternalUserId())
                .msisdn(request.getMsisdn()).build();
        userMappingDao.save(mapping);
    }

    @Override
    public UserPlanMapping getUserPlanMapping(String requestPayload) {
        AmazonNotificationRequest request = Utils.getData(requestPayload, AmazonNotificationRequest.class);
        AmazonNotificationMessage message = Utils.getData(request.getMessage(), AmazonNotificationMessage.class);
        AmazonIapReceiptResponse receiptResponse = getReceiptStatus(message.getReceiptId(), message.getAppUserId());
        PlanDTO planDTO = cachingService.getPlanFromSku(receiptResponse.getTermSku());
        if (Objects.isNull(planDTO)) {
            throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid sku " + receiptResponse.getTermSku());
        }
        WynkUserExtUserMapping mapping = userMappingDao.findByExternalUserId(message.getAppUserId());
        return UserPlanMapping.builder().uid(mapping.getId()).msisdn(mapping.getMsisdn()).planId(planDTO.getId()).build();
    }

    @Override
    public boolean isNotificationEligible(String requestPayload) {
        AmazonNotificationRequest request = Utils.getData(requestPayload, AmazonNotificationRequest.class);
        if (isMessageSignatureValid(request)) {
            try {
                AmazonNotificationMessage message = Utils.getData(request.getMessage(), AmazonNotificationMessage.class);
                if (receiptDetailsDao.existsById(message.getReceiptId())
                        || userMappingDao.countByExternalUserId(message.getAppUserId()) == 1) {
                    return true;
                }
            } catch (Exception e) {
                throw new WynkRuntimeException(PaymentErrorType.PAY400, "Invalid message");
            }
        }
        return false;
    }

    @Override
    public void handleNotification(Transaction transaction, UserPlanMapping mapping) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        AmazonNotificationMessage amazonIapReceipt = (AmazonNotificationMessage) mapping.getMessage();
        saveReceipt(TransactionContext.get(), amazonIapReceipt.getReceiptId(), amazonIapReceipt.getAppUserId());
        if (SUBSCRIBED_NOTIFICATIONS.contains(amazonIapReceipt.getNotificationType()) || PURCHASE_NOTIFICATIONS.contains(amazonIapReceipt.getNotificationType())) {
            finalTransactionStatus = TransactionStatus.SUCCESS;
        } else if (CANCELLED_NOTIFICATIONS.contains(amazonIapReceipt.getNotificationType())) {
            transaction.setType(PaymentEvent.CANCELLED.name());
            finalTransactionStatus = TransactionStatus.SUCCESS;
        } else if (UNSUBSCRIBE_NOTIFICATIONS.contains(amazonIapReceipt.getNotificationType())) {
            transaction.setType(PaymentEvent.UNSUBSCRIBE.name());
            finalTransactionStatus = TransactionStatus.SUCCESS;
        }
        transaction.setStatus(finalTransactionStatus.name());
    }
}
