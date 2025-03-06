package in.wynk.payment.gateway.aps.service;

import com.datastax.driver.core.utils.UUIDs;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.sattlement.SettlementRequest;
import in.wynk.payment.dto.request.ApsGatewaySettlementRequest;
import in.wynk.payment.dto.response.DefaultPaymentSettlementResponse;
import in.wynk.payment.gateway.IPaymentSettlement;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.subscription.common.dto.PlanDTO;
import static in.wynk.payment.core.constant.BeanConstant.AIRTEL_PAY_STACK;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static in.wynk.payment.dto.aps.common.ApsConstant.*;

@Slf4j
public class ApsPaymentSettlementGateway implements IPaymentSettlement<DefaultPaymentSettlementResponse, ApsGatewaySettlementRequest> {

    private final String SETTLEMENT_ENDPOINT;

    private final RestTemplate httpTemplate;
    private final PaymentCachingService cachingService;


    public ApsPaymentSettlementGateway(String settlementEndpoint, RestTemplate httpTemplate, PaymentCachingService cachingService) {
        this.httpTemplate = httpTemplate;
        this.cachingService = cachingService;
        this.SETTLEMENT_ENDPOINT = settlementEndpoint;
    }

    @SneakyThrows
    @PostConstruct
    private void init() {
        this.httpTemplate.getInterceptors().add((request, body, execution) -> {
            final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT);
            final String username = PropertyResolverUtils.resolve(clientAlias, AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_ID);
            final String password = PropertyResolverUtils.resolve(clientAlias, AIRTEL_PAY_STACK, PaymentConstants.MERCHANT_SECRET);
            final String token = AuthSchemes.BASIC + " " + Base64.getEncoder().encodeToString((username + HttpConstant.COLON + password).getBytes(StandardCharsets.UTF_8));
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, token);
            request.getHeaders().add(CHANNEL_ID, AUTH_TYPE_WEB_UNAUTH);
            request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            return execution.execute(request, body);
        });
    }

    @Override
    public DefaultPaymentSettlementResponse settle(ApsGatewaySettlementRequest request) {
        final String settlementOrderId = UUIDs.random().toString();
        final Transaction transaction = TransactionContext.get();
        final PlanDTO purchasedPlan = cachingService.getPlan(transaction.getPlanId());
        final List<SettlementRequest.OrderDetails> orderDetails = purchasedPlan.getActivationServiceIds().stream()
                .map(serviceId -> SettlementRequest.OrderDetails.builder().serviceOrderId(request.getTid()).serviceId(serviceId)
                        .paymentDetails(SettlementRequest.OrderDetails.PaymentDetails.builder().paymentAmount(Double.toString(transaction.getAmount())).build()).build())
                .collect(Collectors.toList());
        final SettlementRequest settlementRequest = SettlementRequest.builder().channel("DIGITAL_STORE").orderId(settlementOrderId)
                .paymentDetails(SettlementRequest.PaymentDetails.builder().paymentTransactionId(request.getTid()).orderPaymentAmount(transaction.getAmount()).build())
                .serviceOrderDetails(orderDetails).build();
        final HttpHeaders headers = new HttpHeaders();
        final RequestEntity<SettlementRequest> requestEntity = new RequestEntity<>(settlementRequest, headers, HttpMethod.POST, URI.create(SETTLEMENT_ENDPOINT));
        httpTemplate.exchange(requestEntity, String.class);
        return DefaultPaymentSettlementResponse.builder().referenceId(settlementOrderId).build();
    }
}
