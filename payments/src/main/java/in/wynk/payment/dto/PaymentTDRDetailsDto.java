package in.wynk.payment.dto;

import lombok.Builder;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Calendar;
import java.util.Date;

@Data
@Builder
public class PaymentTDRDetailsDto {

    private String transactionId;

    private Integer planId;

    private String uid;

    private String referenceId;

    private boolean processed;

    private Calendar day;

    private Date min;

    private double tdr;

    private Calendar createdTimestamp;

    private Calendar updatedTimestamp;
}
