package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PayUBinWrapper<T> {

    private int status;
    private PayUBinData<T> data;

    public T getBin() {
        return data.getBinInfo();
    }

    @Getter
    @NoArgsConstructor
    public static class PayUBinData<T> {
        @JsonProperty(value = "bins_data")
        private T binInfo;
    }

}
