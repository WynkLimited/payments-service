package in.wynk.payment.gateway.payu.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.gateway.verify.AbstractPaymentInstrumentVerificationResponse;
import in.wynk.payment.dto.gateway.verify.BinVerificationResponse;
import in.wynk.payment.dto.gateway.verify.VpaVerificationResponse;
import in.wynk.payment.dto.payu.PayUBinWrapper;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.request.VerificationRequest;
import in.wynk.payment.dto.response.payu.PayUVpaVerificationResponse;
import in.wynk.payment.gateway.IPaymentInstrumentValidator;
import in.wynk.payment.gateway.payu.common.PayUCommonGateway;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.*;



public class PayUVerificationGateway implements IPaymentInstrumentValidator<AbstractPaymentInstrumentVerificationResponse, VerificationRequest> {

    private final PayUCommonGateway common;
    private final Map<String, IPaymentInstrumentValidator<? extends AbstractPaymentInstrumentVerificationResponse, VerificationRequest>> delegate = new HashMap<>();

    public PayUVerificationGateway(PayUCommonGateway common) {
        this.common = common;
        this.delegate.put("VPA", new VPA());
        this.delegate.put("CARD", new CARD());
    }

    @Override
    public AbstractPaymentInstrumentVerificationResponse verify(VerificationRequest request) {
        return delegate.get(request.getVerificationType().getType().toLowerCase()).verify(request);
    }

    private class CARD implements IPaymentInstrumentValidator<BinVerificationResponse, VerificationRequest> {

        @Override
        public BinVerificationResponse verify(VerificationRequest request) {
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
            return BinVerificationResponse.builder().isValid(cardInfo.isValid()).isDomestic(cardInfo.getIsDomestic().equalsIgnoreCase("Y")).isAutoRenewSupported(cardInfo.isAutoRenewSupported()).issuingBank(cardInfo.getIssuingBank()).cardType(cardInfo.getCardType()).cardCategory(cardInfo.getCardCategory()).build();
        }
    }

    private class VPA implements IPaymentInstrumentValidator<VpaVerificationResponse, VerificationRequest> {

        @Override
        public VpaVerificationResponse verify(VerificationRequest request) {
            final MultiValueMap<String, String> verifyVpaRequest = common.buildPayUInfoRequest(request.getClient(), PayUCommand.VERIFY_VPA.getCode(), request.getVerifyValue());
            final PayUVpaVerificationResponse response = common.exchange(common.INFO_API, verifyVpaRequest, new TypeReference<PayUVpaVerificationResponse>() {
            });
            return VpaVerificationResponse.builder().vpa(response.getVpa()).payeeAccountName(response.getPayerAccountName()).isValid(response.getIsVPAValid() == 1).build();
        }
    }


}
