package in.wynk.payment.utils;

import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.dto.AppDetails;
import in.wynk.payment.dto.PageUrlDetails;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.*;
public class PresentationUtils {

    public static List<String> getRails(PartnerDTO partner, OfferDTO offer) {
        if(Objects.nonNull(offer.getUiMeta())) {
            return offer.getUiMeta().getPromotionalImages();
        } else {
            return partner.getContentImages().values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        }
    }

    public static String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

    public static PageUrlDetails getPageDetails (String service, String clientAlias) {
        final String clientPagePlaceHolder = PAYMENT_PAGE_PLACE_HOLDER.replace("%c", clientAlias);
        final IAppDetails appDetails = AppDetails.builder().buildNo(-1).service(service).appId(MOBILITY).os(ANDROID).build();
        final String successPage = PresentationUtils.buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "success"),"${payment.success.page}"), appDetails);
        final String failurePage = PresentationUtils.buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "failure"), "${payment.failure.page}"), appDetails);
        final String pendingPage = PresentationUtils.buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "pending"), "${payment.pending.page}"), appDetails);
        final String unknownPage = PresentationUtils.buildUrlFrom(EmbeddedPropertyResolver.resolveEmbeddedValue(clientPagePlaceHolder.replace("%p", "unknown"), "${payment.unknown.page}"), appDetails);
        return PageUrlDetails.builder().successPageUrl(successPage).failurePageUrl(failurePage).pendingPageUrl(pendingPage).unknownPageUrl(unknownPage).build();
    }
}
