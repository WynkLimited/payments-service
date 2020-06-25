package in.wynk.payment.dto.response.Apb;

import lombok.Data;

import java.net.URI;
import java.util.Date;

@Data
public class ApbChargingResponse {
    private URI returnUri;
    private Date txnDate;
}
