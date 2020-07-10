package in.wynk.payment.service.impl;

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
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.amazonIap.AmazonIapReceipt;
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
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            httpHeaders.add(HttpHeaders.LOCATION, returnUrl.toString());
            httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            httpHeaders.add(HttpHeaders.PRAGMA, "no-cache");
            httpHeaders.add(HttpHeaders.EXPIRES, String.valueOf(0));
            return BaseResponse.<String>builder().body(returnUrl.toString()).status(HttpStatus.OK).headers(httpHeaders).build();
        }
        catch (Exception e){
            throw new WynkRuntimeException(e);
        }
    }

    private ChargingStatus validateTransaction(AmazonIapVerificationRequest amazonIapVerificationRequest){
        try {
            AmazonIapReceipt amazonIapReceipt = getReceiptStatus(amazonIapVerificationRequest.getPaymentResponse().getReceipt().getReceiptId(), amazonIapVerificationRequest.getPaymentResponse().getUserData().getUserId());
            if (amazonIapReceipt == null) {
                throw new WynkRuntimeException("Unable to verify amazon iap receipt for payment resposne received from client");
            }
            final PlanDTO selectedPlan = getSelectedPlan(amazonIapVerificationRequest.getPlanId());
            final float finalPlanAmount = getFinalPlanAmountToBePaid(selectedPlan);
            Transaction transaction = initialiseTransaction(amazonIapVerificationRequest.getPlanId(), finalPlanAmount, amazonIapVerificationRequest.getUid());
            if (amazonIapReceipt.getCancelDate() == null) {
                transaction.setExitTime(Calendar.getInstance());
                subscriptionServiceManager.publish(amazonIapVerificationRequest.getPlanId(), amazonIapVerificationRequest.getUid(), transaction.getId().toString(), transaction.getStatus(), transaction.getType());
                transaction.setStatus(TransactionStatus.SUCCESS.name());
            } else {
                //TODO - Is there anything else to be done here ?
                transaction.setStatus(TransactionStatus.FAILURE.name());

            }
            transaction.setMerchantTransaction(MerchantTransaction.builder().request(amazonIapVerificationRequest).response(amazonIapReceipt).externalTransactionId(amazonIapReceipt.getReceiptID()).build());
            transactionManager.upsert(transaction);
            return ChargingStatus
                    .builder()
                    .transactionStatus(transaction.getStatus())
                    .build();
        }
        catch (Exception e){
            throw new WynkRuntimeException(e);
        }
    }

    private AmazonIapReceipt getReceiptStatus(String receiptId, String userId){
        String requestUrl = amazonIapStatusUrl + amazonIapSecret + "/user/" + userId + "/receiptId/" + receiptId;
        AmazonIapReceipt receiptObj = null;
        try {
            RequestEntity<String> requestEntity = new RequestEntity<>(HttpMethod.GET, URI.create(requestUrl));
            ResponseEntity<String> responseEntity = restTemplate.exchange(requestEntity, String.class);
            if(responseEntity.getBody()!=null)
                receiptObj = mapper.readValue(responseEntity.getBody(), AmazonIapReceipt.class);

        }
        catch (Exception e){
            throw new WynkRuntimeException(e);
        }
        return receiptObj;
    }

    private Transaction initialiseTransaction(int planId, float amount, String uid) {
        return transactionManager.upsert(Transaction.builder()
                .planId(planId)
                .amount(amount)
                .initTime(Calendar.getInstance())
                .consent(Calendar.getInstance())
                .uid(uid)
                //.service(getValueFromSession(SessionKeys.SERVICE))
                .paymentChannel(PaymentCode.AMAZON_IAP.name())
                .status(TransactionStatus.INPROGRESS.name())
                .type(TransactionEvent.PURCHASE.name())
                .build());
    }


    private PlanDTO getSelectedPlan(int planId) {
        List<PlanDTO> plans = getValueFromSession(SessionKeys.ELIGIBLE_PLANS);
        return plans.stream().filter(plan -> plan.getId() == planId).collect(Collectors.toList()).get(0);
    }

    private <T> T getValueFromSession(String key) {
        Session<SessionDTO> session = SessionContextHolder.get();
        return session.getBody().get(key);
    }

    private float getFinalPlanAmountToBePaid(PlanDTO selectedPlan) {
        float finalPlanAmount = selectedPlan.getPrice().getAmount();
        if (selectedPlan.getPrice().getDiscount().size() > 0) {
            for (DiscountDTO discount : selectedPlan.getPrice().getDiscount()) {
                finalPlanAmount *= ((double) (100 - discount.getPercent()) / 100);
            }
        }
        return finalPlanAmount;
    }
}
