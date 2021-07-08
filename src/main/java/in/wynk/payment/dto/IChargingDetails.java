package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.session.context.SessionContextHolder;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.common.constant.BaseConstants.EQUAL;

public interface IChargingDetails extends IPurchaseDetails {

    IPageUrlDetails getPageUrlDetails();

    interface IPageUrlDetails {
        String getSuccessPageUrl();
        String getFailurePageUrl();
        String getPendingPageUrl();
        String getUnknownPageUrl();
    }

    default String buildUrlFrom(String url, IAppDetails appDetails) {
        return url + SessionContextHolder.getId() + SLASH + appDetails.getOs() + QUESTION_MARK + SERVICE + EQUAL + appDetails.getService() + AND + APP_ID + EQUAL + appDetails.getAppId() + AND + BUILD_NO + EQUAL + appDetails.getBuildNo();
    }

}
