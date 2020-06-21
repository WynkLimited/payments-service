package in.wynk.payment.dto.response;

import in.wynk.payment.enums.Status;

public class PaytmWalletLinkResponse extends CustomResponse {

    private String state;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public PaytmWalletLinkResponse() {
    }

    public PaytmWalletLinkResponse(Status status, String statusMessage, String responseCode,
                                   String statusCode, String message, String state) {
        super(status, statusMessage, responseCode, statusCode, message);
        this.state = state;
    }

}
