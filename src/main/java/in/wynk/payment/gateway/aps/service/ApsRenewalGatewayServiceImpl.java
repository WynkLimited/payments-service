package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.SiPaymentInfo;
import in.wynk.payment.dto.aps.request.renewal.SiPaymentRecurringRequest;
import in.wynk.payment.dto.aps.response.renewal.SiPaymentRecurringResponse;
import in.wynk.payment.dto.aps.response.renewal.SiRecurringData;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.gateway.IPaymentRenewal;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.dto.aps.common.ApsConstant.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsRenewalGatewayServiceImpl implements IPaymentRenewal<PaymentRenewalChargingRequest> {

    private final String SI_PAYMENT_API;

    private final ObjectMapper objectMapper;
    private PaymentCachingService cachingService;
    private final ApsCommonGatewayService common;
    private final ApplicationEventPublisher eventPublisher;
    private final IMerchantTransactionService merchantTransactionService;


    public ApsRenewalGatewayServiceImpl(String siPaymentApi,
                                        ObjectMapper objectMapper,
                                        ApsCommonGatewayService common,
                                        PaymentCachingService cachingService,
                                        IMerchantTransactionService merchantTransactionService,
                                        ApplicationEventPublisher eventPublisher) {
        this.common = common;
        this.objectMapper = objectMapper;
        this.SI_PAYMENT_API = siPaymentApi;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public void renew(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        Transaction transaction = TransactionContext.get();
        MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(paymentRenewalChargingRequest.getId());
        if (merchantTransaction == null) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("No merchant transaction found for Subscription");
        }
        PlanPeriodDTO planPeriodDTO = cachingService.getPlan(transaction.getPlanId()).getPeriod();
        if (planPeriodDTO.getMaxRetryCount() < paymentRenewalChargingRequest.getAttemptSequence()) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("Need to break the chain in Payment Renewal as maximum attempts are already exceeded");
        }
        try {
            ApsChargeStatusResponse merchantData = objectMapper.convertValue(merchantTransaction.getResponse(), ApsChargeStatusResponse.class);
            AnalyticService.update(PaymentConstants.PAYMENT_MODE, merchantData.getPaymentMode());

            if(Objects.nonNull(merchantData.getMandateId())) {
                SiPaymentRecurringResponse  apsRenewalResponse = doChargingForRenewal(merchantData);
                if (Objects.nonNull(apsRenewalResponse)) {
                    updateTransactionStatus(planPeriodDTO, apsRenewalResponse, transaction);
                }
            }else {
                log.error("Mandate Id is missing for the transaction Id {}", merchantData.getOrderId());
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


    private boolean isMandateExisting() {
        return true;
    }

    private SiPaymentRecurringResponse doChargingForRenewal(ApsChargeStatusResponse response) {
        Transaction transaction = TransactionContext.get();
        double amount = cachingService.getPlan(transaction.getPlanId()).getFinalPrice();
        SiPaymentRecurringRequest apsSiPaymentRecurringRequest = SiPaymentRecurringRequest.builder().transactionId(transaction.getIdStr()).siPaymentInfo(
                        SiPaymentInfo.builder().mandateTransactionId(response.getMandateId()).paymentMode(response.getPaymentMode()).paymentAmount(amount).paymentGateway(response.getPaymentGateway()).build())
                .build();
        try {
            return common.exchange(transaction.getClientAlias(), SI_PAYMENT_API, HttpMethod.POST, common.getLoginId(transaction.getMsisdn()), apsSiPaymentRecurringRequest, SiPaymentRecurringResponse.class);
        } catch (RestClientException e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException(e);
        }
    }

    private void updateTransactionStatus(PlanPeriodDTO planPeriodDTO, SiPaymentRecurringResponse apsRenewalResponse, Transaction transaction) {
        int retryInterval = planPeriodDTO.getRetryInterval();
        if (apsRenewalResponse.getStatusCodeValue() == HttpStatus.OK.value()) {
            SiRecurringData renewalResponse = apsRenewalResponse.getBody().getData();
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
}
