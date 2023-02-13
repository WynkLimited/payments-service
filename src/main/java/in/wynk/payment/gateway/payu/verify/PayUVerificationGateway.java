package in.wynk.payment.gateway.payu.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.gateway.verify.AbstractPaymentInstrumentValidationResponse;
import in.wynk.payment.dto.gateway.verify.CardValidationResponse;
import in.wynk.payment.dto.gateway.verify.VpaValidationResponse;
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



public class PayUVerificationGateway implements IPaymentInstrumentValidator<AbstractPaymentInstrumentValidationResponse, VerificationRequest> {

    private final PayUCommonGateway common;
    private final Map<String, IPaymentInstrumentValidator<? extends AbstractPaymentInstrumentValidationResponse, VerificationRequest>> delegate = new HashMap<>();

    public PayUVerificationGateway(PayUCommonGateway common) {
        this.common = common;
        this.delegate.put("VPA", new VPA());
        this.delegate.put("CARD", new CARD());
    }

    @Override
    public AbstractPaymentInstrumentValidationResponse verify(VerificationRequest request) {
        return delegate.get(request.getVerificationType().getType().toLowerCase()).verify(request);
    }

    private class CARD implements IPaymentInstrumentValidator<CardValidationResponse, VerificationRequest> {

        @Override
        public CardValidationResponse verify(VerificationRequest request) {
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
            return CardValidationResponse.builder().valid(cardInfo.isValid()).domestic(cardInfo.getIsDomestic().equalsIgnoreCase("Y")).autoRenew(cardInfo.isAutoRenewSupported()).issuingBank(cardInfo.getIssuingBank()).type(cardInfo.getCardType()).level(cardInfo.getCardCategory()).build();
        }
    }

    private class VPA implements IPaymentInstrumentValidator<VpaValidationResponse, VerificationRequest> {

        @Override
        public VpaValidationResponse verify(VerificationRequest request) {
            final MultiValueMap<String, String> verifyVpaRequest = common.buildPayUInfoRequest(request.getClient(), PayUCommand.VERIFY_VPA.getCode(), request.getVerifyValue());
            final PayUVpaVerificationResponse response = common.exchange(common.INFO_API, verifyVpaRequest, new TypeReference<PayUVpaVerificationResponse>() {
            });
            return VpaValidationResponse.builder().vpa(response.getVpa()).payerAccountName(response.getPayerAccountName()).valid(response.getIsVPAValid() == 1).build();
        }
    }


}
