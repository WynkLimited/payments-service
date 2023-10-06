package in.wynk.payment.dto.aps.kafka;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.event.PaymentChargeEvent;
import in.wynk.payment.dto.aps.kafka.response.*;
import in.wynk.stream.advice.KafkaEvent;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
@KafkaEvent(topic = "${wynk.kafka.producer.payment.charge.topic}")
public class PaymentChargeResponseMessage {
    private String to;
    private String from;
    @Setter
    private String orgId;
    @Setter
    private String serviceId;
    private String sessionId;
    private String retailerId;
    private String deeplink;
    private String campaignId;
    private PlanDetails planDetails;
    private OrderDetails orderDetails;

    public static PaymentChargeResponseMessage from (PaymentChargeEvent event, PlanDTO plan) {
        return PaymentChargeResponseMessage.builder().from(event.getFrom()).to(event.getTo()).orgId(event.getOrgId()).serviceId(event.getServiceId())
                .sessionId(event.getSessionId()).retailerId(WynkServiceUtils.fromAppId(PaymentConstants.WHATSAPP).getRetailerId()).deeplink(event.getDeeplink()).campaignId(event.getCampaignId())
                .planDetails(PlanDetails.builder().id(event.getPlanId()).title(plan.getTitle()).description(plan.getDescription())
                        .priceDetails(PriceDetails.builder().price(plan.getPrice().getDisplayAmount().intValue()).currency(plan.getPrice().getCurrency()).discountPrice((int) (long) plan.getPrice().getAmount()).build())
                        .periodDetails(PeriodDetails.builder().validity(plan.getPeriod().getValidity()).validityUnit(plan.getPeriod().getValidityUnit()).build()).build())
                .orderDetails(OrderDetails.builder().id(event.getTransactionId()).code(event.getPaymentGatewayCode()).amount((int) (long) plan.getPrice().getAmount())
                                .discount((int) (long) plan.getPrice().getDisplayAmount() - (int) (long) plan.getPrice().getAmount())
                                .mandate(event.isMandate()).trial(event.isTrialOpted()).mandateAmount((int) (long) plan.getPrice().getMandateAmount()).taxDetails(TaxDetails.builder().value(plan.getTaxValue()).build()).build())
                .build();
    }
}
