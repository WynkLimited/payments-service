package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.commons.constants.BaseConstants;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.*;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Value("${payment.merchant.amazonIap.secret}")
    private String amazonIapSecret;
    @Value("${payment.merchant.amazonIap.status.baseUrl}")
    private String amazonIapStatusUrl;
    @Value("${payment.success.page}")
    private String statusWebUrl;

    private final ObjectMapper mapper;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;
    @Autowired
    @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;

    public AmazonIapMerchantPaymentService(ObjectMapper mapper, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager) {
        this.mapper = mapper;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }


    @Override
    public BaseResponse<Void> verifyReceipt(IapVerificationRequest iapVerificationRequest) {
        try {
            final AmazonIapVerificationRequest request = (AmazonIapVerificationRequest) iapVerificationRequest;
            AnalyticService.update(request);
            final SessionDTO sessionDTO = SessionContextHolder.getBody();
            final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());
            final String msisdn = sessionDTO.get(BaseConstants.MSISDN);

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
