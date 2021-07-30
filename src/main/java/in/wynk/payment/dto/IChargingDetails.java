package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;

public interface IChargingDetails extends IPurchaseDetails {

    IPageUrlDetails getPageUrlDetails();
    ICallbackDetails getCallbackDetails();

    interface IPageUrlDetails {
        String getSuccessPageUrl();
        String getFailurePageUrl();
        String getPendingPageUrl();
        String getUnknownPageUrl();
    }

    interface ICallbackDetails {
        String getCallbackUrl();
    }

}
