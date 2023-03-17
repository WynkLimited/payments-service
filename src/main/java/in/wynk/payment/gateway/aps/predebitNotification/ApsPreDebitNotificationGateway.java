package in.wynk.payment.gateway.aps.predebitNotification;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.request.predebit.ApsPreDebitNotificationRequest;
import in.wynk.payment.dto.aps.response.predebit.ApsPreDebitNotification;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import in.wynk.payment.service.IPreDebitNotificationService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY032;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_PRE_DEBIT_NOTIFICATION_ERROR;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_PRE_DEBIT_NOTIFICATION_SUCCESS;
import static in.wynk.payment.core.constant.UpiConstants.UPI;
import static in.wynk.payment.dto.apb.ApbConstants.TXN_SUCCESS;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsPreDebitNotificationGateway implements IPreDebitNotificationService {

    @Value("${aps.payment.predebit.api}")
    private String PRE_DEBIT_API;

    private ITransactionManagerService transactionManager;
    private final ApsCommonGateway common;

    public ApsPreDebitNotificationGateway (ITransactionManagerService transactionManager, ApsCommonGateway common) {
        this.transactionManager = transactionManager;
        this.common = common;
    }

    @Override
    public AbstractPreDebitNotificationResponse notify (PreDebitNotificationMessage message) {
        try {
            Transaction transaction = transactionManager.get(message.getTransactionId());
            String invoiceNumber = RecurringTransactionUtils.generateInvoiceNumber();
            ApsPreDebitNotificationRequest request = buildApsPreDebitInfoRequest(message.getTransactionId(), message.getDate(), UPI, transaction.getAmount(), invoiceNumber);
            final HttpHeaders headers = new HttpHeaders();
            RequestEntity<ApsPreDebitNotificationRequest> requestEntity = new RequestEntity<>(request, headers, HttpMethod.POST, URI.create(PRE_DEBIT_API));
            /*ApsApiResponseWrapper<ApsPreDebitNotification> response = common.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsPreDebitNotification>>() {
            });*/
            ApsApiResponseWrapper<ApsPreDebitNotification> response =
                    common.exchange1(PRE_DEBIT_API, HttpMethod.POST, request, new TypeReference<ApsApiResponseWrapper<ApsPreDebitNotification>>() {
                    });
            TransactionStatus transactionStatus =
                    TXN_SUCCESS.equals(Objects.requireNonNull(response).getData().getNotificationStatus().getTxnStatus()) ? TransactionStatus.SUCCESS : TransactionStatus.FAILURE;
            if (response.isResult()) {
                log.info(APS_PRE_DEBIT_NOTIFICATION_SUCCESS, "invoiceId: " + invoiceNumber);
            } else {
                log.error(APS_PRE_DEBIT_NOTIFICATION_ERROR, response.getErrorMessage());
                throw new WynkRuntimeException(PAY032);
            }
            return ApsPreDebitNotification.builder().requestId(request.getPreDebitRequestId()).tid(message.getTransactionId()).transactionStatus(transactionStatus)
                    .notificationStatus(response.getData().getNotificationStatus()).build();
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
     * @return Aps notification request with randomly created pre-debit Request id.
     */

    private ApsPreDebitNotificationRequest buildApsPreDebitInfoRequest (String transactionId, String preDebitDate, String paymentMode, double amount, String invoiceNumber) {
        long preDebitFirst = (long) (Math.random() * 100000000000000000L) + 910000000000000000L;
        long preDebitSecond = (long) (Math.random() * 90000000000000000L);
        String preDebitRequestId = preDebitFirst + "_" + preDebitSecond;
        return ApsPreDebitNotificationRequest.builder().mandateTransactionId(transactionId).preDebitRequestId(preDebitRequestId).debitDate(preDebitDate).paymentMode(paymentMode)
                .invoiceNumber(invoiceNumber).amount(amount).build();
    }
}
