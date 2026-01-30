package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.common.base.Strings;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.service.GSTStateCodesCachingService;
import in.wynk.payment.dto.invoice.GstStateCode;
import in.wynk.payment.service.IUserDetailsService;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.service.VasClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.DEFAULT_ACCESS_STATE_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.*;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDetailsService implements IUserDetailsService {
    private final GSTStateCodesCachingService stateCodesCachingService;
    private final VasClientService vasClientService;

    @Override
    public GstStateCode getAccessStateCode(MsisdnOperatorDetails operatorDetails, String DefaultStateCode, IPurchaseDetails purchaseDetails) {
        String gstStateCode = (Strings.isNullOrEmpty(DefaultStateCode)) ? DEFAULT_ACCESS_STATE_CODE : DefaultStateCode;
        AnalyticService.update(DEFAULT_GST_STATE_CODE, gstStateCode);
        try {
            if (Objects.nonNull(operatorDetails) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo()) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo().getGstStateCode())) {
                String optimusGSTStateCode = operatorDetails.getUserMobilityInfo().getGstStateCode().trim();
                if (isNumberFrom0To9(optimusGSTStateCode)) {
                    optimusGSTStateCode = "0" + optimusGSTStateCode;
                }
                AnalyticService.update(OPTIMUS_GST_STATE_CODE, optimusGSTStateCode);
                if (stateCodesCachingService.containsKey(optimusGSTStateCode)) {
                    return GstStateCode.builder()
                            .defaultGstStateCode(gstStateCode)
                            .optimusGstStateCode(optimusGSTStateCode)
                            .build();
                }
            }
            if (Objects.nonNull(purchaseDetails) &&
                    Objects.nonNull(purchaseDetails.getGeoLocation()) &&
                    Objects.nonNull(purchaseDetails.getGeoLocation().getStateCode())) {
                final String geoLocationISOStateCode = purchaseDetails.getGeoLocation().getStateCode();
                if (stateCodesCachingService.containsByISOStateCode(geoLocationISOStateCode)) {
                    gstStateCode = stateCodesCachingService.getByISOStateCode(geoLocationISOStateCode).getId();
                    AnalyticService.update(GEOLOCATION_GST_STATE_CODE, gstStateCode);
                    return GstStateCode.builder()
                            .defaultGstStateCode(gstStateCode)
                            .geoLocationGstStateCode(gstStateCode)
                            .build();
                }
            }
        } catch (Exception ex) {
            log.error(PaymentLoggingMarker.GST_STATE_CODE_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY441, ex);
        }
        return GstStateCode.builder()
                .defaultGstStateCode(gstStateCode)
                .build();
    }

    @Override
    public MsisdnOperatorDetails getOperatorDetails (String msisdn) {
        try {
            if (StringUtils.isNotBlank(msisdn)) {
                MsisdnOperatorDetails operatorDetails = vasClientService.allOperatorDetails(msisdn);
                if (Objects.nonNull(operatorDetails)) {
                    return operatorDetails;
                }
            }
            return null;
        } catch (Exception ex) {
            log.error(PaymentLoggingMarker.OPERATOR_DETAILS_NOT_FOUND, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY443, ex);
        }
    }

    private boolean isNumberFrom0To9 (String input) {
        try {
            int number = Integer.parseInt(input);
            return number >= 0 && number <= 9;
        } catch (NumberFormatException e) {
            return false; // Input is not a valid integer
        }
    }
}
