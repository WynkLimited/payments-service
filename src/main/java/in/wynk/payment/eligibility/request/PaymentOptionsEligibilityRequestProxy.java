package in.wynk.payment.eligibility.request;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.service.IUserProfileService;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.service.VasClientService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaymentOptionsEligibilityRequestProxy {
    private Set<String> thanksSegments;
    private Boolean airtelUser;

    public Set<String> getThanksSegments(String msisdn, String service) {
        if(Objects.isNull(thanksSegments)) {
            IUserProfileService userProfileService = BeanLocatorFactory.getBean(IUserProfileService.class);
            this.thanksSegments = userProfileService.fetchThanksSegment(msisdn, service).values().stream().filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toSet());
        }
        return thanksSegments;
    }

    public boolean isAirtelUser(String msisdn,String service) {
        if (Objects.isNull(airtelUser)) {
            VasClientService vasClientService = BeanLocatorFactory.getBean(VasClientService.class);
            final MsisdnOperatorDetails msisdnOperatorDetails = vasClientService.allOperatorDetails(msisdn);
            if (msisdnOperatorDetails != null && msisdnOperatorDetails.getUserMobilityInfo() != null && msisdnOperatorDetails.getUserMobilityInfo().getCircle() != null) {
                this.airtelUser = Boolean.TRUE;
                return true;
            }
            this.airtelUser = CollectionUtils.isNotEmpty(getThanksSegments(msisdn, service));
        }
        return airtelUser;
    }
}
