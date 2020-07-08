package in.wynk.payment.dto.response;

import lombok.Getter;


@Getter
public class ValidateTokenResponse {
    private String id;

    private String email;

    private String mobile;

    private long expires;

}
