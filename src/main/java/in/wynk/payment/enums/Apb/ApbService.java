package in.wynk.payment.enums.Apb;

public enum ApbService {
    NB("NetBanking"),
    WT("Wallet");

    String name;

    ApbService(String name){
        this.name = name;
    }
}
