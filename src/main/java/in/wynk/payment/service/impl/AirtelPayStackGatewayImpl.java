package in.wynk.payment.service.impl;

import com.google.gson.Gson;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
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
import in.wynk.payment.dto.aps.charge.ApsExternalChargingRequest;
import in.wynk.payment.dto.aps.charge.ApsUpiIntentChargingResponse;
import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.common.UpiPaymentInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.service.AbstractMerchantPaymentStatusService;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.PaymentCachingService;
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

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        return null;
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
                try {
                    final ResponseEntity<ApsApiResponseWrapper<ApsUpiIntentChargingResponse>> response  = httpTemplate.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsUpiIntentChargingResponse>>() {});
                    if (Objects.nonNull(response.getBody())) {
                        final String encryptedParams = EncryptionUtils.encrypt(gson.toJson(response.getBody().getData().getUpiLink()), encryptionKey);
                        builder.data(ApsChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).info(encryptedParams).build());
                        return builder.build();
                    }
                    throw new WynkRuntimeException(PaymentErrorType.PAY024);
                } catch (Exception e) {
                    final PaymentErrorType errorType = PaymentErrorType.PAY024;
                    builder.error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).status(errorType.getHttpResponseStatusCode()).success(false);
                    log.error(errorType.getMarker(), e.getMessage(), e);
                }
                return builder.build();
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
