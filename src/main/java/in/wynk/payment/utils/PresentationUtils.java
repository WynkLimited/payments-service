package in.wynk.payment.utils;

import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PresentationUtils {

    public static List<String> getRails(PartnerDTO partner, OfferDTO offer) {
        if(Objects.nonNull(offer.getUiMeta())) {
            return offer.getUiMeta().getPromotionalImages();
        } else {
            return partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        }
    }
}
