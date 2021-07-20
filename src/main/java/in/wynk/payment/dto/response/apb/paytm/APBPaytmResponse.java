package in.wynk.payment.dto.response.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.apb.paytm.AbstractAPBPaytmResponse;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class APBPaytmResponse extends AbstractAPBPaytmResponse {
    private APBPaytmResponseData data;
}
