package in.wynk.payment.gateway.aps.renewal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.common.SiPaymentInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
import in.wynk.payment.dto.aps.request.renewal.ApsSiPaymentRecurringRequest;
import in.wynk.payment.dto.aps.response.renewal.ApsRenewalStatusResponse;
import in.wynk.payment.dto.aps.response.renewal.ApsSiPaymentRecurringResponse;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import in.wynk.payment.service.IMerchantPaymentRenewalServiceV2;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.dto.apb.ApbConstants.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PaymentConstants.AIRTEL_PAY_STACK_RENEW)
public class ApsRenewalGateway implements IMerchantPaymentRenewalServiceV2<PaymentRenewalChargingMessage> {

    @Value("${aps.payment.renewal.api}")
    private String SI_PAYMENT_API;

    private final ApsCommonGateway common;
    private final IMerchantTransactionService merchantTransactionService;
    private PaymentCachingService cachingService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;


    public ApsRenewalGateway (ApsCommonGateway common, IMerchantTransactionService merchantTransactionService, PaymentCachingService cachingService, ObjectMapper objectMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.common = common;
        this.merchantTransactionService = merchantTransactionService;
        this.cachingService = cachingService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void renew (PaymentRenewalChargingMessage message) {
        Transaction transaction = TransactionContext.get();
        MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(message.getId());
        if (merchantTransaction == null) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("No merchant transaction found for Subscription");
        }
        PlanPeriodDTO planPeriodDTO = cachingService.getPlan(transaction.getPlanId()).getPeriod();
        if (planPeriodDTO.getMaxRetryCount() < message.getAttemptSequence()) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("Need to break the chain in Payment Renewal as maximum attempts are already exceeded");
        }
        try {
            ApsSiPaymentRecurringResponse apsRenewalResponse = objectMapper.convertValue(merchantTransaction.getResponse(), ApsSiPaymentRecurringResponse.class);
            String mode = apsRenewalResponse.getBody().getData().getPaymentMode();
            AnalyticService.update(PaymentConstants.PAYMENT_MODE, mode);
            if (isMandateExisting()) {
                apsRenewalResponse = doChargingForRenewal(merchantTransaction, mode);
            }

            if (Objects.nonNull(apsRenewalResponse)) {
                updateTransactionStatus(planPeriodDTO, apsRenewalResponse, transaction);
            }

        } catch (WynkRuntimeException e) {
            if (e.getErrorCode().equals(PAY014.getErrorCode())) {
                transaction.setStatus(TransactionStatus.TIMEDOUT.getValue());
            } else if (e.getErrorCode().equals(PAY009.getErrorCode()) || e.getErrorCode().equals(PAY035.getErrorCode())) {
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }
            throw e;
        }
    }


    private boolean isMandateExisting () {
        return true;
    }

    private ApsSiPaymentRecurringResponse doChargingForRenewal (MerchantTransaction merchantTransaction, String mode) {
        Transaction transaction = TransactionContext.get();

        double amount = cachingService.getPlan(transaction.getPlanId()).getFinalPrice();
        String invoiceNumber = RecurringTransactionUtils.generateInvoiceNumber();
        ApsSiPaymentRecurringRequest apsSiPaymentRecurringRequest =
                ApsSiPaymentRecurringRequest.builder().transactionId(transaction.getIdStr()).userInfo(UserInfo.builder().loginId("7417656401").build())
                        .siPaymentInfo(SiPaymentInfo.builder().mandateTransactionId(merchantTransaction.getExternalTransactionId()).paymentMode(mode).paymentAmount(amount).invoiceNumber(invoiceNumber)
                                .build())
                        .build();
        try {
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsSiPaymentRecurringRequest<SiPaymentInfo>> requestEntity =
                    new RequestEntity<ApsSiPaymentRecurringRequest<SiPaymentInfo>>(apsSiPaymentRecurringRequest, headers, HttpMethod.POST, URI.create(SI_PAYMENT_API));
            /*ApsApiResponseWrapper<ApsSiPaymentRecurringResponse> response = common.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsSiPaymentRecurringResponse>>() {
            });*/
            ApsApiResponseWrapper<ApsSiPaymentRecurringResponse> response =
                    common.exchange1(SI_PAYMENT_API, HttpMethod.POST, apsSiPaymentRecurringRequest, new TypeReference<ApsApiResponseWrapper<ApsSiPaymentRecurringResponse>>() {
                    });
            return Objects.requireNonNull(response).getData();
        } catch (RestClientException e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException(e);
        }
    }

    private void updateTransactionStatus (PlanPeriodDTO planPeriodDTO, ApsSiPaymentRecurringResponse apsRenewalResponse, Transaction transaction) {
        int retryInterval = planPeriodDTO.getRetryInterval();
        if (apsRenewalResponse.getStatusCodeValue() == HttpStatus.OK.value()) {
            ApsRenewalStatusResponse renewalResponse = apsRenewalResponse.getBody().getData();
            if (PG_STATUS_SUCCESS.equalsIgnoreCase(renewalResponse.getPgStatus())) {
                transaction.setStatus(TransactionStatus.SUCCESS.getValue());
            } else if (PG_STATUS_FAILED.equalsIgnoreCase(renewalResponse.getPgStatus())) {
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                    StringUtils.equalsIgnoreCase(PG_STATUS_PENDING, renewalResponse.getPgStatus())) {
                transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
            } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                    StringUtils.equalsIgnoreCase(PG_STATUS_PENDING, renewalResponse.getPgStatus())) {
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }
        } else {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(apsRenewalResponse.getStatusCode()).build());
        }
    }

    /**
     * @param transaction
     * @return map having client and oderId for which charge status to be fetched
     */
    private Object buildApsRequest (Transaction transaction) {
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(BaseConstants.CLIENT, transaction.getClientAlias());
        requestMap.add(PaymentConstants.ORDER_ID, transaction.getIdStr());
        return requestMap;
    }
}
