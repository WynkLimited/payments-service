package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.invoice.GstStateCode;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;

/**
 * @author Nishesh Pandey
 */
public interface IUserDetailsService {
    GstStateCode getAccessStateCode(MsisdnOperatorDetails operatorDetails, String DefaultStateCode, IPurchaseDetails purchaseDetails);

    MsisdnOperatorDetails getOperatorDetails (String msisdn);
}
