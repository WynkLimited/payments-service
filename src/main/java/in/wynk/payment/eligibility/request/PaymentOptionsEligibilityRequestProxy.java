package in.wynk.payment.eligibility.request;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.coupon.core.service.IUserProfileService;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.service.VasClientService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PaymentOptionsEligibilityRequestProxy {
    private Pair<Set<String>,Boolean> thanksSegments;
    private Pair<Boolean, Boolean> airtelUser;

    public Set<String> getThanksSegments(String msisdn, String service) {
        if(Objects.isNull(thanksSegments)) {
            IUserProfileService userProfileService = BeanLocatorFactory.getBean(IUserProfileService.class);
            this.thanksSegments = Pair.of(userProfileService.fetchThanksSegment(msisdn, service).values().stream().filter(Objects::nonNull).flatMap(Collection::stream).collect(Collectors.toSet()),Boolean.TRUE);
        }
        return thanksSegments.getFirst();
    }

    public boolean isAirtelUser(String msisdn,String service) {
        if (Objects.isNull(airtelUser)) {
            VasClientService vasClientService = BeanLocatorFactory.getBean(VasClientService.class);
            final MsisdnOperatorDetails msisdnOperatorDetails = vasClientService.allOperatorDetails(msisdn);
            if (msisdnOperatorDetails != null && msisdnOperatorDetails.getUserMobilityInfo() != null && msisdnOperatorDetails.getUserMobilityInfo().getCircle() != null) {
                this.airtelUser = Pair.of(Boolean.TRUE, Boolean.TRUE);
                return airtelUser.getFirst();
            }
            this.airtelUser = Pair.of(CollectionUtils.isNotEmpty(getThanksSegments(msisdn, service)), Boolean.TRUE);
        }
        return airtelUser.getFirst();
    }
}
