package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;

import java.io.Serializable;

public interface IChargingDetails extends IPurchaseDetails, Serializable {

    IPageUrlDetails getPageUrlDetails();

    ICallbackDetails getCallbackDetails();

    interface IPageUrlDetails extends Serializable {

        String getSuccessPageUrl();

        String getFailurePageUrl();

        String getPendingPageUrl();

        String getUnknownPageUrl();

    }

    interface ICallbackDetails extends Serializable {
        String getCallbackUrl();
    }

}