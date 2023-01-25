package in.wynk.payment.dto.request.common;

import in.wynk.payment.dto.request.PromotionDetails;
import in.wynk.payment.dto.request.upi.AlertDetails;
import in.wynk.payment.dto.request.upi.SupportingDetails;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
public class UPI {
    private String id;
    private String code;
    private String title;
    private String description;
    private List<AlertDetails> alertDetails;
    private SupportingDetails supportingDetails;
    private SavedDetails savedDetails;
    private PromotionDetails promotonDetails;
    private UiDetails uiDetails;
}
