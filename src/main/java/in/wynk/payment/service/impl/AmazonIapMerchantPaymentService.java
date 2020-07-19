package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.enums.WynkService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.amazonIap.AmazonIapReceiptResponse;
import in.wynk.payment.dto.amazonIap.AmazonIapVerificationRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatus;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Calendar;

@Service(BeanConstant.AMAZON_IAP_PAYMENT_SERVICE)
public class AmazonIapMerchantPaymentService implements IMerchantIapPaymentVerificationService {

    @Value("${payment.merchant.amazonIap.secret}")
    private String amazonIapSecret;

    @Value("${payment.merchant.amazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;

    @Value("${payment.status.web.url}")
    private String statusWebUrl;

    private static ObjectMapper mapper = new ObjectMapper();
    private final ITransactionManagerService transactionManager;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final RestTemplate restTemplate;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final PaymentCachingService cachingService;

    public AmazonIapMerchantPaymentService(RestTemplate restTemplate, ITransactionManagerService transactionManager, ISQSMessagePublisher sqsMessagePublisher, ISubscriptionServiceManager subscriptionServiceManager, PaymentCachingService cachingService) {
        this.restTemplate = restTemplate;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.cachingService = cachingService;
    }

    @Override
    public BaseResponse<Void> verifyReceipt(IapVerificationRequest iapVerificationRequest) {
        try {
            AmazonIapVerificationRequest amazonIapVerificationRequest = (AmazonIapVerificationRequest) iapVerificationRequest;
            ChargingStatus amazonIapVerificationResponse = validateTransaction(amazonIapVerificationRequest);
            URIBuilder returnUrl = new URIBuilder(statusWebUrl);
            returnUrl.addParameter("status", amazonIapVerificationResponse.getTransactionStatus().name());
            return BaseResponse.redirectResponse(returnUrl.build().toString());
        }
        catch (Exception e){
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        }
    }

    private ChargingStatus validateTransaction(AmazonIapVerificationRequest amazonIapVerificationRequest){
        try {
            AmazonIapReceiptResponse amazonIapReceipt = getReceiptStatus(amazonIapVerificationRequest.getReceipt().getReceiptId(), amazonIapVerificationRequest.getUserData().getUserId());
            if (amazonIapReceipt == null) {
                throw new WynkRuntimeException(PaymentErrorType.PAY012, "Unable to verify amazon iap receipt for payment resposne received from client");
            }
            final PlanDTO selectedPlan = cachingService.getPlan(amazonIapVerificationRequest.getPlanId());
            final double finalPlanAmount = selectedPlan.getPrice().getAmount();

            TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
            TransactionEvent transactionEvent = TransactionEvent.SUBSCRIBE;

            if (amazonIapReceipt.getCancelDate() == null) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            }
            else {
                transactionEvent = TransactionEvent.UNSUBSCRIBE;
            }

            MerchantTransaction merchantTransaction = MerchantTransaction.builder()
                    .request(amazonIapVerificationRequest)
                    .response(amazonIapReceipt)
                    .externalTransactionId(amazonIapReceipt.getReceiptID())
                    .build();

            Transaction transaction = transactionManager.upsert(Transaction.builder()
                    .planId(amazonIapVerificationRequest.getPlanId())
                    .amount(finalPlanAmount)
                    .initTime(Calendar.getInstance())
                    .consent(Calendar.getInstance())
                    .uid(amazonIapVerificationRequest.getUid())
                    .service(amazonIapVerificationRequest.getService())
                    .paymentChannel(PaymentCode.AMAZON_IAP.name())
                    .status(finalTransactionStatus.name())
                    .type(transactionEvent.name())
                    .merchantTransaction(merchantTransaction)
                    .build());

            subscriptionServiceManager.subscribePlanSync(amazonIapVerificationRequest.getPlanId(),
                    transaction.getId().toString(),
                    SessionContextHolder.get().getId().toString(),
                    transaction.getUid(),
                    transaction.getMsisdn(),
                    WynkService.fromString(transaction.getService()),
                    transaction.getStatus());

            return ChargingStatus
                    .builder()
                    .transactionStatus(transaction.getStatus())
                    .build();
        }
        catch (Exception e){
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        }
    }

    private AmazonIapReceiptResponse getReceiptStatus(String receiptId, String userId){
        String requestUrl = amazonIapStatusUrl + amazonIapSecret + "/user/" + userId + "/receiptId/" + receiptId;
        AmazonIapReceiptResponse receiptObj = null;
        try {
            RequestEntity<String> requestEntity = new RequestEntity<>(HttpMethod.GET, URI.create(requestUrl));
            ResponseEntity<String> responseEntity = restTemplate.exchange(requestEntity, String.class);
            if(responseEntity.getBody()!=null)
                receiptObj = mapper.readValue(responseEntity.getBody(), AmazonIapReceiptResponse.class);

        }
        catch (HttpStatusCodeException | JsonProcessingException e){
            throw new WynkRuntimeException(PaymentErrorType.PAY012, e);
        }
        return receiptObj;
    }

}
