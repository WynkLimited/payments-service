package in.wynk.payment.gateway.aps.verify;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.aps.request.verify.aps.ApsBinVerificationRequest;
import in.wynk.payment.dto.aps.response.verify.ApsBinVerificationResponseData;
import in.wynk.payment.dto.aps.response.verify.ApsVpaVerificationData;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.verify.BinVerificationResponse;
import in.wynk.payment.dto.gateway.verify.VpaVerificationResponse;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.VerificationRequestV2;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import in.wynk.payment.service.IVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static in.wynk.payment.core.constant.PaymentConstants.LOB_AUTO_PAY_REGISTER_WYNK;
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

    private final ApsCommonGateway common;
    private final RestTemplate httpTemplate;
    private final PaymentMethodEligibilityVerification verification = new PaymentMethodEligibilityVerification();

    public ApsVerificationGateway (@Qualifier("apsHttpTemplate") RestTemplate httpTemplate, ApsCommonGateway common) {
        this.httpTemplate = httpTemplate;
        this.common = common;
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

                    ApsBinVerificationResponseData apsBinVerificationResponseData =
                            common.exchange(BIN_VERIFY_ENDPOINT, HttpMethod.POST, binRequest, ApsBinVerificationResponseData.class);

                    return BinVerificationResponse.fromAps(apsBinVerificationResponseData);

                } catch (Exception e) {
                    log.error(APS_BIN_VERIFICATION, "Bin Verification Request failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
            }
        }

        private class VpaVerification implements IVerificationService<AbstractVerificationResponse, VerificationRequestV2> {
            @Override
            public AbstractVerificationResponse verify (VerificationRequestV2 request) {
                String userVpa = request.getVerifyValue();
                String lob = LOB_AUTO_PAY_REGISTER_WYNK;
                final URI uri = httpTemplate.getUriTemplateHandler().expand(VPA_VERIFY_ENDPOINT, userVpa, lob);
                try {
                    final HttpHeaders headers = new HttpHeaders();
                    RequestEntity<VerificationRequestV2> entity = new RequestEntity<>(request, headers, HttpMethod.GET, URI.create(uri.toString()));
                    ApsVpaVerificationData apsVpaVerificationData = common.exchange(uri.toString(), HttpMethod.GET, request, ApsVpaVerificationData.class);

                    return VpaVerificationResponse.fromAps(apsVpaVerificationData);
                } catch (Exception e) {
                    log.error(APS_VPA_VERIFICATION, "Vpa verification failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
            }
        }
    }
}
