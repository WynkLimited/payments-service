package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.utils.Utils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@ToString
@NoArgsConstructor
public class PhonePeTransactionResponse {

    public Boolean success;
    private PhonePeTransactionStatus code;
    public String message;
    public Data data = new Data();

    public PhonePeTransactionResponse(Map<String, String> requestPayload) {
        if (MapUtils.isNotEmpty(requestPayload)) {
            String code = Utils.getStringParameter(requestPayload, "code");
            if (StringUtils.isNotEmpty(code)) {
                this.code = PhonePeTransactionStatus.valueOf(code);
                if (this.code.equals(PhonePeTransactionStatus.PAYMENT_SUCCESS)) {
                    this.success = true;
                }
            }
            this.data.merchantId = Utils.getStringParameter(requestPayload, "merchantId");
            this.data.transactionId = Utils.getStringParameter(requestPayload, "transactionId");
            this.data.amount = Utils.getLongParameter(requestPayload, "amount", 0L);
            this.data.providerReferenceId = Utils.getStringParameter(requestPayload, "providerReferenceId");
        }
    }


    @Getter
    @ToString
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static public class Data {

        public String transactionId;
        public String merchantId;
        public String providerReferenceId;
        public Long amount;
        public String paymentState;
        public String payResponseCode;


    }
}



