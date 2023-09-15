package in.wynk.payment.eligibility.request;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.eligibility.dto.IEligibilityRequest;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.dto.common.AbstractPaymentOptionInfo;
import in.wynk.payment.dto.common.AbstractSavedInstrumentInfo;
import in.wynk.payment.gateway.IPaymentInstrumentsProxy;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@SuperBuilder
public abstract class PaymentOptionsEligibilityRequest implements IEligibilityRequest {

    private final String si;
    private final String os;
    private final String uid;
    private final String appId;
    private final String msisdn;
    private final String client;
    private final String service;
    private final String couponCode;
    private final String countryCode;

    private final int buildNo;

    private final Map<String, Integer> msisdnRangeProbability = new HashMap<>();

    private final PaymentOptionsEligibilityRequestProxy paymentOptionsEligibilityRequestProxy;
    private final Map<String, AbstractPaymentInstrumentsProxy<AbstractPaymentOptionInfo, AbstractSavedInstrumentInfo>> payInstrumentProxyMap = new HashMap<>();

    @Setter
    private String group;

    public Integer getMsisdnRangeProbability () {
        if (msisdnRangeProbability.containsKey(msisdn)) {
            return msisdnRangeProbability.get(msisdn);
        }
        long sum = findSumOfDigits(Long.valueOf(msisdn.replace("+91", "")));
        int probability = ((int) sum) % 10;
        msisdnRangeProbability.put(msisdn, probability);
        return probability;
    }

    private long findSumOfDigits (Long msisdn) {
        return msisdn == 0 ? 0 : msisdn % 10 + findSumOfDigits(msisdn / 10);
    }

    public AbstractPaymentInstrumentsProxy getPaymentInstrumentsProxy(String payCode) {
        if (Objects.nonNull(payInstrumentProxyMap) && payInstrumentProxyMap.containsKey(payCode))
            return payInstrumentProxyMap.get(payCode);
        final AbstractPaymentInstrumentsProxy proxy = BeanLocatorFactory.getBean(payCode, IPaymentInstrumentsProxy.class).load(this);
        payInstrumentProxyMap.put(payCode, proxy);
        return proxy;
    }

    public static PaymentOptionsEligibilityRequest from(PaymentOptionsComputationDTO computationDTO) {
        final PlanDTO planDTO = computationDTO.getPlanDTO();
        final ItemDTO itemDTO = computationDTO.getItemDTO();
        if (Objects.nonNull(planDTO)) {
            PaymentOptionsPlanEligibilityRequest.PaymentOptionsPlanEligibilityRequestBuilder builder = PaymentOptionsPlanEligibilityRequest.builder().client(computationDTO.getClient());
            builder.planId(String.valueOf(planDTO.getId())).appId(computationDTO.getAppId()).buildNo(computationDTO.getBuildNo()).countryCode(computationDTO.getCountryCode()).couponCode(computationDTO.getCouponCode()).service(planDTO.getService()).os(computationDTO.getOs()).uid(IdentityUtils.getUidFromUserName(computationDTO.getMsisdn(), computationDTO.getPlanDTO().getService()));
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) builder.msisdn(computationDTO.getMsisdn());
            if (computationDTO.getCountryCode() == null) {
                builder.countryCode(WynkServiceUtils.fromServiceId(planDTO.getService()).getDefaultCountryCode());
            }
            return builder.planId(String.valueOf(planDTO.getId())).si(computationDTO.getSi()).paymentOptionsEligibilityRequestProxy(BeanLocatorFactory.getBean(PaymentOptionsEligibilityRequestProxy.class)).build();
        } else {
            PaymentOptionsItemEligibilityRequest.PaymentOptionsItemEligibilityRequestBuilder builder = PaymentOptionsItemEligibilityRequest.builder().client(computationDTO.getClient());
            builder.itemId(String.valueOf(itemDTO.getId())).appId(computationDTO.getAppId()).buildNo(computationDTO.getBuildNo()).countryCode(computationDTO.getCountryCode()).couponCode(computationDTO.getCouponCode()).service(itemDTO.getService()).os(computationDTO.getOs());
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) builder.msisdn(computationDTO.getMsisdn());
            if (computationDTO.getCountryCode() == null) {
                builder.countryCode(WynkServiceUtils.fromServiceId(itemDTO.getService()).getDefaultCountryCode());
            }
            return builder.itemId(itemDTO.getId()).si(computationDTO.getSi()).paymentOptionsEligibilityRequestProxy(BeanLocatorFactory.getBean(PaymentOptionsEligibilityRequestProxy.class)).build();
        }
    }
}