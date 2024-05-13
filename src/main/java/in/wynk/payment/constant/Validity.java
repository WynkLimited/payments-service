package in.wynk.payment.constant;

public enum Validity {
    MONTHLY(30), QUARTERLY(90) , YEARLY(365);
    int days;
    Validity(int days){
        this.days = days;
    }
    public int getValidity(){
        return days;
    }
    public static int getValidity(String validity){
        for(Validity v : values()){
            if(v.name().equalsIgnoreCase(validity)){
                return v.getValidity();
            }
        }
        return -1;
    }
}
