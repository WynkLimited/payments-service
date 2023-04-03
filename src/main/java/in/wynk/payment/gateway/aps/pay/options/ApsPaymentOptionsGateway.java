package in.wynk.payment.gateway.aps.pay.options;

import in.wynk.payment.core.constant.CardConstants;
import in.wynk.payment.core.constant.NetBankingConstants;
import in.wynk.payment.core.constant.WalletConstants;
import in.wynk.payment.dto.IPaymentOptionEligibility;
import in.wynk.payment.dto.aps.request.option.ApsPaymentOptionRequest;
import in.wynk.payment.dto.aps.response.option.ApsPaymentOptionsResponse;
import in.wynk.payment.dto.aps.response.option.paymentOptions.AbstractPaymentOptions;
import in.wynk.payment.gateway.aps.common.ApsCommonGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsPaymentOptionsGateway implements IPaymentOptionEligibility {

    private final String PAYMENT_OPTION_ENDPOINT;

    private final ApsCommonGateway common;

    private final Map<String, String> PAY_GROUP_MIGRATION_MAPPING = new HashMap<String, String>() {{
        put(CardConstants.CARD, CardConstants.CARDS);
        put(WalletConstants.WALLET, WalletConstants.WALLETS);
        put(NetBankingConstants.NET_BANKING, NetBankingConstants.NETBANKING);
    }};

    public ApsPaymentOptionsGateway(String payOptionEndpoint, ApsCommonGateway common) {
        this.common = common;
        this.PAYMENT_OPTION_ENDPOINT = payOptionEndpoint;
    }

    public ApsPaymentOptionsResponse payOption(String msisdn) {
        final ApsPaymentOptionRequest request = ApsPaymentOptionRequest.builder().build();
        return common.exchange(PAYMENT_OPTION_ENDPOINT, HttpMethod.POST, common.getLoginId(msisdn), request, ApsPaymentOptionsResponse.class);
    }

    @Override
    public boolean isEligible(String msisdn, String payGroup, String payId) {
        final String group = PAY_GROUP_MIGRATION_MAPPING.getOrDefault(payGroup, payGroup);
        final ApsPaymentOptionsResponse response = payOption(msisdn);
        final List<AbstractPaymentOptions> payOption = response.getPayOptions();
        final List<AbstractPaymentOptions> filteredPayOption = payOption.stream().filter(option -> option.getType().equalsIgnoreCase(group)).collect(Collectors.toList());
        return payOption.stream().filter(option -> option.getType().equalsIgnoreCase(group)).map(AbstractPaymentOptions::getOption).flatMap(Collection::stream).filter(AbstractPaymentOptions.ISubOption::isEnabled).anyMatch(option -> payId.equalsIgnoreCase(option.getId()));
    }
}

