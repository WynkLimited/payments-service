package in.wynk.payment.dto.amazonIap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AmazonIapStatusCode {

    ERR_001("101", "Receipt is already processed"),
    ERR_002("102", "Receipt is empty or null"),
    ERR_003("103", "Failed to get receipt from amazon server"),
    ERR_004("104", "Something went wrong");

    private final String code;
    private final String description;

}
