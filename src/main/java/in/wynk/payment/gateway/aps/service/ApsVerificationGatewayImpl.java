package in.wynk.payment.gateway.aps.service;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.aps.request.verify.BinVerificationRequest;
import in.wynk.payment.dto.aps.response.verify.BinVerificationResponse;
import in.wynk.payment.dto.aps.response.verify.VpaVerificationResponse;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.AbstractVerificationRequest;
import in.wynk.payment.gateway.IPaymentAccountVerification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_BIN_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.APS_VPA_VERIFICATION;
import static in.wynk.payment.dto.aps.common.ApsConstant.APS_LOB_AUTO_PAY_REGISTER_WYNK;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsVerificationGatewayImpl implements IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> {

    private final String VPA_VERIFY_ENDPOINT;
    private final String BIN_VERIFY_ENDPOINT;

    private final ApsCommonGatewayService common;
    private final RestTemplate httpTemplate;
    private final PaymentMethodEligibilityVerification verification = new PaymentMethodEligibilityVerification();

    public ApsVerificationGatewayImpl(String vpaVerifyEndpoint, String binVerifyEndpoint, RestTemplate httpTemplate, ApsCommonGatewayService common) {
        this.httpTemplate = httpTemplate;
        this.common = common;
        this.VPA_VERIFY_ENDPOINT = vpaVerifyEndpoint;
        this.BIN_VERIFY_ENDPOINT = binVerifyEndpoint;
    }

    @Override
    public AbstractVerificationResponse verify (AbstractVerificationRequest request) {
        return verification.verify(request);
    }

    private class PaymentMethodEligibilityVerification implements IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> {
        private final Map<VerificationType, IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest>> verificationDelegate = new HashMap<>();

        public PaymentMethodEligibilityVerification () {
            verificationDelegate.put(VerificationType.VPA, new VpaVerification());
            verificationDelegate.put(VerificationType.BIN, new BinVerification());//Card verification
        }

        @Override
        public AbstractVerificationResponse verify (AbstractVerificationRequest request) {
            return verificationDelegate.get(request.getVerificationType()).verify(request);
        }

        private class BinVerification implements IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> {
            @Override
            public in.wynk.payment.dto.gateway.verify.BinVerificationResponse verify (AbstractVerificationRequest request) {
                final BinVerificationRequest binRequest = BinVerificationRequest.builder().cardBin(request.getVerifyValue()).lob(APS_LOB_AUTO_PAY_REGISTER_WYNK).build();
                try {
                    BinVerificationResponse
                            apsBinVerificationResponseData = common.exchange(request.getClient(), BIN_VERIFY_ENDPOINT, HttpMethod.POST, common.getLoginId(request.getMsisdn()), binRequest, BinVerificationResponse.class);
                    return in.wynk.payment.dto.gateway.verify.BinVerificationResponse.fromAps(apsBinVerificationResponseData);

                } catch (Exception e) {
                    log.error(APS_BIN_VERIFICATION, "Bin Verification Request failure due to "+ e.getMessage());
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
            }
        }

        private class VpaVerification implements IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> {
            @Override
            public AbstractVerificationResponse verify (AbstractVerificationRequest request) {
                String userVpa = request.getVerifyValue();
                final URI uri = httpTemplate.getUriTemplateHandler().expand(VPA_VERIFY_ENDPOINT, userVpa, APS_LOB_AUTO_PAY_REGISTER_WYNK);
                try {
                    VpaVerificationResponse apsVpaVerificationData = common.exchange(request.getClient(), uri.toString(), HttpMethod.GET, common.getLoginId(request.getMsisdn()), request, VpaVerificationResponse.class);
                    return in.wynk.payment.dto.gateway.verify.VpaVerificationResponse.fromAps(apsVpaVerificationData);
                } catch (Exception e) {
                    log.error(APS_VPA_VERIFICATION, "Vpa verification failure due to ", e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY039, e);
                }
            }
        }
    }
}
