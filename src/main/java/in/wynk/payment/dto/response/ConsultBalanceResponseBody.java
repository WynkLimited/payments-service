package in.wynk.payment.dto.response;

import in.wynk.common.enums.Status;
import lombok.Data;

import java.util.Map;

@Data
public class ConsultBalanceResponseBody {

    private Result resultInfo;

    private boolean fundsSufficient;

    private boolean addMoneyAllowed;

    private Double deficitAmount;

    private Map<String, Double> amountDetails;

    @Data
    public static class Result {

        private Status resultStatus;

        private String resultCode;

        private String resultMsg;
    }

}
