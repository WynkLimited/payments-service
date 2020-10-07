package in.wynk.payment.dto.response;

import in.wynk.common.enums.Status;

public class CustomResponse {

    private Status status;

    private String statusMessage;

    private String responseCode;

    private String statusCode;

    private String message;

    public CustomResponse() {
    }

    public CustomResponse(Status status, String statusMessage, String responseCode,
                        String statusCode, String message) {
        this.status = status;
        this.statusMessage = statusMessage;
        this.responseCode = responseCode;
        this.statusCode = statusCode;
        this.message = message;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "CustomResponse{" +
                "status=" + status +
                ", statusMessage='" + statusMessage + '\'' +
                ", responseCode='" + responseCode + '\'' +
                ", statusCode='" + statusCode + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
