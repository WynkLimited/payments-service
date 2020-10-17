package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.TransactionEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.TransactionContext;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.dto.amazonIap.AmazonIapReceiptResponse;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.IapVerificationResponse;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

import static in.wynk.payment.core.constant.PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE;

@Slf4j
@Service(BeanConstant.AMAZON_IAP_PAYMENT_SERVICE)
public class AmazonIapMerchantPaymentService implements IMerchantIapPaymentVerificationService {

    @Value("${payment.merchant.amazonIap.secret}")
    private String amazonIapSecret;
    @Value("${payment.merchant.amazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;
    @Value("${payment.failure.page}")
    private String FAILURE_PAGE;

    public AmazonIapMerchantPaymentService(ObjectMapper mapper, ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
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

    private void fetchAndUpdateTransaction(Transaction transaction) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        AmazonIapVerificationRequest request = transaction.getValueFromPaymentMetaData("amazonIapVerificationRequest");
        try {
            builder.request(request);
            AmazonIapReceiptResponse amazonIapReceipt = getReceiptStatus(request.getReceipt().getReceiptId(), request.getUserData().getUserId());
            if (amazonIapReceipt == null) {
                throw new WynkRuntimeException(PaymentErrorType.PAY012, "Unable to verify amazon iap receipt for payment response received from client");
            }

            TransactionEvent transactionEvent = TransactionEvent.PURCHASE;

            if (amazonIapReceipt.getCancelDate() == null) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else {
                transactionEvent = TransactionEvent.UNSUBSCRIBE;
            }

            builder.externalTransactionId(amazonIapReceipt.getReceiptID()).response(amazonIapReceipt);

            transaction.setType(transactionEvent.name());
        } catch (HttpStatusCodeException e) {
            builder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE, "failed to execute fetchAndUpdateTransaction for amazonIap due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        } finally {
            transaction.setStatus(finalTransactionStatus.name());
            eventPublisher.publishEvent(builder.build());
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

}
