package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.ApsPaymentRefundRequest;
import in.wynk.payment.dto.ApsPaymentRefundResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.request.refund.ApsExternalPaymentRefundRequest;
import in.wynk.payment.dto.aps.request.sattlement.ApsSettlementRequest;
import in.wynk.payment.dto.aps.request.status.refund.ApsRefundStatusRequest;
import in.wynk.payment.dto.aps.response.refund.ApsExternalPaymentRefundStatusResponse;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.ChargingTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.PaymentGatewaySettlementRequest;
import in.wynk.payment.dto.request.RefundTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.DefaultPaymentSettlementResponse;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_REFUND_STATUS;

@Slf4j
@Service(PaymentConstants.AIRTEL_PAY_STACK)
public class AirtelPayStackGatewayImpl extends AbstractMerchantPaymentStatusService implements
        IMerchantPaymentSettlement<DefaultPaymentSettlementResponse, PaymentGatewaySettlementRequest>,
        IMerchantPaymentRefundService<ApsPaymentRefundResponse, ApsPaymentRefundRequest>
          {

    @Value("${aps.payment.init.refund.api}")
    private String REFUND_ENDPOINT;
    @Value("${aps.payment.init.settlement.api}")
    private String SETTLEMENT_ENDPOINT;

    @Value("${aps.payment.option.api}")
    private String PAYMENT_OPTION_ENDPOINT;
    @Value("${aps.payment.saved.details.api}")
    private String SAVED_DETAILS_ENDPOINT;
    @Value("${aps.payment.refund.status.api}")
    private String REFUND_STATUS_ENDPOINT;
    @Value("${aps.payment.charge.status.api}")
    private String CHARGING_STATUS_ENDPOINT;
    @Value("${payment.polling.page}")
    private String CLIENT_POLLING_SCREEN_URL;

    private final RestTemplate httpTemplate;
    private final ResourceLoader resourceLoader;
    private final PaymentCachingService cache;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;
    private final PaymentCachingService cachingService;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    private final IMerchantTransactionService merchantTransactionService;
    private ITransactionManagerService transactionManager;


    public AirtelPayStackGatewayImpl (@Qualifier("apsHttpTemplate") RestTemplate httpTemplate, PaymentCachingService cache, IErrorCodesCacheService errorCache,
                                      ApplicationEventPublisher eventPublisher, ResourceLoader resourceLoader,
                                      PaymentMethodCachingService paymentMethodCachingService, ObjectMapper objectMapper, PaymentCachingService cachingService,
                                      IMerchantTransactionService merchantTransactionService, ITransactionManagerService transactionManager) {
        super(cache, errorCache);
        this.cache = cache;
        this.httpTemplate = httpTemplate;
        this.eventPublisher = eventPublisher;
        this.resourceLoader = resourceLoader;
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.objectMapper = objectMapper;
        this.cachingService = cachingService;
        this.merchantTransactionService = merchantTransactionService;
        this.transactionManager = transactionManager;
    }

    @SneakyThrows
    @PostConstruct
    private void init () {
        this.httpTemplate.getInterceptors().add((request, body, execution) -> {
            final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
            final String username = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
            final String password = PropertyResolverUtils.resolve(clientAlias, PaymentConstants.AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
            final String token = AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, token);
            request.getHeaders().add(CHANNEL_ID, AUTH_TYPE_WEB_UNAUTH);
            request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            return execution.execute(request, body);
        });
    }

    private <R , T> ResponseEntity<ApsApiResponseWrapper<R>> exchange (RequestEntity<T> entity, ParameterizedTypeReference<ApsApiResponseWrapper<R>> target) {
        try {
            return httpTemplate.exchange(entity, target);
        } catch (Exception e) {
            throw new WynkRuntimeException(PAY024, e);
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status (AbstractTransactionReconciliationStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        if (request instanceof ChargingTransactionReconciliationStatusRequest) {
            syncChargingTransactionFromSource(transaction);
        } else if (request instanceof RefundTransactionReconciliationStatusRequest) {
            syncRefundTransactionFromSource(transaction, request.getExtTxnId());
        } else {
            throw new WynkRuntimeException(PAY889, "Unknown transaction status request to process for uid: " + transaction.getUid());
        }
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            return WynkResponseEntity.<AbstractChargingStatusResponse>builder()
                    .data(ChargingStatusResponse.success(transaction.getIdStr(), cache.validTillDate(transaction.getPlanId()), transaction.getPlanId())).build();
        } else if (transaction.getStatus() == TransactionStatus.FAILURE) {
            return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(ChargingStatusResponse.failure(transaction.getIdStr(), transaction.getPlanId())).build();
        }
        throw new WynkRuntimeException(PAY025);
    }

    private void syncRefundTransactionFromSource (Transaction transaction, String refundId) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final ApsRefundStatusRequest refundStatusRequest = ApsRefundStatusRequest.builder().refundId(refundId).build();
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsRefundStatusRequest> requestEntity = new RequestEntity<>(refundStatusRequest, headers, HttpMethod.POST, URI.create(REFUND_STATUS_ENDPOINT));
            final ResponseEntity<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>> response =
                    httpTemplate.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>>() {
                    });
            final ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse> wrapper = response.getBody();
            assert wrapper != null;
            if (!wrapper.isResult()) {
                throw new WynkRuntimeException("Unable to initiate Refund");
            }
            final ApsExternalPaymentRefundStatusResponse body = wrapper.getData();
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

    public void syncChargingTransactionFromSource (Transaction transaction) {
        final String txnId = transaction.getIdStr();
        final boolean fetchHistoryTransaction = false;
        final MerchantTransactionEvent.Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final URI uri = httpTemplate.getUriTemplateHandler().expand(CHARGING_STATUS_ENDPOINT, txnId, fetchHistoryTransaction);
            final ResponseEntity<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>> response =
                    httpTemplate.exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>>() {
                    });
            final ApsApiResponseWrapper<List<ApsChargeStatusResponse>> wrapper = response.getBody();
            assert wrapper != null;
            if (wrapper.isResult()) {
                final List<ApsChargeStatusResponse> body = wrapper.getData();
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

    @Override
    public DefaultPaymentSettlementResponse settle (PaymentGatewaySettlementRequest request) {
        final String settlementOrderId = UUIDs.random().toString();
        final Transaction transaction = TransactionContext.get();
        final PlanDTO purchasedPlan = cache.getPlan(transaction.getPlanId());
        final List<ApsSettlementRequest.OrderDetails> orderDetails = purchasedPlan.getActivationServiceIds().stream()
                .map(serviceId -> ApsSettlementRequest.OrderDetails.builder().serviceOrderId(request.getTid()).serviceId(serviceId)
                        .paymentDetails(ApsSettlementRequest.OrderDetails.PaymentDetails.builder().paymentAmount(Double.toString(transaction.getAmount())).build()).build())
                .collect(Collectors.toList());
        final ApsSettlementRequest settlementRequest = ApsSettlementRequest.builder().channel("DIGITAL_STORE").orderId(settlementOrderId)
                .paymentDetails(ApsSettlementRequest.PaymentDetails.builder().paymentTransactionId(request.getTid()).orderPaymentAmount(transaction.getAmount()).build())
                .serviceOrderDetails(orderDetails).build();
        final HttpHeaders headers = new HttpHeaders();
        final RequestEntity<ApsSettlementRequest> requestEntity = new RequestEntity<>(settlementRequest, headers, HttpMethod.POST, URI.create(SETTLEMENT_ENDPOINT));
        httpTemplate.exchange(requestEntity, String.class);
        return DefaultPaymentSettlementResponse.builder().referenceId(settlementOrderId).build();
    }

    @Override
    public WynkResponseEntity<ApsPaymentRefundResponse> refund (ApsPaymentRefundRequest request) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final Transaction refundTransaction = TransactionContext.get();
        final MerchantTransactionEvent.Builder mBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        final WynkResponseEntity.WynkResponseEntityBuilder<ApsPaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        final ApsPaymentRefundResponse.ApsPaymentRefundResponseBuilder<?, ?> refundResponseBuilder =
                ApsPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId())
                        .itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn())
                        .paymentEvent(refundTransaction.getType());
        try {
            final ApsExternalPaymentRefundRequest refundRequest =
                    ApsExternalPaymentRefundRequest.builder().refundAmount(String.valueOf(refundTransaction.getAmount())).pgId(request.getPgId()).postingId(refundTransaction.getIdStr()).build();
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsExternalPaymentRefundRequest> requestEntity = new RequestEntity<>(refundRequest, headers, HttpMethod.POST, URI.create(REFUND_ENDPOINT));
            final ResponseEntity<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>> response =
                    httpTemplate.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse>>() {
                    });
            final ApsApiResponseWrapper<ApsExternalPaymentRefundStatusResponse> wrapper = response.getBody();
            assert wrapper != null;
            if (!wrapper.isResult()) {
                throw new WynkRuntimeException("Unable to initiate Refund");
            }
            final ApsExternalPaymentRefundStatusResponse body = wrapper.getData();
            mBuilder.request(refundRequest);
            mBuilder.response(body);
            mBuilder.externalTransactionId(body.getRefundId());
            refundResponseBuilder.requestId(body.getRefundId());
            if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_SUCCESS")) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (!StringUtils.isEmpty(body.getRefundStatus()) && body.getRefundStatus().equalsIgnoreCase("REFUND_FAILED")) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (WynkRuntimeException ex) {
            final PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            responseBuilder.success(false).status(ex.getErrorType().getHttpResponseStatusCode())
                    .error(TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(mBuilder.build());
        }
        return responseBuilder.data(refundResponseBuilder.build()).build();
    }
}
