package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.data.enums.State;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.AmazonReceiptDetails;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.ReceiptDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.receipts.ReceiptDetailsDao;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.amazonIap.AmazonIapReceiptResponse;
import in.wynk.payment.dto.amazonIap.AmazonIapStatusCode;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.IapVerificationResponse;
import in.wynk.payment.service.AbstractMerchantPaymentStatusService;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.EnumSet;
import java.util.Optional;

import static in.wynk.payment.core.constant.PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE;

@Slf4j
@Service(BeanConstant.AMAZON_IAP_PAYMENT_SERVICE)
public class AmazonIapMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantIapPaymentVerificationService, IMerchantPaymentStatusService {

    private final RestTemplate restTemplate;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;
    @Value("${payment.merchant.amazonIap.secret}")
    private String amazonIapSecret;
    @Value("${payment.merchant.amazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;
    private final ObjectMapper mapper;
    @Value("${payment.failure.page}")
    private String FAILURE_PAGE;
    private final ReceiptDetailsDao receiptDetailsDao;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentCachingService cachingService;

    public AmazonIapMerchantPaymentService(ObjectMapper mapper, ReceiptDetailsDao receiptDetailsDao, ApplicationEventPublisher eventPublisher, PaymentCachingService cachingService, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        super(cachingService);
        this.mapper = mapper;
        this.receiptDetailsDao = receiptDetailsDao;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.restTemplate = restTemplate;
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
                AmazonReceiptDetails amazonReceiptDetails = AmazonReceiptDetails.builder()
                        .receiptId(request.getReceipt().getReceiptId())
                        .id(request.getReceipt().getReceiptId())
                        .uid(transaction.getUid())
                        .msisdn(transaction.getMsisdn())
                        .planId(transaction.getPlanId())
                        .build();
                receiptDetailsDao.save(amazonReceiptDetails);
                if (amazonIapReceipt != null) {
                    if (amazonIapReceipt.getCancelDate() == null) {
                        finalTransactionStatus = TransactionStatus.SUCCESS;
                    } else {
                        transaction.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                    }
                } else {
                    errorBuilder.code(AmazonIapStatusCode.ERR_002.getCode()).description(AmazonIapStatusCode.ERR_002.getDescription());
                    throw new WynkRuntimeException(PaymentErrorType.PAY012, "Unable to verify amazon iap receipt for payment response received from client");
                }
            } else if (((mapping.isPresent() && mapping.get().getState() == State.ACTIVE) & EnumSet.of(TransactionStatus.FAILURE).contains(transaction.getStatus()))) { // added for recon
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else {
                log.warn("Receipt is already present for uid: {}, planId: {} and receiptUserId: {}", transaction.getUid(), transaction.getPlanId(), mapping.get().getUserId());
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

    private AmazonIapReceiptResponse getReceiptStatus(String receiptId, String userId) {
        try {
            String requestUrl = amazonIapStatusUrl + amazonIapSecret + "/user/" + userId + "/receiptId/" + receiptId;
            RequestEntity<String> requestEntity = new RequestEntity<>(HttpMethod.GET, URI.create(requestUrl));
            ResponseEntity<String> responseEntity = restTemplate.exchange(requestEntity, String.class);
            if (responseEntity.getBody() != null)
                return mapper.readValue(responseEntity.getBody(), AmazonIapReceiptResponse.class);
            else
                throw new WynkRuntimeException(PaymentErrorType.PAY012);
        } catch (JsonProcessingException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }
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

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchChargingStatusFromAmazonIapSource(TransactionContext.get());
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(statusResponse).build();
    }

    private ChargingStatusResponse fetchChargingStatusFromDataSource(Transaction transaction) {
        ChargingStatusResponse.ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
    }


}
