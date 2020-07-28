package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.commons.constants.Constants;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.amazonIap.AmazonIapReceiptResponse;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Slf4j
@Service(BeanConstant.AMAZON_IAP_PAYMENT_SERVICE)
public class AmazonIapMerchantPaymentService implements IMerchantIapPaymentVerificationService {

    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;
    @Value("${payment.merchant.amazonIap.secret}")
    private String amazonIapSecret;
    @Value("${payment.merchant.amazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;
    @Value("${payment.status.web.url}")
    private String statusWebUrl;

    public AmazonIapMerchantPaymentService(ObjectMapper mapper, RestTemplate restTemplate, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager) {
        this.mapper = mapper;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }


    @Override
    public BaseResponse<Void> verifyReceipt(IapVerificationRequest iapVerificationRequest) {
        try {
            final AmazonIapVerificationRequest request = (AmazonIapVerificationRequest) iapVerificationRequest;
            final SessionDTO sessionDTO = SessionContextHolder.getBody();
            final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());
            final String msisdn = sessionDTO.get(Constants.MSISDN);

            Transaction transaction = transactionManager.initiateTransaction(request.getUid(), msisdn, selectedPlan.getId(), selectedPlan.getPrice().getAmount(), PaymentCode.AMAZON_IAP, TransactionEvent.PURCHASE);
            transaction.putValueInPaymentMetaData("amazonIapVerificationRequest", request);
            transactionManager.updateAndPublishSync(transaction, this::fetchAndUpdateTransaction);
            URIBuilder returnUrl = new URIBuilder(statusWebUrl);
            returnUrl.addParameter(PaymentConstants.STATUS, transaction.getStatus().name());
            return BaseResponse.redirectResponse(returnUrl.build().toString());
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        }
    }

    private void fetchAndUpdateTransaction(Transaction transaction) {
        AmazonIapVerificationRequest request = transaction.getValueFromPaymentMetaData("amazonIapVerificationRequest");
        try {
            AmazonIapReceiptResponse amazonIapReceipt = getReceiptStatus(request.getReceipt().getReceiptId(), request.getUserData().getUserId());
            if (amazonIapReceipt == null) {
                throw new WynkRuntimeException(PaymentErrorType.PAY012, "Unable to verify amazon iap receipt for payment response received from client");
            }

            TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
            TransactionEvent transactionEvent = TransactionEvent.SUBSCRIBE;

            if (amazonIapReceipt.getCancelDate() == null) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else {
                transactionEvent = TransactionEvent.UNSUBSCRIBE;
            }

            eventPublisher.publishEvent(MerchantTransactionEvent.builder()
                    .id(transaction.getIdStr())
                    .externalTransactionId(amazonIapReceipt.getReceiptID())
                    .request(request)
                    .response(amazonIapReceipt)
                    .build());

            transaction.setType(transactionEvent.name());
            transaction.setStatus(finalTransactionStatus.name());
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        }
    }

    private AmazonIapReceiptResponse getReceiptStatus(String receiptId, String userId) {
        String requestUrl = amazonIapStatusUrl + amazonIapSecret + "/user/" + userId + "/receiptId/" + receiptId;
        AmazonIapReceiptResponse receiptObj = null;
        try {
            RequestEntity<String> requestEntity = new RequestEntity<>(HttpMethod.GET, URI.create(requestUrl));
            ResponseEntity<String> responseEntity = restTemplate.exchange(requestEntity, String.class);
            if (responseEntity.getBody() != null)
                receiptObj = mapper.readValue(responseEntity.getBody(), AmazonIapReceiptResponse.class);

        } catch (HttpStatusCodeException | JsonProcessingException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        }
        return receiptObj;
    }

}
