package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.DiscountDTO;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
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
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

@Service(BeanConstant.AMAZON_IAP_MERCHANT_PAYMENT_SERVICE)
public class AmazonIapMerchantPaymentService implements IMerchantIapPaymentVerificationService {

    @Value("${payment.merchant.amazonIap.secret}")
    private String amazonIapSecret;

    @Value("${payment.merchant.amazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;

    @Value("${payment.merchant.amazonIap.return.wynkUrl}")
    private String wynkReturnUrl;

    private static ObjectMapper mapper = new ObjectMapper();
    private final ITransactionManagerService transactionManager;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final RestTemplate restTemplate;
    private final ISubscriptionServiceManager subscriptionServiceManager;

    public AmazonIapMerchantPaymentService(RestTemplate restTemplate, ITransactionManagerService transactionManager, ISQSMessagePublisher sqsMessagePublisher, ISubscriptionServiceManager subscriptionServiceManager) {
        this.restTemplate = restTemplate;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.subscriptionServiceManager = subscriptionServiceManager;
    }

    @Override
    public BaseResponse<String> verifyIap(IapVerificationRequest iapVerificationRequest) {
        try {
            AmazonIapVerificationRequest amazonIapVerificationRequest = (AmazonIapVerificationRequest) iapVerificationRequest;
            ChargingStatus amazonIapVerificationResponse = validateTransaction(amazonIapVerificationRequest);
            URIBuilder returnUrl = new URIBuilder(wynkReturnUrl);
            returnUrl.addParameter("status", amazonIapVerificationResponse.getTransactionStatus().name());
            return BaseResponse.<String>builder().body(returnUrl.toString()).status(HttpStatus.FOUND).build();
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
            final PlanDTO selectedPlan = subscriptionServiceManager.getPlan(amazonIapVerificationRequest.getPlanId());
            final float finalPlanAmount = selectedPlan.getPrice().getAmount();

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

            subscriptionServiceManager.publish(amazonIapVerificationRequest.getPlanId(),
                    amazonIapVerificationRequest.getUid(),
                    transaction.getId().toString(),
                    transaction.getStatus(),
                    transaction.getType());

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
