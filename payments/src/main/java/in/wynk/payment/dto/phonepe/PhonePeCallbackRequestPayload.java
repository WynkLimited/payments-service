package in.wynk.payment.dto.phonepe;

import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class PhonePeCallbackRequestPayload extends CallbackRequest {

    private String code;
    private String amount;
    private String checksum;
    private String merchantId;
    private String transactionId;
    private String providerReferenceId;

    private String param1;
    private String param2;
    private String param3;
    private String param4;
    private String param5;
    private String param6;
    private String param7;
    private String param8;
    private String param9;
    private String param10;
    private String param11;
    private String param12;
    private String param13;
    private String param14;
    private String param15;
    private String param16;
    private String param17;
    private String param18;
    private String param19;
    private String param20;



}