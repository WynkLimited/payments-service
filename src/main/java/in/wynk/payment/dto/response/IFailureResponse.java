package in.wynk.payment.dto.response;

public interface IFailureResponse {
    String getFailureType();

    String getTitle();

    String getSubtitle();

    String getDescription();

    String getButtonText();

    boolean isButtonArrow();
}
