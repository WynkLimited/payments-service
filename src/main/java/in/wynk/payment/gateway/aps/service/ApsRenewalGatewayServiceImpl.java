package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.LOB;
import in.wynk.payment.dto.aps.common.SiPaymentInfo;
import in.wynk.payment.dto.aps.request.renewal.SiPaymentRecurringRequest;
import in.wynk.payment.dto.aps.response.renewal.SiPaymentRecurringResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.gateway.IPaymentRenewal;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Objects;
import java.util.Optional;

import static in.wynk.common.constant.BaseConstants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY009;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY035;
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
    private final ITransactionManagerService transactionManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;


    public ApsRenewalGatewayServiceImpl (String siPaymentApi,
                                         ObjectMapper objectMapper,
                                         ApsCommonGatewayService common,
                                         PaymentCachingService cachingService,
                                         IMerchantTransactionService merchantTransactionService,
                                         ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManager, IRecurringPaymentManagerService recurringPaymentManagerService) {
        this.common = common;
        this.objectMapper = objectMapper;
        this.SI_PAYMENT_API = siPaymentApi;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.merchantTransactionService = merchantTransactionService;
        this.transactionManager = transactionManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
    }

    @Override
    public void renew (PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        Transaction transaction = TransactionContext.get();
        PlanPeriodDTO planPeriodDTO = cachingService.getPlan(transaction.getPlanId()).getPeriod();
        if (planPeriodDTO.getMaxRetryCount() < paymentRenewalChargingRequest.getAttemptSequence()) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("Need to break the chain in Payment Renewal as maximum attempts are already exceeded");
        }
        String txnId = paymentRenewalChargingRequest.getId();
        PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(txnId);
        if (Objects.nonNull(renewal) && StringUtils.isNotBlank(renewal.getLastSuccessTransactionId())) {
            txnId = renewal.getLastSuccessTransactionId();
        }
        MerchantTransaction merchantTransaction = getMerchantData(txnId);
        try {
            ApsChargeStatusResponse[] apsChargeStatusResponses = (merchantTransaction == null) ? common.syncChargingTransactionFromSource(transactionManager.get(txnId), Optional.empty()) :
                    objectMapper.convertValue(merchantTransaction.getResponse(), ApsChargeStatusResponse[].class);
            ApsChargeStatusResponse merchantData = apsChargeStatusResponses[0];
            if (Objects.isNull(merchantData.getMandateId())) {
                apsChargeStatusResponses = common.syncChargingTransactionFromSource(transactionManager.get(txnId), Optional.empty());
                merchantData = apsChargeStatusResponses[0];
            }
            AnalyticService.update(PaymentConstants.PAYMENT_MODE, merchantData.getPaymentMode());
            if (Objects.nonNull(merchantData.getMandateId())) {
                SiPaymentRecurringResponse apsRenewalResponse = doChargingForRenewal(merchantData);
                updateTransactionStatus(planPeriodDTO, apsRenewalResponse, transaction);
            } else {
                log.error("Mandate Id is missing for the transaction Id {}", merchantData.getOrderId());
            }
        } catch (WynkRuntimeException e) {
            if (e.getErrorCode().equals(PAY009.getErrorCode()) || e.getErrorCode().equals(PAY035.getErrorCode())) {
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }
            throw e;
        }
    }

    private MerchantTransaction getMerchantData (String id) {
        try {
            return merchantTransactionService.getMerchantTransaction(id);
        } catch (Exception e) {
            log.error("Exception occurred while getting data for tid {} from merchant table: {}", id, e.getMessage());
            return null;
        }
    }

    private SiPaymentRecurringResponse doChargingForRenewal (ApsChargeStatusResponse response) {
        Transaction transaction = TransactionContext.get();
        double amount = cachingService.getPlan(transaction.getPlanId()).getFinalPrice();
        SiPaymentRecurringRequest apsSiPaymentRecurringRequest = SiPaymentRecurringRequest.builder().orderId(transaction.getIdStr()).siPaymentInfo(
                SiPaymentInfo.builder().mandateTransactionId(response.getMandateId()).paymentMode(response.getPaymentMode()).paymentAmount(amount).paymentGateway(response.getPaymentRoutedThrough())
                        .lob(LOB.SI_WYNK.toString()).build()).build();
        try {
            SiPaymentRecurringResponse siResponse =
                    common.exchange(transaction.getClientAlias(), SI_PAYMENT_API, HttpMethod.POST, transaction.getMsisdn(), apsSiPaymentRecurringRequest, SiPaymentRecurringResponse.class);
            if (siResponse == null) {
                siResponse = new SiPaymentRecurringResponse();
            }
            return siResponse;
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw e;
        }
    }

    private void updateTransactionStatus (PlanPeriodDTO planPeriodDTO, SiPaymentRecurringResponse apsRenewalResponse, Transaction transaction) {
        int retryInterval = planPeriodDTO.getRetryInterval();
        if (PG_STATUS_SUCCESS.equalsIgnoreCase(apsRenewalResponse.getPgStatus())) {
            transaction.setStatus(TransactionStatus.SUCCESS.getValue());
        } else if (PG_STATUS_FAILED.equalsIgnoreCase(apsRenewalResponse.getPgStatus())) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(apsRenewalResponse.getPgStatus()).build());
        } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                StringUtils.equalsIgnoreCase(PG_STATUS_PENDING, apsRenewalResponse.getPgStatus())) {
            transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
        } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                StringUtils.equalsIgnoreCase(PG_STATUS_PENDING, apsRenewalResponse.getPgStatus())) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
        }
    }
}
