package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.verify.BinVerificationResponse;
import in.wynk.payment.dto.gateway.verify.VpaVerificationResponse;
import in.wynk.payment.dto.payu.PayUBinWrapper;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.dto.request.AbstractVerificationRequest;
import in.wynk.payment.dto.response.payu.PayUVpaVerificationResponse;
import in.wynk.payment.gateway.IPaymentAccountVerification;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.UNKNOWN;

@Slf4j
public class PayUVerificationGatewayImpl implements IPaymentAccountVerification<AbstractVerificationResponse, AbstractVerificationRequest> {

    private final ObjectMapper objectMapper;
    private final PayUCommonGateway common;
    private final Map<VerificationType, IPaymentAccountVerification<? extends AbstractVerificationResponse, AbstractVerificationRequest>> delegate = new HashMap<>();

    public PayUVerificationGatewayImpl(PayUCommonGateway common, ObjectMapper objectMapper) {
        this.common = common;
        this.objectMapper = objectMapper;
        this.delegate.put(VerificationType.VPA, new VPA());
        this.delegate.put(VerificationType.BIN, new CARD());
    }

    @Override
    public AbstractVerificationResponse verify(AbstractVerificationRequest request) {
        return delegate.get(request.getVerificationType()).verify(request);
    }

    private class CARD implements IPaymentAccountVerification<BinVerificationResponse, AbstractVerificationRequest> {

        @Override
        public BinVerificationResponse verify(AbstractVerificationRequest request) {
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

    private class VPA implements IPaymentAccountVerification<VpaVerificationResponse, AbstractVerificationRequest> {

        @SneakyThrows
        @Override
        public VpaVerificationResponse verify (AbstractVerificationRequest request) {
            final MultiValueMap<String, String> verifyVpaRequest = common.buildPayUInfoRequest(request.getClient(), PayUCommand.VERIFY_VPA.getCode(), request.getVerifyValue(), objectMapper.writeValueAsString(new HashMap<String, String>() {{
                put("validateAutoPayVPA", "1");
            }}));
            try {
                final PayUVpaVerificationResponse response = common.exchange(common.INFO_API, verifyVpaRequest, new TypeReference<PayUVpaVerificationResponse>() {
                });
                return VpaVerificationResponse.from(response);
            }catch(Exception e) {
                log.error("Exception occurred while verifying vpa with payU {}", e.getMessage());
               throw new WynkRuntimeException("Exception occurred while verifying vpa with payU", e);
            }
        }
    }
}
