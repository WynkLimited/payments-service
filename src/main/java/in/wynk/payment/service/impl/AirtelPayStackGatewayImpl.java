package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.google.gson.Gson;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.ApsChargingRequest;
import in.wynk.payment.dto.ApsChargingResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.*;
import in.wynk.payment.dto.aps.request.bin.ApsBinVerificationRequest;
import in.wynk.payment.dto.aps.request.sattlement.ApsSettlementRequest;
import in.wynk.payment.dto.aps.response.bin.ApsBinVerificationResponse;
import in.wynk.payment.dto.aps.response.bin.ApsVpaVerificationResponse;
import in.wynk.payment.dto.aps.response.charge.*;
import in.wynk.payment.dto.aps.request.charge.ApsExternalChargingRequest;
import in.wynk.payment.dto.aps.response.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.PaymentGatewaySettlementRequest;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.DefaultPaymentSettlementResponse;
import in.wynk.payment.dto.response.IVerificationResponse;
import in.wynk.payment.dto.response.payu.PayUVpaVerificationResponse;
import in.wynk.payment.service.*;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service("APS")
public class AirtelPayStackGatewayImpl extends AbstractMerchantPaymentStatusService implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>>, IMerchantPaymentSettlement<DefaultPaymentSettlementResponse, PaymentGatewaySettlementRequest>, IMerchantVerificationService {

    @Value("${aps.auth.api.username}")
    private String username;
    @Value("${aps.auth.api.password}")
    private String password;
    @Value("${payment.encKey}")
    private String encryptionKey;
    @Value("${aps.payment.init.refund.api}")
    private String REFUND_ENDPOINT;
    @Value("${aps.payment.init.charge.api}")
    private String CHARGING_ENDPOINT;
    @Value("${aps.payment.init.charge.upi.api}")
    private String UPI_CHARGING_ENDPOINT;
    @Value("${aps.payment.init.settlement.api}")
    private String SETTLEMENT_ENDPOINT;
    @Value("${aps.payment.verify.bin.api}")
    private String BIN_VERIFY_ENDPOINT;
    @Value("${aps.payment.verify.vpa.api}")
    private String VPA_VERIFY_ENDPOINT;
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

    private final Gson gson;
    private final RestTemplate httpTemplate;
    private final PaymentCachingService cache;
    private final ApplicationEventPublisher eventPublisher;
    private final VerificationWrapper verificationWrapper = new VerificationWrapper();
    private final Map<String, IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>>> chargingDelegate = new HashMap<>();

    public AirtelPayStackGatewayImpl(Gson gson, @Qualifier("apsHttpTemplate") RestTemplate httpTemplate, PaymentCachingService cache, IErrorCodesCacheService errorCache, ApplicationEventPublisher eventPublisher) {
        super(cache, errorCache);
        this.gson = gson;
        this.cache = cache;
        this.httpTemplate = httpTemplate;
        this.eventPublisher = eventPublisher;
        chargingDelegate.put("UPI", new UpiCharging());
        chargingDelegate.put("CARD", new CardCharging());
        chargingDelegate.put("NET_BANKING", new NetBankingCharging());
    }

    @PostConstruct
    private void init() {
        final String token = AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
        this.httpTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, token);
            request.getHeaders().add("channel-id", "WEB_UNAUTH");
            return execution.execute(request, body);
        });
    }

    @Override
    public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
        final PaymentMethod method = cache.getPaymentMethod(request.getPaymentId());
        return chargingDelegate.get(method.getGroup().toUpperCase()).charge(request);
    }

    private <R extends AbstractApsExternalChargingResponse, T> ResponseEntity<ApsApiResponseWrapper<R>> exchange(RequestEntity<T> entity, ParameterizedTypeReference<ApsApiResponseWrapper<R>> target) {
        try {
            return httpTemplate.exchange(entity, target);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY024, e);
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        this.syncChargingTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(ChargingStatusResponse.success(transaction.getIdStr(), cache.validTillDate(transaction.getPlanId()), transaction.getPlanId())).build();
        } else if (transaction.getStatus() == TransactionStatus.FAILURE) {
            return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(ChargingStatusResponse.failure(transaction.getIdStr(), transaction.getPlanId())).build();
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY025);
    }

    public void syncChargingTransactionFromSource(Transaction transaction) {
        final String txnId = transaction.getIdStr();
        final boolean fetchHistoryTransaction = false;
        final MerchantTransactionEvent.Builder builder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final URI uri = httpTemplate.getUriTemplateHandler().expand(CHARGING_STATUS_ENDPOINT, txnId, fetchHistoryTransaction);
            final ResponseEntity<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>> response = httpTemplate.exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<ApsApiResponseWrapper<List<ApsChargeStatusResponse>>>() {
            });
            final ApsApiResponseWrapper<List<ApsChargeStatusResponse>> wrapper = response.getBody();
            assert wrapper != null;
            if (wrapper.isResult()) {
                final List<ApsChargeStatusResponse> body =  wrapper.getData();
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
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.APS_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE) {
                eventPublisher.publishEvent(builder.build());
            }
        }
    }

    @Override
    public DefaultPaymentSettlementResponse settle(PaymentGatewaySettlementRequest request) {
        final String settlementOrderId = UUIDs.random().toString();
        final Transaction transaction = TransactionContext.get();
        final PlanDTO purchasedPlan = cache.getPlan(transaction.getPlanId());
        final List<ApsSettlementRequest.OrderDetails> orderDetails = purchasedPlan.getActivationServiceIds().stream().map(serviceId -> ApsSettlementRequest.OrderDetails.builder().serviceOrderId(request.getTid()).serviceId(serviceId).paymentDetails(ApsSettlementRequest.OrderDetails.PaymentDetails.builder().paymentAmount(Double.toString(transaction.getAmount())).build()).build()).collect(Collectors.toList());
        final ApsSettlementRequest settlementRequest = ApsSettlementRequest.builder().channel("DIGITAL_STORE").orderId(settlementOrderId).paymentDetails(ApsSettlementRequest.PaymentDetails.builder().paymentTransactionId(request.getTid()).orderPaymentAmount(transaction.getAmount()).build()).serviceOrderDetails(orderDetails).build();
        final HttpHeaders headers = new HttpHeaders();
        final RequestEntity<ApsSettlementRequest> requestEntity = new RequestEntity<>(settlementRequest, headers, HttpMethod.POST, URI.create(SETTLEMENT_ENDPOINT));
        final ResponseEntity<String> response = httpTemplate.exchange(requestEntity, String.class);
        return DefaultPaymentSettlementResponse.builder().referenceId(settlementOrderId).build();
    }

    @Override
    public WynkResponseEntity<IVerificationResponse> doVerify(VerificationRequest request) {
        return verificationWrapper.doVerify(request);
    }

    private class VerificationWrapper implements IMerchantVerificationService {

        private final Map<VerificationType, IMerchantVerificationService> verificationHolder = new HashMap<>();

        public VerificationWrapper() {
            verificationHolder.put(VerificationType.VPA, new VpaVerification());
            verificationHolder.put(VerificationType.BIN, new CardVerification());
        }

        @Override
        public WynkResponseEntity<IVerificationResponse> doVerify(VerificationRequest request) {
            return verificationHolder.get(request.getVerificationType()).doVerify(request);
        }

        private class CardVerification implements IMerchantVerificationService {
            @Override
            public WynkResponseEntity<IVerificationResponse> doVerify(VerificationRequest request) {
                final WynkResponseEntity.WynkResponseEntityBuilder<IVerificationResponse> builder = WynkResponseEntity.<IVerificationResponse>builder();
                final ApsBinVerificationRequest binRequest = ApsBinVerificationRequest.builder().cardBin(request.getVerifyValue()).build();
                final RequestEntity<ApsBinVerificationRequest> entity = new RequestEntity<>(binRequest, new HttpHeaders(), HttpMethod.POST, URI.create(BIN_VERIFY_ENDPOINT));
                try {
                    final ResponseEntity<ApsApiResponseWrapper<ApsBinVerificationResponse>> response = httpTemplate.exchange(entity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsBinVerificationResponse>>() {
                    });
                    final ApsApiResponseWrapper<ApsBinVerificationResponse> wrapper = response.getBody();
                    if (!wrapper.isResult()) throw new WynkRuntimeException("Bin Verification Request failure");
                    final ApsBinVerificationResponse body = wrapper.getData();
                    builder.data(PayUCardInfo.builder().cardCategory(body.getCardCategory()).cardType(body.getCardNetwork()).issuingBank(body.getBankCode()).autoRenewSupported(body.isAutoPayEnable()).isDomestic(body.isDomestic() ? "1" : "0").build());
                } catch (Exception e) {
                    log.error(PaymentLoggingMarker.APS_BIN_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
                    builder.data(PayUCardInfo.builder().valid(Boolean.FALSE).isDomestic(BaseConstants.UNKNOWN.toUpperCase()).cardType(BaseConstants.UNKNOWN.toUpperCase()).cardCategory(BaseConstants.UNKNOWN.toUpperCase()).build());
                }
                return builder.build();
            }
        }

        private class VpaVerification implements IMerchantVerificationService {
            @Override
            public WynkResponseEntity<IVerificationResponse> doVerify(VerificationRequest request) {
                final WynkResponseEntity.WynkResponseEntityBuilder<IVerificationResponse> builder = WynkResponseEntity.<IVerificationResponse>builder();
                try {
                    final ResponseEntity<ApsApiResponseWrapper<ApsVpaVerificationResponse>> wrapper = httpTemplate.exchange(VPA_VERIFY_ENDPOINT.replace("{vpa}", request.getVerifyValue()), HttpMethod.GET, null, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsVpaVerificationResponse>>() {
                    });
                    final ApsApiResponseWrapper<ApsVpaVerificationResponse> response = wrapper.getBody();
                    if (!response.isResult()) throw new WynkRuntimeException("Vpa verification failure");
                    final ApsVpaVerificationResponse body = response.getData();
                    builder.data(PayUVpaVerificationResponse.builder().isValid(body.isVpaValid()).isVPAValid(body.isVpaValid() ? 1 : 0).vpa(body.getVpa()).payerAccountName(body.getPayeeAccountName()).status(body.getStatus()).build());
                } catch (Exception e) {
                    log.error(PaymentLoggingMarker.APS_VPA_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
                    builder.data(PayUVpaVerificationResponse.builder().isValid(false).build());
                }
                return builder.build();
            }
        }

    }


    private class NetBankingCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

        @Override
        public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
            final WynkResponseEntity.WynkResponseEntityBuilder<ApsChargingResponse> builder = WynkResponseEntity.builder();
            final Transaction transaction = TransactionContext.get();
            final UserInfo userInfo = UserInfo.builder().loginId(request.getMsisdn()).build();
            // TODO: get the card info and card type, bank code etc from request
            final NetBankingPaymentInfo netBankingInfo = NetBankingPaymentInfo.builder().bankCode("UTIB").build();
            final ApsExternalChargingRequest<NetBankingPaymentInfo> payRequest = ApsExternalChargingRequest.<NetBankingPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(netBankingInfo).build();
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsExternalChargingRequest<NetBankingPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(CHARGING_ENDPOINT));
            final ResponseEntity<ApsApiResponseWrapper<ApsNetBankingChargingResponse>> response = exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsNetBankingChargingResponse>>() {
            });
            if (Objects.nonNull(response.getBody()) && response.getBody().isResult()) {
                final ApsNetBankingChargingResponse chargingResponse = response.getBody().getData();
                builder.data(ApsChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).info(chargingResponse.getForm()).build());
                return builder.build();
            }
            throw new WynkRuntimeException(PaymentErrorType.PAY024);
        }
    }

    private class CardCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

        @Override
        public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
            final WynkResponseEntity.WynkResponseEntityBuilder<ApsChargingResponse> builder = WynkResponseEntity.builder();
            final Transaction transaction = TransactionContext.get();
            final UserInfo userInfo = UserInfo.builder().loginId(request.getMsisdn()).build();
            final String encCardInfo = "";
            // TODO: get the card info and card type, bank code etc from request
            final FreshCardPaymentInfo freshCardInfo = FreshCardPaymentInfo.builder().cardDetails(encCardInfo).bankCode("UTIB").paymentMode("CREDIT_CARD").build();
            final ApsExternalChargingRequest<FreshCardPaymentInfo> payRequest = ApsExternalChargingRequest.<FreshCardPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(freshCardInfo).build();
            final HttpHeaders headers = new HttpHeaders();
            final RequestEntity<ApsExternalChargingRequest<FreshCardPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(CHARGING_ENDPOINT));
            final ResponseEntity<ApsApiResponseWrapper<ApsCardChargingResponse>> response = exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsCardChargingResponse>>() {
            });
            if (Objects.nonNull(response.getBody()) && response.getBody().isResult()) {
                final ApsCardChargingResponse cardChargingResponse = response.getBody().getData();
                builder.data(ApsChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).info(cardChargingResponse.getForm()).build());
                return builder.build();
            }
            throw new WynkRuntimeException(PaymentErrorType.PAY024);
        }
    }

    private class UpiCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

        private final Map<String, IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>>> upiDelegate = new HashMap<>();

        public UpiCharging() {
            upiDelegate.put("SEAMLESS", new UpiSeamlessCharging());
            upiDelegate.put("NON_SEAMLESS", new UpiNonSeamlessCharging());
        }

        @Override
        public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
            final PaymentMethod method = cache.getPaymentMethod(request.getPaymentId());
            final String flowType = method.getFlowType();
            return upiDelegate.get(flowType).charge(request);
        }

        private class UpiSeamlessCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

            @Override
            @SneakyThrows
            public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
                final WynkResponseEntity.WynkResponseEntityBuilder<ApsChargingResponse> builder = WynkResponseEntity.builder();
                final Transaction transaction = TransactionContext.get();
                final PaymentMethod method = cache.getPaymentMethod(request.getPaymentId());
                final String payAppName = (String) method.getMeta().get(PaymentConstants.APP_NAME);
                final UserInfo userInfo = UserInfo.builder().loginId(request.getMsisdn()).build();
                final IntentUpiPaymentInfo upiIntentDetails = IntentUpiPaymentInfo.builder().upiDetails(IntentUpiPaymentInfo.UpiDetails.builder().appName(payAppName).build()).paymentAmount(transaction.getAmount()).build();
                final ApsExternalChargingRequest<IntentUpiPaymentInfo> payRequest = ApsExternalChargingRequest.<IntentUpiPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(upiIntentDetails).channelInfo(ChannelInfo.builder().redirectionUrl(CLIENT_POLLING_SCREEN_URL).build()).build();
                final HttpHeaders headers = new HttpHeaders();
                final RequestEntity<ApsExternalChargingRequest<IntentUpiPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(UPI_CHARGING_ENDPOINT));
                final ResponseEntity<ApsApiResponseWrapper<ApsUpiIntentChargingChargingResponse>> response = exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsUpiIntentChargingChargingResponse>>() {
                });
                if (Objects.nonNull(response.getBody()) && response.getBody().isResult()) {
                    final String encryptedParams = EncryptionUtils.encrypt(gson.toJson(response.getBody().getData().getUpiLink()), encryptionKey);
                    builder.data(ApsChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).info(encryptedParams).build());
                    return builder.build();
                }
                throw new WynkRuntimeException(PaymentErrorType.PAY024);
            }
        }

        private class UpiNonSeamlessCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

            @Override
            public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
                final WynkResponseEntity.WynkResponseEntityBuilder<ApsChargingResponse> builder = WynkResponseEntity.builder();
                final Transaction transaction = TransactionContext.get();
                final UserInfo userInfo = UserInfo.builder().loginId(request.getMsisdn()).build();
                final CollectUpiPaymentInfo upiCollectInfo = CollectUpiPaymentInfo.builder().vpa(request.getVpa()).build();
                final ApsExternalChargingRequest<CollectUpiPaymentInfo> payRequest = ApsExternalChargingRequest.<CollectUpiPaymentInfo>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(upiCollectInfo).build();
                final HttpHeaders headers = new HttpHeaders();
                final RequestEntity<ApsExternalChargingRequest<CollectUpiPaymentInfo>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(CHARGING_ENDPOINT));
                final ResponseEntity<ApsApiResponseWrapper<ApsUpiCollectChargingResponse>> response = exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsUpiCollectChargingResponse>>() {
                });
                if (Objects.nonNull(response.getBody()) && response.getBody().isResult()) {
                    builder.data(ApsChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).info(CLIENT_POLLING_SCREEN_URL).build());
                    return builder.build();
                }
                throw new WynkRuntimeException(PaymentErrorType.PAY024);
            }
        }
    }

}
