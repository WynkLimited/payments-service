package in.wynk.payment.dto.response;

import in.wynk.payment.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ConsultBalanceResponseBody {

    private Result resultInfo;

    private boolean fundsSufficient;

    private boolean addMoneyAllowed;

    private BigDecimal deficitAmount;

    private Map<String, BigDecimal> amountDetails;

    public static class Result {

        private Status resultStatus;

        private String resultCode;

        private String resultMsg;

        public Result() {
        }

        public Result(Status resultStatus, String resultCode, String resultMsg) {
            this.resultStatus = resultStatus;
            this.resultCode = resultCode;
            this.resultMsg = resultMsg;
        }

        public Status getResultStatus() {
            return resultStatus;
        }

        public void setResultStatus(Status resultStatus) {
            this.resultStatus = resultStatus;
        }

        public String getResultCode() {
            return resultCode;
        }

        public void setResultCode(String resultCode) {
            this.resultCode = resultCode;
        }

        public String getResultMsg() {
            return resultMsg;
        }

        public void setResultMsg(String resultMsg) {
            this.resultMsg = resultMsg;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "resultStatus=" + resultStatus +
                    ", resultCode='" + resultCode + '\'' +
                    ", resultMsg='" + resultMsg + '\'' +
                    '}';
        }
    }

}
