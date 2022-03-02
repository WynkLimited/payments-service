package in.wynk.payment.service.impl;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.ApsChargingRequest;
import in.wynk.payment.dto.ApsChargingResponse;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.charge.ApsExternalChargingRequest;
import in.wynk.payment.dto.aps.charge.ApsExternalChargingResponse;
import in.wynk.payment.dto.aps.common.ApsApiResponseWrapper;
import in.wynk.payment.dto.aps.common.UpiPaymentInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.PaymentCachingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service("APS")
public class AirtelPayStackGatewayImpl implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

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

    private final RestTemplate httpTemplate;
    private final PaymentCachingService cache;
    private Map<String, IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>>> chargingDelegate = new HashMap<>();

    public AirtelPayStackGatewayImpl(@Qualifier("apsHttpTemplate") RestTemplate httpTemplate, PaymentCachingService cache) {
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
            upiDelegate.put("INTENT", new UpiIntentCharging());
            upiDelegate.put("COLLECT", new UpiCollectCharging());
        }

        @Override
        public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
            final PaymentMethod method = cache.getPaymentMethod(request.getPaymentId());
            final String flowType = method.getFlowType();
            return upiDelegate.get(flowType).charge(request);
        }

        private class UpiIntentCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

            @Override
            public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
                final Transaction transaction = TransactionContext.get();
                final PaymentMethod method = cache.getPaymentMethod(request.getPaymentId());
                final String payAppName = (String) method.getMeta().get(PaymentConstants.PAY_APP_NAME);
                final UserInfo userInfo = UserInfo.builder().loginId(request.getMsisdn()).build();
                final UpiPaymentInfo.UpiIntentDetails upiIntentDetails = UpiPaymentInfo.UpiIntentDetails.builder().upiApp(payAppName).build();
                final UpiPaymentInfo upiPaymentInfo = UpiPaymentInfo.<UpiPaymentInfo.UpiIntentDetails>builder().upiDetails(upiIntentDetails).build();
                final ApsExternalChargingRequest payRequest = ApsExternalChargingRequest.<UpiPaymentInfo<UpiPaymentInfo.UpiIntentDetails>>builder().userInfo(userInfo).orderId(transaction.getIdStr()).paymentInfo(upiPaymentInfo).build();
                final HttpHeaders headers = new HttpHeaders();
                final RequestEntity<ApsExternalChargingRequest> requestEntity = new RequestEntity<>(payRequest, headers, HttpMethod.POST, URI.create(CHARGING_ENDPOINT));
                final ResponseEntity<ApsApiResponseWrapper<ApsExternalChargingResponse>> response  = httpTemplate.exchange(requestEntity, new ParameterizedTypeReference<ApsApiResponseWrapper<ApsExternalChargingResponse>>() {});
                return null;
            }

        }

        private class UpiCollectCharging implements IMerchantPaymentChargingService<ApsChargingResponse, ApsChargingRequest<?>> {

            @Override
            public WynkResponseEntity<ApsChargingResponse> charge(ApsChargingRequest<?> request) {
                return null;
            }

        }

    }

}
