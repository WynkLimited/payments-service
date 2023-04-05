package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.constant.CardConstants;
import in.wynk.payment.constant.NetBankingConstants;
import in.wynk.payment.constant.UpiConstants;
import in.wynk.payment.constant.WalletConstants;
import in.wynk.payment.dto.aps.request.option.PaymentOptionRequest;
import in.wynk.payment.dto.aps.response.option.PaymentOptionsResponse;
import in.wynk.payment.dto.aps.response.option.paymentOptions.CardPaymentOptions;
import in.wynk.payment.dto.aps.response.option.paymentOptions.NetBankingPaymentOptions;
import in.wynk.payment.dto.aps.response.option.paymentOptions.UpiPaymentOptions;
import in.wynk.payment.dto.aps.response.option.paymentOptions.WalletPaymentsOptions;
import in.wynk.payment.dto.aps.response.option.savedOptions.CardSavedPayOptions;
import in.wynk.payment.dto.aps.response.option.savedOptions.NetBankingSavedPayOptions;
import in.wynk.payment.dto.aps.response.option.savedOptions.UpiSavedOptions;
import in.wynk.payment.dto.aps.response.option.savedOptions.WalletSavedOptions;
import in.wynk.payment.dto.common.*;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.IPaymentInstrumentsGatewayProxy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ApsPaymentOptionsGatewayServiceImpl implements IPaymentInstrumentsGatewayProxy<PaymentOptionsPlanEligibilityRequest> {

    private final String PAYMENT_OPTION_ENDPOINT;

    private final ApsCommonGatewayService common;

    private final Map<String, String> PAY_GROUP_MIGRATION_MAPPING = new HashMap<String, String>() {{
        put(CardConstants.CARDS, CardConstants.CARD);
        put(WalletConstants.WALLETS, WalletConstants.WALLET);
        put(NetBankingConstants.NETBANKING, NetBankingConstants.NET_BANKING);
    }};

    public ApsPaymentOptionsGatewayServiceImpl(String payOptionEndpoint, ApsCommonGatewayService common) {
        this.common = common;
        this.PAYMENT_OPTION_ENDPOINT = payOptionEndpoint;
    }

    @Override
    public AbstractPaymentInstrumentsProxy load(PaymentOptionsPlanEligibilityRequest request) {
        return new ApsPaymentInstrumentsProxy(request.getMsisdn());
    }

    @Getter
    private class ApsPaymentInstrumentsProxy extends AbstractPaymentInstrumentsProxy {
        private final PaymentOptionsResponse response;

        public ApsPaymentInstrumentsProxy(String userId) {
            super();
            response = payOption(userId);
        }

        @Override
        public List<AbstractPaymentOptionInfo> getPaymentInstruments(String userId) {
            final List<AbstractPaymentOptionInfo> payInfoList = new ArrayList<>();
            if (!CollectionUtils.isEmpty(response.getPayOptions())) {
                response.getPayOptions().forEach(option -> {
                    switch (option.getType()) {
                        case CardConstants.CARDS:
                            final List<CardOptionInfo> cardOptionInfoList = ((CardPaymentOptions) option).getOption().stream().map(cardOption -> CardOptionInfo.builder().id(cardOption.getId()).order(payInfoList.size()).enabled(cardOption.isEnabled()).build()).collect(Collectors.toList());
                            payInfoList.addAll(cardOptionInfoList);
                        case UpiConstants.UPI:
                            final List<UpiOptionInfo> upiOptionInfoList = ((UpiPaymentOptions) option).getOption().stream().map(upiOption -> UpiOptionInfo.builder().enabled(upiOption.isEnabled()).id(upiOption.getId()).health(upiOption.getHealth()).title(upiOption.getDisplayName()).order(upiOption.getOrder()).packageId(upiOption.getHyperSdkPackageName()).build()).collect(Collectors.toList());
                            payInfoList.addAll(upiOptionInfoList);
                        case WalletConstants.WALLETS:
                            final List<WalletOptionInfo> walletOptionInfoList = ((WalletPaymentsOptions) option).getOption().stream().map(walletOption -> WalletOptionInfo.builder().id(walletOption.getId()).enabled(walletOption.isEnabled()).title(walletOption.getId()).recommended(walletOption.isRecommended()).health(walletOption.getHealth()).order(payInfoList.size()).build()).collect(Collectors.toList());
                            payInfoList.addAll(walletOptionInfoList);
                        case NetBankingConstants.NETBANKING:
                            final List<NetBankingOptionInfo> netBankingOptionInfoList = ((NetBankingPaymentOptions) option).getOption().stream().map(bankOption -> NetBankingOptionInfo.builder().id(bankOption.getId()).health(bankOption.getHealth()).title(bankOption.getName()).recommended(bankOption.isRecommended()).enabled(bankOption.isEnabled()).build()).collect(Collectors.toList());
                            payInfoList.addAll(netBankingOptionInfoList);
                    }
                });
            }
            return payInfoList;
        }

        @Override
        public List<AbstractSavedInstrumentInfo> getSavedDetails(String userId) {
            final List<AbstractSavedInstrumentInfo> savedDetails = new ArrayList<>();
            if (Objects.nonNull(response.getSavedUserOptions()) && CollectionUtils.isEmpty(response.getSavedUserOptions().getPayOptions())) {
                response.getSavedUserOptions().getPayOptions().forEach(savedOption -> {
                    final String paymentGroup = PAY_GROUP_MIGRATION_MAPPING.getOrDefault(savedOption.getType(), savedOption.getType());
                    switch (savedOption.getType()) {
                        case CardConstants.CARDS:
                            final CardSavedPayOptions savedCardOption = ((CardSavedPayOptions) savedOption);
                            final SavedCardInfo cardInfo = SavedCardInfo.builder()
                                    .type(paymentGroup)
                                    .order(savedOption.getOrder())
                                    .health(savedCardOption.getHealth())
                                    .expired(savedCardOption.isExpired())
                                    .blocked(savedCardOption.isBlocked())
                                    .iconUrl(savedCardOption.getIconUrl())
                                    .cardBin(savedCardOption.getCardBin())
                                    .bankCode(savedCardOption.getBankCode())
                                    .bankCode(savedCardOption.getBankCode())
                                    .cardType(savedCardOption.getCardType())
                                    .tokenized(savedCardOption.isTokenized())
                                    .favourite(savedCardOption.isFavourite())
                                    .title(savedCardOption.getCardBankName())
                                    .preferred(savedCardOption.isPreferred())
                                    .cvvLength(savedCardOption.getCvvLength())
                                    .createdOn(savedCardOption.getCreatedOn())
                                    .updatedOn(savedCardOption.getUpdatedOn())
                                    .cardRefNo(savedCardOption.getCardRefNo())
                                    .recommended(savedCardOption.isPreferred())
                                    .expiryYear(savedCardOption.getExpiryYear())
                                    .lastUsedOn(savedCardOption.getLastUsedOn())
                                    .cardNumber(savedCardOption.getCardNumber())
                                    .cardStatus(savedCardOption.getCardStatus())
                                    .cardStatus(savedCardOption.getCardStatus())
                                    .expiryMonth(savedCardOption.getExpiryMonth())
                                    .cardCategory(savedCardOption.getCardCategory())
                                    .cardBankName(savedCardOption.getCardBankName())
                                    .cardCategory(savedCardOption.getCardCategory())
                                    .autoPayEnable(savedCardOption.isAutoPayEnable())
                                    .maskedCardNumber(savedCardOption.getMaskedCardNumber())
                                    .expressCheckout(savedCardOption.isShowOnQuickCheckout())
                                    .enable(!savedCardOption.isBlocked() && !savedCardOption.isExpired() && !savedCardOption.isHidden())
                                    .build();
                            savedDetails.add(cardInfo);
                        case UpiConstants.UPI:
                            final UpiSavedOptions savedUpiOption = ((UpiSavedOptions) savedOption);
                            final UpiSavedInfo upiInfo = UpiSavedInfo.builder()
                                    .type(paymentGroup)
                                    .iconUrl(savedUpiOption.getIconUrl())
                                    .health(savedUpiOption.getHealth())
                                    .order(savedUpiOption.getOrder())
                                    .valid(savedUpiOption.isValid())
                                    .enable(savedUpiOption.isEnable())
                                    .title(savedUpiOption.getUpiApp())
                                    .favourite(savedUpiOption.isFavourite())
                                    .recommended(savedUpiOption.isPreferred())
                                    .autoPayEnabled(Boolean.TRUE)
                                    .preferred(savedUpiOption.isPreferred())
                                    .expressCheckout(savedUpiOption.isShowOnQuickCheckout())
                                    .vpa(savedUpiOption.getUserVPA())
                                    .payId(savedUpiOption.getUpiPspAppName())
                                    .packageId(savedUpiOption.getHyperSdkPackageName())
                                    .build();
                            savedDetails.add(upiInfo);
                        case WalletConstants.WALLETS:
                            final WalletSavedOptions savedWalletOption = ((WalletSavedOptions) savedOption);
                            final WalletSavedInfo walletInfo = WalletSavedInfo.builder()
                                    .type(paymentGroup)
                                    .valid(savedWalletOption.isValid())
                                    .order(savedWalletOption.getOrder())
                                    .linked(savedWalletOption.isLinked())
                                    .health(savedWalletOption.getHealth())
                                    .enable(!savedWalletOption.isHidden())
                                    .iconUrl(savedWalletOption.getIconUrl())
                                    .title(savedWalletOption.getWalletType())
                                    .favourite(savedWalletOption.isFavourite())
                                    .preferred(savedWalletOption.isPreferred())
                                    .autoPayEnabled(Boolean.TRUE)
                                    .recommended(savedWalletOption.isRecommended())
                                    .balance(savedWalletOption.getWalletBalance().doubleValue())
                                    .walletId(savedWalletOption.getWalletId())
                                    .addMoneyAllowed(Boolean.TRUE)
                                    .expressCheckout(savedWalletOption.isShowOnQuickCheckout())
                                    .build();
                            savedDetails.add(walletInfo);
                        case NetBankingConstants.NETBANKING:
                            final NetBankingSavedPayOptions savedBankOption = ((NetBankingSavedPayOptions) savedOption);
                            final NetBankingSavedInfo bankInfo = NetBankingSavedInfo.builder()
                                    .type(paymentGroup)
                                    .order(savedBankOption.getOrder())
                                    .health(savedBankOption.getHealth())
                                    .enable(!savedBankOption.isHidden())
                                    .iconUrl(savedBankOption.getIconUrl())
                                    .favourite(savedBankOption.isFavourite())
                                    .preferred(savedBankOption.isPreferred())
                                    .expressCheckout(savedBankOption.isShowOnQuickCheckout())
                                    .build();
                            savedDetails.add(bankInfo);
                    }
                });
            }
            return savedDetails;
        }
    }

    private PaymentOptionsResponse payOption(String msisdn) {
        final PaymentOptionRequest request = PaymentOptionRequest.builder().build();
        return common.exchange(PAYMENT_OPTION_ENDPOINT, HttpMethod.POST, common.getLoginId(msisdn), request, PaymentOptionsResponse.class);
    }
}

