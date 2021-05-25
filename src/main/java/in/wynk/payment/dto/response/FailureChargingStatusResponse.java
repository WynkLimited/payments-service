package in.wynk.payment.dto.response;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class FailureChargingStatusResponse extends AbstractChargingStatusResponse {
    private String failureType;
    private String title;
    private String subtitle;
    private String description;
    private String buttonText;
    private boolean buttonArrow;

    public FailureChargingStatusResponse(FailureResponse failureResponse) {
        this.buttonArrow = failureResponse.isButtonArrow();
        this.buttonText = failureResponse.getButtonText();
        this.description = failureResponse.getDescription();
        this.failureType = failureResponse.getFailureType();
        this.subtitle = failureResponse.getSubtitle();
        this.title = failureResponse.getTitle();
    }
}
