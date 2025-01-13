package in.wynk.payment.dto.gateway.upi;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "UPI", mode = "INTENT")
public class UpiIntentChargingResponse extends AbstractSeamlessUpiChargingResponse {
    private String pa;
    private String pn;
    private String tr;
    private String am;
    private String cu;
    private String tn;
    private String mc;
    private String mn;
    private String rev;
    private String mode;
    private String recur;
    private String orgId;
    private String block;
    private String amRule;
    private String purpose;
    private String txnType;
    private String recurType;
    private String recurValue;
    private String validityEnd;
    private String validityStart;
    private String fam;

    public String toDeeplink(boolean autoRenewOpted, String upiPrefix) {
        StringBuilder stringBuilder = new StringBuilder(upiPrefix);
        if (!autoRenewOpted) stringBuilder.append("://pay?");
        else {
            stringBuilder.append("://mandate?");
            if (!StringUtils.isEmpty(getMn()))
                stringBuilder.append("&mn=").append(getMn());
            if (!StringUtils.isEmpty(getRev()))
                stringBuilder.append("&rev=").append(getRev());
            if (!StringUtils.isEmpty(getMode()))
                stringBuilder.append("&mode=").append(getMode());
            if (!StringUtils.isEmpty(getRecur()))
                stringBuilder.append("&recur=").append(getRecur());
            if (!StringUtils.isEmpty(getOrgId()))
                stringBuilder.append("&orgid=").append(getOrgId());
            if (!StringUtils.isEmpty(getBlock()))
                stringBuilder.append("&block=").append(getBlock());
            if (!StringUtils.isEmpty(getAmRule()))
                stringBuilder.append("&amrule=").append(getAmRule());
            if (!StringUtils.isEmpty(getPurpose()))
                stringBuilder.append("&purpose=").append(getPurpose());
            if (!StringUtils.isEmpty(getTxnType()))
                stringBuilder.append("&txnType=").append(getTxnType());
            if (!StringUtils.isEmpty(getRecurType()))
                stringBuilder.append("&recurtype=").append(getRecurType());
            if (!StringUtils.isEmpty(getRecurValue()))
                stringBuilder.append("&recurvalue=").append(getRecurValue());
            if (!StringUtils.isEmpty(getValidityEnd()))
                stringBuilder.append("&validityend=").append(getValidityEnd());
            if (!StringUtils.isEmpty(getValidityStart()))
                stringBuilder.append("&validitystart=").append(getValidityStart());
            stringBuilder.append("&");
        }
        stringBuilder.append("pa=").append(getPa());
        stringBuilder.append("&pn=").append(getPn());
        stringBuilder.append("&tr=").append(getTr());
        stringBuilder.append("&am=").append(getAm());
        stringBuilder.append("&cu=").append(getCu());
        stringBuilder.append("&tn=").append(getTn());
        stringBuilder.append("&mc=").append(getMc());
        stringBuilder.append("&tid=").append(getTid().replaceAll("-", ""));
        return stringBuilder.toString();
    }

}
