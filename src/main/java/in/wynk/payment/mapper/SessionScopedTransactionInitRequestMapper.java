package in.wynk.payment.mapper;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.Utils;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.service.IPricingManager;
import in.wynk.session.context.SessionContextHolder;

import static in.wynk.common.constant.BaseConstants.CLIENT;

public class SessionScopedTransactionInitRequestMapper implements IObjectMapper {

    public static AbstractTransactionInitRequest from(AbstractChargingRequest<?> request) {
        AbstractTransactionInitRequest initRequest = AbstractChargingRequest.PlanWebChargingDetails.class.isAssignableFrom(request.getChargingDetails().getClass()) ? planInit(request): pointInit(request);
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    private static PlanTransactionInitRequest planInit(AbstractChargingRequest<?> request) {
        final AbstractChargingRequest.PlanWebChargingDetails planDetails = (AbstractChargingRequest.PlanWebChargingDetails) request.getChargingDetails();
        final PaymentEvent paymentEvent = planDetails.isAutoRenew() ? PaymentEvent.SUBSCRIBE: PaymentEvent.PURCHASE;
        final SessionDTO session = SessionContextHolder.getBody();
        final String rawMsisdn = session.get(BaseConstants.MSISDN);
        final String msisdn = Utils.getTenDigitMsisdn(rawMsisdn);
        final String uid = session.get(BaseConstants.UID);
        final String clientAlias = session.get(CLIENT);
        return PlanTransactionInitRequest.builder().autoRenewOpted(planDetails.isAutoRenew()).paymentCode(request.getPaymentCode()).trialOpted(planDetails.isTrialOpted()).couponId(planDetails.getCouponId()).planId(planDetails.getPlanId()).clientAlias(clientAlias).event(paymentEvent).msisdn(msisdn).uid(uid).build();
    }

    public static PointTransactionInitRequest pointInit(AbstractChargingRequest<?> request) {
        final AbstractChargingRequest.PointWebChargingDetails planDetails = (AbstractChargingRequest.PointWebChargingDetails) request.getChargingDetails();
        final SessionDTO session = SessionContextHolder.getBody();
        final double amount = session.get(BaseConstants.POINT_PURCHASE_ITEM_PRICE);
        final String rawMsisdn = session.get(BaseConstants.MSISDN);
        final String msisdn = Utils.getTenDigitMsisdn(rawMsisdn);
        final String uid = session.get(BaseConstants.UID);
        final String clientAlias = session.get(CLIENT);
        return PointTransactionInitRequest.builder().paymentCode(request.getPaymentCode()).event(PaymentEvent.POINT_PURCHASE).couponId(planDetails.getCouponId()).itemId(planDetails.getItemId()).clientAlias(clientAlias).amount(amount).msisdn(msisdn).uid(uid).build();
    }

}
