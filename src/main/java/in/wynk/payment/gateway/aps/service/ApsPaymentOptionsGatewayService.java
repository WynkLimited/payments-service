package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.core.constant.CardConstants;
import in.wynk.payment.core.constant.NetBankingConstants;
import in.wynk.payment.core.constant.UpiConstants;
import in.wynk.payment.core.constant.WalletConstants;
import in.wynk.payment.dto.IPaymentOptionEligibility;
import in.wynk.payment.dto.aps.request.option.ApsPaymentOptionRequest;
import in.wynk.payment.dto.aps.response.option.ApsPaymentOptionsResponse;
import in.wynk.payment.dto.aps.response.option.paymentOptions.AbstractPaymentOptions;
import in.wynk.payment.dto.aps.response.option.paymentOptions.NetBankingPaymentOptions;
import in.wynk.payment.dto.aps.response.option.paymentOptions.UpiPaymentOptions;
import in.wynk.payment.dto.aps.response.option.paymentOptions.WalletPaymentsOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsPaymentOptionsGatewayService implements IPaymentOptionEligibility {

    private String PAYMENT_OPTION_ENDPOINT;

    private final ApsCommonGatewayService common;

    private final Map<String, String> PAY_GROUP_MIGRATION_MAPPING = new HashMap<String, String>() {{
        put(CardConstants.CARD, CardConstants.CARDS);
        put(WalletConstants.WALLET, WalletConstants.WALLETS);
        put(NetBankingConstants.NET_BANKING, NetBankingConstants.NETBANKING);
    }};

    public ApsPaymentOptionsGatewayService (String payOptionEndpoint, ApsCommonGatewayService common) {
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
        final boolean isGroupEligible = payOption.stream().filter(option -> option.getType().equalsIgnoreCase(group)).findAny().isPresent();
        if (isGroupEligible) {
            switch (group) {
                case CardConstants.CARDS:
                    return Boolean.TRUE;
                case UpiConstants.UPI:
                    return filteredPayOption.stream().flatMap(option -> ((UpiPaymentOptions) option).getUpiSupportedApps().stream()).filter(UpiPaymentOptions.UpiSupportedApps::isEnable).filter(upi -> upi.getUpiPspAppName().equalsIgnoreCase(payId)).findAny().isPresent();
                case WalletConstants.WALLETS:
                    return filteredPayOption.stream().flatMap(option -> ((WalletPaymentsOptions) option).getSubOption().stream()).filter(option -> option.getSubType().equalsIgnoreCase(payId)).findFirst().isPresent();
                case NetBankingConstants.NETBANKING:
                    return filteredPayOption.stream().flatMap(option -> ((NetBankingPaymentOptions) option).getSubOption().stream()).filter(option -> option.getSubType().equalsIgnoreCase(payId)).findFirst().isPresent();
            }
        }
        return Boolean.FALSE;
    }
}

