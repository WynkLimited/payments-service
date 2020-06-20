package in.wynk.payment.dto.Itunes;

import in.wynk.payment.enums.ItunesReceiptType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ItunesIdUidMapping")
@Getter
@Setter
@NoArgsConstructor
public class ItunesIdUidMapping {

    @Id
    private Key         key;
    private String      itunesId;
    private String secret;
    private ItunesReceiptType type;

    @Setter
    @Getter
    public static class Key {

        private String uid;
        private int    productId;

        public Key(String uid, int productId) {

            this.uid = uid;
            this.productId = productId;
        }

    }
}