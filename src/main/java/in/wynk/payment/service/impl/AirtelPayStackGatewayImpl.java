package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.google.common.util.concurrent.RateLimiter;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.sattlement.ApsSettlementRequest;
import in.wynk.payment.dto.request.PaymentGatewaySettlementRequest;
import in.wynk.payment.dto.response.DefaultPaymentSettlementResponse;
import in.wynk.payment.service.IMerchantPaymentSettlement;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service(PaymentConstants.AIRTEL_PAY_STACK)
public class AirtelPayStackGatewayImpl implements IMerchantPaymentSettlement<DefaultPaymentSettlementResponse, PaymentGatewaySettlementRequest> {

    @Value("${aps.payment.init.settlement.api}")
    private String SETTLEMENT_ENDPOINT;
    @Value("${aps.payment.saved.details.api}")
    private String SAVED_DETAILS_ENDPOINT;

    private final RestTemplate httpTemplate;
    private final PaymentMethodCachingService paymentMethodCachingService;
    private final PaymentCachingService cachingService;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);


    public AirtelPayStackGatewayImpl (@Qualifier("apsHttpTemplate") RestTemplate httpTemplate, PaymentCachingService cache, IErrorCodesCacheService errorCache,
                                      PaymentMethodCachingService paymentMethodCachingService, PaymentCachingService cachingService) {
        this.httpTemplate = httpTemplate;
        this.paymentMethodCachingService = paymentMethodCachingService;
        this.cachingService = cachingService;
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

    @Override
    public DefaultPaymentSettlementResponse settle (PaymentGatewaySettlementRequest request) {
        final String settlementOrderId = UUIDs.random().toString();
        final Transaction transaction = TransactionContext.get();
        final PlanDTO purchasedPlan = cachingService.getPlan(transaction.getPlanId());
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
}
