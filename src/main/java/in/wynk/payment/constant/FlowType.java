package in.wynk.payment.constant;

public enum FlowType {
    INTENT("INTENT"), COLLECT("COLLECT"), SEAMLESS("SEAMLESS"), NON_SEAMLESS("NON_SEAMLESS"), COLLECT_IN_APP("COLLECT_IN_APP"), NON_SEAMLESS_REDIRECT_FLOW("NON_SEAMLESS_REDIRECT_FLOW");
    private final String value;

    FlowType (String value) {
        this.value = value;
    }

    public String getValue () {
        return this.value;
    }
}
