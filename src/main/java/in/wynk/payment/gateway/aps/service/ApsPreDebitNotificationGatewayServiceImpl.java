package in.wynk.payment.gateway.aps.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.aps.request.predebit.PreDebitNotificationRequest;
import in.wynk.payment.dto.aps.response.predebit.PreDebitNotification;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.gateway.IPaymentRenewal;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IPreDebitNotificationService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY032;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_PRE_DEBIT_NOTIFICATION_ERROR;
import static in.wynk.payment.constant.UpiConstants.UPI;
import static in.wynk.payment.dto.aps.common.ApsConstant.TXN_SUCCESS;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsPreDebitNotificationGatewayServiceImpl implements IPreDebitNotificationService {

    private final String SI_PAYMENT_API;

    private ITransactionManagerService transactionManager;
    private final ApsCommonGatewayService common;
    private final IPaymentRenewal<PaymentRenewalChargingRequest> renewalGateway;

    public ApsPreDebitNotificationGatewayServiceImpl(String siPaymentApi,
                                                     ObjectMapper mapper,
                                                     PaymentCachingService payCache,
                                                     IMerchantTransactionService merchantTransactionService,
                                                     ITransactionManagerService transactionManager,
                                                     ApsCommonGatewayService common,
                                                     ApplicationEventPublisher eventPublisher) {
        this.common = common;
        this.transactionManager = transactionManager;
        this.SI_PAYMENT_API = siPaymentApi;
        this.renewalGateway = new ApsRenewalGatewayServiceImpl(siPaymentApi, mapper, common, payCache, merchantTransactionService, eventPublisher);
    }

    @Override
    public AbstractPreDebitNotificationResponse notify (PreDebitNotificationMessage message) {
        try {
            Transaction transaction = transactionManager.get(message.getTransactionId());
            String invoiceNumber = RecurringTransactionUtils.generateInvoiceNumber();
            PreDebitNotificationRequest request = buildApsPreDebitInfoRequest(message.getTransactionId(), message.getDate(), UPI, transaction.getAmount(), invoiceNumber);

            //PreDebitNotification apsPreDebitNotification = common.exchange(PRE_DEBIT_API, HttpMethod.POST, common.getLoginId(transaction.getMsisdn()),request, PreDebitNotification.class);
          /*  TransactionStatus transactionStatus = TXN_SUCCESS.equals(apsPreDebitNotification.getNotificationStatus().getTxnStatus()) ? TransactionStatus.SUCCESS : TransactionStatus.FAILURE;
            return PreDebitNotification.builder().requestId(request.getPreDebitRequestId()).tid(message.getTransactionId()).transactionStatus(transactionStatus)
                    .notificationStatus(apsPreDebitNotification.getNotificationStatus()).build();*/
            return null;
        } catch (Exception e) {
            log.error(APS_PRE_DEBIT_NOTIFICATION_ERROR, e.getMessage());
            throw new WynkRuntimeException(PAY032);
        }
    }

    /**
     * @param transactionId
     * @param preDebitDate
     * @param paymentMode
     * @param amount
     * @param invoiceNumber
     * @return ApsGateway notification request with randomly created pre-debit Request id.
     */

    private PreDebitNotificationRequest buildApsPreDebitInfoRequest (String transactionId, String preDebitDate, String paymentMode, double amount, String invoiceNumber) {
        long preDebitFirst = (long) (Math.random() * 100000000000000000L) + 910000000000000000L;
        long preDebitSecond = (long) (Math.random() * 90000000000000000L);
        String preDebitRequestId = preDebitFirst + "_" + preDebitSecond;
        return PreDebitNotificationRequest.builder().mandateTransactionId(transactionId).preDebitRequestId(preDebitRequestId).debitDate(preDebitDate).paymentMode(paymentMode)
               /* .invoiceNumber(invoiceNumber)*/.amount(amount).build();
    }
}
