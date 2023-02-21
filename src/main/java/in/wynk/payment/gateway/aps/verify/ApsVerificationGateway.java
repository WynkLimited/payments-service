package in.wynk.payment.gateway.aps.verify;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.constant.HttpConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.aps.request.verify.aps.ApsBinVerificationRequest;
import in.wynk.payment.dto.aps.response.verify.ApsBinVerificationResponseData;
import in.wynk.payment.dto.aps.response.verify.ApsVpaVerificationData;
import in.wynk.payment.dto.aps.verify.ApsCardVerificationResponseWrapper;
import in.wynk.payment.dto.aps.verify.ApsVpaVerificationResponseWrapper;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.verify.BinVerificationResponse;
import in.wynk.payment.dto.gateway.verify.VpaVerificationResponse;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.VerificationRequestV2;
import in.wynk.payment.service.IVerificationService;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.AuthSchemes;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_BIN_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_VPA_VERIFICATION;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(PaymentConstants.AIRTEL_PAY_STACK_VERIFY)
public class ApsVerificationGateway implements IVerificationService<AbstractVerificationResponse, VerificationRequestV2> {

    @Value("${aps.payment.verify.vpa.api}")
    private String VPA_VERIFY_ENDPOINT;
    @Value("${aps.payment.verify.bin.api}")
    private String BIN_VERIFY_ENDPOINT;

    private final RestTemplate httpTemplate;
    private final PaymentMethodEligibilityVerification verification = new PaymentMethodEligibilityVerification();

    public ApsVerificationGateway (@Qualifier("apsHttpTemplate") RestTemplate httpTemplate) {
        this.httpTemplate = httpTemplate;
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
    public AbstractVerificationResponse verify (VerificationRequestV2 request) {
        return verification.verify(request);
    }

    private class PaymentMethodEligibilityVerification implements IVerificationService<AbstractVerificationResponse, VerificationRequestV2> {
        private final Map<VerificationType, IVerificationService<AbstractVerificationResponse, VerificationRequestV2>> verificationDelegate = new HashMap<>();

        public PaymentMethodEligibilityVerification () {
            verificationDelegate.put(VerificationType.VPA, new VpaVerification());
            verificationDelegate.put(VerificationType.BIN, new BinVerification());//Card verification
        }

        @Override
        public AbstractVerificationResponse verify (VerificationRequestV2 request) {
            return verificationDelegate.get(request.getVerificationType()).verify(request);
        }

        private class BinVerification implements IVerificationService<AbstractVerificationResponse, VerificationRequestV2> {
            @Override
            public BinVerificationResponse verify (VerificationRequestV2 request) {
                final ApsBinVerificationRequest binRequest = ApsBinVerificationRequest.builder().cardBin(request.getVerifyValue()).build();
                final RequestEntity<ApsBinVerificationRequest> entity = new RequestEntity<>(binRequest, new HttpHeaders(), HttpMethod.POST, URI.create(BIN_VERIFY_ENDPOINT));
                try {
                    final ResponseEntity<ApsCardVerificationResponseWrapper<ApsBinVerificationResponseData>> response =
                            httpTemplate.exchange(entity, new ParameterizedTypeReference<ApsCardVerificationResponseWrapper<ApsBinVerificationResponseData>>() {
                            });
                    final ApsCardVerificationResponseWrapper<ApsBinVerificationResponseData> wrapper = response.getBody();
                    if (Objects.nonNull(wrapper) && wrapper.isResult()) {
                        final ApsBinVerificationResponseData body = wrapper.getData();
                        return BinVerificationResponse.builder().cardCategory(body.getCardCategory()).cardType(body.getCardNetwork()).issuingBank(body.getBankCode())
                                .autoRenewSupported(body.isAutoPayEnable()).build();
                    }
                } catch (Exception e) {
                    log.error(APS_BIN_VERIFICATION, "Bin Verification Request failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
                throw new WynkRuntimeException(PaymentErrorType.PAY039);
            }
        }

        private class VpaVerification implements IVerificationService<AbstractVerificationResponse, VerificationRequestV2> {
            @Override
            public AbstractVerificationResponse verify (VerificationRequestV2 request) {
                String userVpa = request.getVerifyValue();
                String lob = WYNK;
                final URI uri = httpTemplate.getUriTemplateHandler().expand(VPA_VERIFY_ENDPOINT, userVpa, lob);
                try {
                    final ResponseEntity<ApsVpaVerificationResponseWrapper<ApsVpaVerificationData>> wrapper =
                            httpTemplate.exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<ApsVpaVerificationResponseWrapper<ApsVpaVerificationData>>() {
                            });
                    final ApsVpaVerificationResponseWrapper<ApsVpaVerificationData> response = wrapper.getBody();

                    if (Objects.nonNull(response) && response.isResult()) {
                        ApsVpaVerificationData body = response.getData();
                        return VpaVerificationResponse.builder().verifyValue(request.getVerifyValue()).verificationType(VerificationType.VPA).autoRenewSupported(response.isAutoPayHandleValid())
                                .vpa(response.getVpa()).payerAccountName(response.getPayeeAccountName())
                                .valid(response.isVpaValid())
                                .build();
                    }
                } catch (Exception e) {
                    log.error(APS_VPA_VERIFICATION, "Vpa verification failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
                throw new WynkRuntimeException(PaymentErrorType.PAY039);
            }
        }
    }
}
