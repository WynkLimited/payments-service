package in.wynk.payment.gateway.aps.service;

import in.wynk.common.dto.SessionDTO;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.aps.request.verify.BinVerificationRequest;
import in.wynk.payment.dto.aps.response.verify.BinVerificationResponse;
import in.wynk.payment.dto.aps.response.verify.VpaVerificationResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.service.IVerificationService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.WYNK;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_BIN_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_VPA_VERIFICATION;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsVerificationGatewayService implements IVerificationService<AbstractVerificationResponse, VerificationRequest> {

    private String VPA_VERIFY_ENDPOINT;
    private String BIN_VERIFY_ENDPOINT;

    private final ApsCommonGatewayService common;
    private final RestTemplate httpTemplate;
    private final PaymentMethodEligibilityVerification verification = new PaymentMethodEligibilityVerification();

    public ApsVerificationGatewayService (String vpaVerifyEndpoint, String binVerifyEndpoint, RestTemplate httpTemplate, ApsCommonGatewayService common) {
        this.httpTemplate = httpTemplate;
        this.common = common;
        this.VPA_VERIFY_ENDPOINT = vpaVerifyEndpoint;
        this.BIN_VERIFY_ENDPOINT = binVerifyEndpoint;
    }

    @Override
    public AbstractVerificationResponse verify (VerificationRequest request) {
        return verification.verify(request);
    }

    private class PaymentMethodEligibilityVerification implements IVerificationService<AbstractVerificationResponse, VerificationRequest> {
        private final Map<VerificationType, IVerificationService<AbstractVerificationResponse, VerificationRequest>> verificationDelegate = new HashMap<>();

        public PaymentMethodEligibilityVerification () {
            verificationDelegate.put(VerificationType.VPA, new VpaVerification());
            verificationDelegate.put(VerificationType.BIN, new BinVerification());//Card verification
        }

        @Override
        public AbstractVerificationResponse verify (VerificationRequest request) {
            return verificationDelegate.get(request.getVerificationType()).verify(request);
        }

        private class BinVerification implements IVerificationService<AbstractVerificationResponse, VerificationRequest> {
            @Override
            public in.wynk.payment.dto.gateway.verify.BinVerificationResponse verify (VerificationRequest request) {
                final BinVerificationRequest binRequest = BinVerificationRequest.builder().cardBin(request.getVerifyValue()).build();
                final SessionDTO sessionDTO = SessionContextHolder.getBody();
                try {
                    BinVerificationResponse
                            apsBinVerificationResponseData = common.exchange(BIN_VERIFY_ENDPOINT, HttpMethod.POST, common.getLoginId(sessionDTO.get("msisdn")), binRequest, BinVerificationResponse.class);
                    return in.wynk.payment.dto.gateway.verify.BinVerificationResponse.fromAps(apsBinVerificationResponseData);

                } catch (Exception e) {
                    log.error(APS_BIN_VERIFICATION, "Bin Verification Request failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
            }
        }

        private class VpaVerification implements IVerificationService<AbstractVerificationResponse, VerificationRequest> {
            @Override
            public AbstractVerificationResponse verify (VerificationRequest request) {
                String userVpa = request.getVerifyValue();
                String lob = WYNK;
                final URI uri = httpTemplate.getUriTemplateHandler().expand(VPA_VERIFY_ENDPOINT, userVpa, lob);
                final SessionDTO sessionDTO = SessionContextHolder.getBody();
                try {
                    VpaVerificationResponse apsVpaVerificationData = common.exchange(uri.toString(), HttpMethod.GET, common.getLoginId(sessionDTO.get("msisdn")), request, VpaVerificationResponse.class);
                    return in.wynk.payment.dto.gateway.verify.VpaVerificationResponse.fromAps(apsVpaVerificationData);
                } catch (Exception e) {
                    log.error(APS_VPA_VERIFICATION, "Vpa verification failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
            }
        }
    }
}
