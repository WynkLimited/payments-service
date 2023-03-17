package in.wynk.payment.gateway.aps.status;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.request.status.refund.ApsRefundStatusRequest;
import in.wynk.payment.dto.aps.response.refund.ApsExternalPaymentRefundStatusResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.common.response.DefaultPaymentStatusResponse;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.ChargingTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.RefundTransactionReconciliationStatusRequest;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import in.wynk.payment.service.IPaymentStatusService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import in.wynk.payment.core.constant.PaymentConstants;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY889;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_REFUND_STATUS;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_REFUND_STATUS_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentErrorType.*;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PaymentConstants.APS_PAYMENT_STATUS)
public class ApsStatusGateway implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

    @Value("${aps.payment.refund.status.api}")
    private String REFUND_STATUS_ENDPOINT;
    @Value("${aps.payment.charge.status.api}")
    private String CHARGING_STATUS_ENDPOINT;

    private final ApsCommonGateway common;
    private final RestTemplate httpTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<Class<? extends AbstractTransactionReconciliationStatusRequest>, IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>
            statusDelegate = new HashMap<>();

    public ApsStatusGateway (ApsCommonGateway common, @Qualifier("apsHttpTemplate") RestTemplate httpTemplate,ApplicationEventPublisher eventPublisher) {
        this.common = common;
        this.httpTemplate = httpTemplate;
        this.eventPublisher = eventPublisher;
        this.statusDelegate.put(ChargingTransactionReconciliationStatusRequest.class, new ChargingTransactionReconciliationStatusService());
        this.statusDelegate.put(RefundTransactionReconciliationStatusRequest.class, new RefundTransactionReconciliationStatusService());
    }

    @Override
    public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> reconStatusService =
                statusDelegate.get(request.getClass());
        if (Objects.isNull(reconStatusService)){
            throw new WynkRuntimeException(PAY889, "Unknown transaction status request to process for uid: " + transaction.getUid());
        }
        return reconStatusService.status(request);
    }

    private class ChargingTransactionReconciliationStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            syncChargingTransactionFromSource(transaction);
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(APS_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(APS_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }

    private class RefundTransactionReconciliationStatusService implements IPaymentStatusService<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest> {

        @Override
        public AbstractPaymentStatusResponse status (AbstractTransactionStatusRequest request) {
            final Transaction transaction = TransactionContext.get();
            RefundTransactionReconciliationStatusRequest refundRequest = (RefundTransactionReconciliationStatusRequest) request;
            syncRefundTransactionFromSource(transaction, refundRequest.getExtTxnId());
            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(APS_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY004);
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(APS_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at APS end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY003);
            }
            return DefaultPaymentStatusResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).transactionType(transaction.getType()).build();
        }
    }

    public void syncChargingTransactionFromSource (Transaction transaction) {
        final String txnId = transaction.getIdStr();
        final boolean fetchHistoryTransaction = false;
        final MerchantTransactionEvent.Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final URI uri = httpTemplate.getUriTemplateHandler().expand(CHARGING_STATUS_ENDPOINT, txnId, fetchHistoryTransaction);

            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsRefundStatusRequest> requestEntity = new RequestEntity<>(null, headers, HttpMethod.GET, URI.create(uri.toString()));
            /*ApsApiResponseWrapper<List<ApsChargeStatusResponse>> response =
                    common.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>>() {
                    });*/
            ApsApiResponseWrapper<List<ApsChargeStatusResponse>> response =
                    common.exchange1(uri.toString(), HttpMethod.GET, null, new TypeReference<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>>() {
                    });
            assert response != null;
            if (response.isResult()) {
                final List<ApsChargeStatusResponse> body = response.getData();
                final ApsChargeStatusResponse status = body.get(0);
                if (status.getPaymentStatus().equalsIgnoreCase("PAYMENT_SUCCESS")) {
                    transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                } else if (status.getPaymentStatus().equalsIgnoreCase("PAYMENT_FAILED")) {
                    transaction.setStatus(TransactionStatus.FAILURE.getValue());
                }
                builder.request(status).response(status);
                builder.externalTransactionId(status.getPgId());
            }
        } catch (HttpStatusCodeException e) {
            builder.request(e.getResponseBodyAsString()).response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PAY998, e);
        } catch (Exception e) {
            log.error(APS_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PAY998, e);
        } finally {
            if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE) {
                eventPublisher.publishEvent(builder.build());
            }
        }
    }

    private void syncRefundTransactionFromSource (Transaction transaction, String refundId) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final ApsRefundStatusRequest refundStatusRequest = ApsRefundStatusRequest.builder().refundId(refundId).build();
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsRefundStatusRequest> requestEntity = new RequestEntity<>(refundStatusRequest, headers, HttpMethod.POST, URI.create(REFUND_STATUS_ENDPOINT));
            /*ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse> response =
                    common.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>>() {
                    });*/
            ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse> response =
                    common.exchange1(REFUND_STATUS_ENDPOINT, HttpMethod.POST, refundStatusRequest, new TypeReference<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>>() {
                    });
            assert response != null;
            if (!response.isResult()) {
                throw new WynkRuntimeException("Unable to initiate Refund");
            }
            final ApsExternalPaymentRefundStatusResponse body = response.getData();
            mBuilder.request(refundStatusRequest);
            mBuilder.response(body);
            mBuilder.externalTransactionId(body.getRefundId());
            if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (HttpStatusCodeException e) {
            mBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PAY998, e);
        } catch (Exception e) {
            log.error(APS_REFUND_STATUS, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PAY998, e);
        } finally {
            transaction.setStatus(finalTransactionStatus.name());
            eventPublisher.publishEvent(mBuilder.build());
        }

    }
}
