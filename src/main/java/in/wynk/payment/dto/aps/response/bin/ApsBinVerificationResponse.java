package in.wynk.payment.dto.aps.response.bin;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ApsBinVerificationResponse {
   private String cardBin;
   private String cardNetwork;
   private String cardCategory;
   private String bankCode;
   private String cvvLength;
   private boolean blocked;
   private String bankName;
   private String healthState;
   private boolean autoPayEnable;
   private boolean domestic;
}
