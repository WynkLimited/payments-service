package in.wynk.payment.dto.Itunes;

import in.wynk.payment.enums.ItunesReceiptType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ItunesIdUidMapping")
public class ItunesIdUidMapping {

    @Id
    private Key         key;
    private String      itunesId;
    private String secret;
    private ItunesReceiptType type;



    public Key getKey() {
        return key;
    }

    public void setKey(Key key) {
        this.key = key;
    }

    public String getItunesId() {
        return itunesId;
    }

    public void setItunesId(String itunesId) {
        this.itunesId = itunesId;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }


    public ItunesReceiptType getType() {
        return type;
    }

    public void setType(ItunesReceiptType type) {
        this.type = type;
    }

    public static class Key {

        private String uid;
        private int    productId;

        public Key(String uid, int productId) {

            this.uid = uid;
            this.productId = productId;
        }

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }


        public int getProductId() {
            return productId;
        }

        public void setProductId(int productId) {
            this.productId = productId;
        }

    }
}