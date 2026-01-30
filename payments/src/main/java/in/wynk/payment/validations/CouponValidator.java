package in.wynk.payment.validations;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.BaseHandler;
import in.wynk.coupon.core.constant.CouponProvisionState;
import in.wynk.coupon.core.dto.CouponProvisionRequest;
import in.wynk.coupon.core.dto.CouponResponse;
import in.wynk.coupon.core.service.ICouponManager;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.identity.client.utils.IdentityUtils;
import org.apache.commons.lang3.StringUtils;

import static in.wynk.common.constant.BaseConstants.PLAN;
import static in.wynk.common.constant.BaseConstants.POINT;
import static in.wynk.coupon.core.constant.ProvisionSource.MANAGED;

public class CouponValidator<T extends ICouponValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        if (StringUtils.isNotBlank(request.getCouponCode())) {
            CouponProvisionRequest.CouponProvisionRequestBuilder builder = CouponProvisionRequest.builder()
                    .source(MANAGED)
                    .msisdn(request.getMsisdn())
                    .service(request.getService())
                    .couponCode(request.getCouponCode())
                    .paymentCode(request.getPaymentCode().getCode())
                    .uid(IdentityUtils.getUidFromUserName(request.getMsisdn(), request.getService()));
            if (request.getProductDetails().getType().equalsIgnoreCase(POINT))
                builder.itemId(request.getProductDetails().getId());
            if (request.getProductDetails().getType().equalsIgnoreCase(PLAN))
                builder.selectedPlan(BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(request.getProductDetails().getId()));
            CouponResponse couponResponse = BeanLocatorFactory.getBean(ICouponManager.class).evalCouponEligibility(builder.build());
            if (couponResponse.getState() == CouponProvisionState.INELIGIBLE)
                throw new WynkRuntimeException(couponResponse.getError().getCode(), couponResponse.getError().getDescription(), "Validation Failure");
        }
        super.handle(request);
    }
}