package in.wynk.payment.dto.request;


import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.SessionDTO;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.UpiConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.common.FilteredPaymentOptionsResult;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.request.common.UpiDetails;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionServiceV2;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.payment.core.constant.PaymentLoggingMarker;


import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.common.constant.BaseConstants.GEO_LOCATION;

@Service
@RequiredArgsConstructor
public class WebRequestVersionConversion implements IPresentation<WebChargingRequestV2, AbstractPaymentOptionsRequest<WebPaymentOptionsRequest>> {

    private static final Logger logger = LoggerFactory.getLogger(WebRequestVersionConversion.class);
    private final IPaymentOptionServiceV2 paymentOptionService;

    @Override
    public WebChargingRequestV2 transform(AbstractPaymentOptionsRequest<WebPaymentOptionsRequest> request) throws URISyntaxException {
        try {
            validateRequest(request);
            FilteredPaymentOptionsResult filteredPaymentOptionsResult = paymentOptionService.getPaymentOptionsForQRCode(request);

            List<PaymentOptionsDTO.PaymentMethodDTO> methods = filteredPaymentOptionsResult.getMethods();
            PaymentOptionsDTO.PaymentMethodDTO methodDTO = methods.isEmpty() ? null : methods.get(0);
            if (Objects.isNull(methodDTO)) {
                throw new WynkRuntimeException(PaymentErrorType.PAY853);
            }
            UpiPaymentDetails paymentDetails = buildPaymentDetails(methodDTO);
            PlanDetails productDetails = buildProductDetails(request);
            GeoLocation geoLocation = SessionContextHolder.<SessionDTO>getBody().get(GEO_LOCATION);

            return WebChargingRequestV2.builder()
                    .productDetails(productDetails)
                    .paymentDetails(paymentDetails)
                    .geoLocation(geoLocation)
                    .build();
        } catch (Exception e) {
            logger.error(PaymentLoggingMarker.QRCODE_REQUEST_TRANSFORM_ERROR, "Error while transforming paymentOptions request to Charging request", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY854, e);
        }
    }

    private void validateRequest(AbstractPaymentOptionsRequest<WebPaymentOptionsRequest> request) {
        if (Objects.isNull(request.getProductDetails())) {
            throw new WynkRuntimeException(PaymentErrorType.PAY851);
        }
        if (Objects.isNull(request.getPaymentDetails())) {
            throw new WynkRuntimeException(PaymentErrorType.PAY852);
        }
    }

    private UpiPaymentDetails buildPaymentDetails(PaymentOptionsDTO.PaymentMethodDTO methodDTO) {
        return UpiPaymentDetails.builder()
                .paymentMode(UpiConstants.UPI)
                .paymentId(methodDTO.getPaymentId())
                .merchantName(methodDTO.getPaymentCode())
                .autoRenew(methodDTO.isAutoRenewSupported())
                .trialOpted(false)
                .upiDetails(UpiDetails.builder()
                        .intent(true)
                        .build())
                .build();
    }

    private PlanDetails buildProductDetails(AbstractPaymentOptionsRequest<WebPaymentOptionsRequest> request) {
        if (StringUtils.isEmpty(request.getProductDetails().getId())) {
            throw new WynkRuntimeException(PaymentErrorType.PAY855);
        }
        return PlanDetails.builder()
                .planId(Integer.parseInt(request.getProductDetails().getId()))
                .type(PLAN)
                .build();
    }
}