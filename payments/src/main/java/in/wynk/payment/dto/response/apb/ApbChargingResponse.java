package in.wynk.payment.dto.response.apb;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.Date;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbChargingResponse {
    private URI returnUri;
    private Date txnDate;
}
