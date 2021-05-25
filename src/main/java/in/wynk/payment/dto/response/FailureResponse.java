package in.wynk.payment.dto.response;

import lombok.Getter;

@Getter
public enum FailureResponse implements IFailureResponse {
    FAIL001("FAILURE","Something went wrong","We could not process your payment","Don’t worry, any amount deducted will be credited back to the payment source in a few working days.","TRY ANOTHER OPTION",Boolean.TRUE),
    FAIL002("PAYMENT-PENDING","Payment under process","We are still processing your payment","You will soon get a confirmation message from us regarding your payment. Kindly wait for some time.","GO TO HOMEPAGE",Boolean.FALSE),
    FAIL003("PAYMENT-TIMEOUT","That was too long","This session has expired","Looks like your subscription for #{PLAN_NAME} could not be completed.","LET’S TRY AGAIN",Boolean.TRUE);
    // payment timeout might not be required. also, can we use a placeholder in enum
    private final String failureType;
    private final String title;
    private final String subtitle;
    private final String description;
    private final String buttonText;
    private final boolean buttonArrow;

    FailureResponse(String failureType, String title, String subtitle, String description, String buttonText, boolean buttonArrow) {
        this.failureType = failureType;
        this.description = description;
        this.title = title;
        this.subtitle = subtitle;
        this.buttonArrow = buttonArrow;
        this.buttonText = buttonText;
    }
}
