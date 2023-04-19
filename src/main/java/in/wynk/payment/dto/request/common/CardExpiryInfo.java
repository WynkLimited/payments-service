package in.wynk.payment.dto.request.common;


import lombok.Getter;

import java.io.Serializable;

@Getter
public class CardExpiryInfo implements Serializable {
    private String month;
    private String year;
}
