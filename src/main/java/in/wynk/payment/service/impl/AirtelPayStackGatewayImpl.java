package in.wynk.payment.service.impl;

import com.google.gson.Gson;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.ApsChargingRequest;
import in.wynk.payment.dto.ApsChargingResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.charge.AbstractApsExternalChargingResponse;
import in.wynk.payment.dto.aps.charge.ApsExternalChargingRequest;
import in.wynk.payment.dto.aps.charge.ApsUpiIntentChargingChargingResponse;
import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.common.UpiPaymentInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
import in.wynk.payment.dto.aps.status.charge.ApsChargeStatusResponse;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.service.AbstractMerchantPaymentStatusService;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.PaymentCachingService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service("APS")
public class AirtelPayStackGatewayImpl extends AbstractMerchantPaymentStatusService implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

    @Value("${payment.encKey}")
    private String encryptionKey;
    @Value("${aps.payment.refund.init.api}")
    private String REFUND_ENDPOINT;
    @Value("${aps.payment.init.api}")
    private String CHARGING_ENDPOINT;
    @Value("${aps.payment.settlement.api}")
    private String SETTLEMENT_ENDPOINT;
    @Value("${aps.bin.verify.api}")
    private String BIN_VERIFY_ENDPOINT;
    @Value("${aps.payment.option.api}")
    private String PAYMENT_OPTION_ENDPOINT;
    @Value("${aps.saved.details.api}")
    private String SAVED_DETAILS_ENDPOINT;
    @Value("${aps.payment.refund.status.api}")
    private String REFUND_STATUS_ENDPOINT;
    @Value("${aps.payment.status.api}")
    private String CHARGING_STATUS_ENDPOINT;

    private final Gson gson;
    private final RestTemplate httpTemplate;
    private final PaymentCachingService cache;
    private final Map<String, IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>>> chargingDelegate = new HashMap<>();

    public AirtelPayStackGatewayImpl(Gson gson, @Qualifier("apsHttpTemplate") RestTemplate httpTemplate, PaymentCachingService cache, IErrorCodesCacheService errorCache) {
        super(cache, errorCache);
        this.gson = gson;
        this.cache = cache;
        this.httpTemplate = httpTemplate;
        chargingDelegate.put("UPI", new UpiCharging());
        chargingDelegate.put("CARD", new CardCharging());
        chargingDelegate.put("NET_BANKING", new NetBankingCharging());
    }

    @Override
    public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
        final PaymentMethod method = cache.getPaymentMethod(request.getPaymentId());
        final String paymentGroup = method.getGroup();
        return chargingDelegate.get(paymentGroup).charge(request);
    }

    private <R extends AbstractApsExternalChargingResponse, T> ResponseEntity<ApsApiResponseWrapper<R>> exchange(RequestEntity<T> entity) {
        try {
            return httpTemplate.exchange(entity, new ParameterizedTypeReference<ApsApiResponseWrapper<R>>() {
            });
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY024, e);
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest request) {
        final Transaction transaction = TransactionContext.get();
        final String txnId = request.getTransactionId();
        final boolean fetchHistoryTransaction = false;
        ApsChargeStatusResponse  response = httpTemplate.getForObject(CHARGING_STATUS_ENDPOINT, ApsChargeStatusResponse.class, txnId, fetchHistoryTransaction);
        if (response.getPaymentStatus().equalsIgnoreCase("PAYMENT_SUCCESS")) {
            transaction.setStatus(TransactionStatus.SUCCESS.getValue());
        } else if (response.getPaymentStatus().equalsIgnoreCase("PAYMENT_FAILED")) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
        }
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(ChargingStatusResponse.success(transaction.getIdStr(), -1L, transaction.getPlanId())).build();
    }

    private static class NetBankingCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

        @Override
        public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {

            return null;
        }

    }

    private static class CardCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

        @Override
        public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
            return null;
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
                final UpiPaymentInfo.UpiIntentDetails upiIntentDetails = UpiPaymentInfo.UpiIntentDetails.builder().upiApp(payAppName).build();
                final UpiPaymentInfo<UpiPaymentInfo.UpiIntentDetails> upiPaymentInfo = UpiPaymentInfo.<UpiPaymentInfo.UpiIntentDetails>builder().upiDetails(upiIntentDetails).build();
                final ApsExternalChargingRequest<UpiPaymentInfo<UpiPaymentInfo.UpiIntentDetails>> payRequest = ApsExternalChargingRequest.<UpiPaymentInfo<UpiPaymentInfo.UpiIntentDetails>>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(upiPaymentInfo).build();
                final HttpHeaders headers = new HttpHeaders();
                final RequestEntity<ApsExternalChargingRequest<UpiPaymentInfo<UpiPaymentInfo.UpiIntentDetails>>> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(CHARGING_ENDPOINT));
                final ResponseEntity<ApsApiResponseWrapper<ApsUpiIntentChargingChargingResponse>> response = exchange(requestEntity);
                if (Objects.nonNull(response.getBody())) {
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
                throw new WynkRuntimeException("Method is not implemented");
            }
        }

    }

}
