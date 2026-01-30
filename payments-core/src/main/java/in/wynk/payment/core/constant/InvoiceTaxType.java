package in.wynk.payment.core.constant;

public enum InvoiceTaxType {
    CGST("CGST"), SGST("SGST"), IGST("IGST");

    private final String type;
    InvoiceTaxType (String taxType) {
        this.type = taxType;
    }

    public String getType () {
        return this.type;
    }
}
