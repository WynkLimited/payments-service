package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.dto.common.AbstractPaymentOptionInfo;
import in.wynk.payment.dto.common.AbstractSavedInstrumentInfo;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.IExternalPaymentEligibilityService;

import java.util.List;


public class ApsEligibilityGatewayServiceImpl implements IExternalPaymentEligibilityService {

    @Override
    public boolean isEligible(PaymentMethod entity, PaymentOptionsPlanEligibilityRequest eligibilityRequest) {
        final AbstractPaymentInstrumentsProxy<AbstractPaymentOptionInfo, AbstractSavedInstrumentInfo> proxy = eligibilityRequest.getPaymentInstrumentsProxy(entity.getPaymentCode().getCode());
        final List<AbstractPaymentOptionInfo> payOption = proxy.getPaymentInstruments(eligibilityRequest.getMsisdn());
        return payOption.stream().filter(option -> option.getType().equalsIgnoreCase(entity.getGroup())).filter(AbstractPaymentOptionInfo::isEnabled).map(AbstractPaymentOptionInfo::getId).anyMatch(optionId -> entity.getAlias().equalsIgnoreCase(optionId));
    }

    @Override
    public boolean isEligible (PaymentMethod entity, PaymentOptionsItemEligibilityRequest eligibilityRequest) {
        final AbstractPaymentInstrumentsProxy<AbstractPaymentOptionInfo, AbstractSavedInstrumentInfo> proxy = eligibilityRequest.getPaymentInstrumentsProxy(entity.getPaymentCode().getCode());
        final List<AbstractPaymentOptionInfo> payOption = proxy.getPaymentInstruments(eligibilityRequest.getMsisdn());
        return payOption.stream().filter(option -> option.getType().equalsIgnoreCase(entity.getGroup())).filter(AbstractPaymentOptionInfo::isEnabled).map(AbstractPaymentOptionInfo::getId).anyMatch(optionId -> entity.getAlias().equalsIgnoreCase(optionId));
    }
}
