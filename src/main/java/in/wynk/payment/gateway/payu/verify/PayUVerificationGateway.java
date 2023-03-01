package in.wynk.payment.gateway.payu.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.verify.BinVerificationResponse;
import in.wynk.payment.dto.gateway.verify.VpaVerificationResponse;
import in.wynk.payment.dto.payu.PayUBinWrapper;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.VerificationRequestV2;
import in.wynk.payment.dto.response.payu.PayUVpaVerificationResponse;
import in.wynk.payment.gateway.IPaymentInstrumentValidator;
import in.wynk.payment.gateway.payu.common.PayUCommonGateway;
import in.wynk.payment.service.IVerificationService;
import in.wynk.payment.service.impl.PayUPaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.UNKNOWN;

@Slf4j
@Service(PaymentConstants.PAYU_VERIFY)
public class PayUVerificationGateway implements IVerificationService<AbstractVerificationResponse, VerificationRequestV2> {

    private final PayUCommonGateway common;
    private final Map<VerificationType, IPaymentInstrumentValidator<? extends AbstractVerificationResponse, VerificationRequestV2>> delegate = new HashMap<>();

    public PayUVerificationGateway(PayUCommonGateway common) {
        this.common = common;
        this.delegate.put(VerificationType.VPA, new VPA());
        this.delegate.put(VerificationType.BIN, new CARD());
    }

    @Override
    public AbstractVerificationResponse verify(VerificationRequestV2 request) {
        return delegate.get(request.getVerificationType()).verify(request);
    }

    private class CARD implements IPaymentInstrumentValidator<BinVerificationResponse, VerificationRequestV2> {

        @Override
        public BinVerificationResponse verify(VerificationRequestV2 request) {
            MultiValueMap<String, String> verifyBinRequest = common.buildPayUInfoRequest(request.getClient(), PayUCommand.CARD_BIN_INFO.getCode(), "1", new String[]{request.getVerifyValue(), null, null, "1"});
            PayUCardInfo cardInfo;
            try {
                PayUBinWrapper<PayUCardInfo> payUBinWrapper = common.exchange(common.INFO_API, verifyBinRequest, new TypeReference<PayUBinWrapper<PayUCardInfo>>() {
                });
                cardInfo = payUBinWrapper.getBin();
            } catch (WynkRuntimeException e) {
                cardInfo = new PayUCardInfo();
                cardInfo.setValid(Boolean.FALSE);
                cardInfo.setIssuingBank(UNKNOWN.toUpperCase());
                cardInfo.setCardType(UNKNOWN.toUpperCase());
                cardInfo.setCardCategory(UNKNOWN.toUpperCase());
            }
            return BinVerificationResponse.from(cardInfo);
        }
    }

    private class VPA implements IPaymentInstrumentValidator<VpaVerificationResponse, VerificationRequestV2> {

        @Override
        public VpaVerificationResponse verify (VerificationRequestV2 request) {
            final MultiValueMap<String, String> verifyVpaRequest = common.buildPayUInfoRequest(request.getClient(), PayUCommand.VERIFY_VPA.getCode(), request.getVerifyValue());
            final PayUVpaVerificationResponse response = common.exchange(common.INFO_API, verifyVpaRequest, new TypeReference<PayUVpaVerificationResponse>() {
            });
            return VpaVerificationResponse.from(response);
        }
    }
}
