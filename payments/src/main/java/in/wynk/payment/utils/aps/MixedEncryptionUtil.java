package in.wynk.payment.utils.aps;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.payment.dto.aps.common.RSA;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.StringJoiner;

public class MixedEncryptionUtil {
    private final RSA rsa;
    private final String DATA_SPLIT = "data=";
    private final String KEY_SPLIT = ",";

    public String encryptObj(Object message) {
        try {
            return this.encrypt(JsonConversionUtil.convertToJson(message));
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

    public String encrypt(String message) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, BadPaddingException, InvalidKeyException {
        String secret = RandomUtil.generateRandomString(8);
        String salt = RandomUtil.generateRandomString(8);
        String data = Base64.getEncoder().encodeToString(SymmetricalEncryptionUtil.encrypt(message.getBytes(StandardCharsets.UTF_8), secret, salt));
        StringJoiner rawKey = new StringJoiner(",");
        rawKey.add(secret);
        rawKey.add(salt);
        String key = this.rsa.encrypt(rawKey.toString());
        return key + "data=" + data;
    }

    public <T> T decryptObj(String encryptedMessage, Class<T> clazz) {
        try {
            return JsonConversionUtil.convertFromJson(this.decrypt(encryptedMessage), clazz);
        } catch (Exception var4) {
            throw new RuntimeException(var4);
        }
    }

    public <T> T decryptObj(String encryptedMessage, TypeReference<T> typeReference) {
        try {
            return JsonConversionUtil.convertFromJson(this.decrypt(encryptedMessage), typeReference);
        } catch (Exception var4) {
            throw new RuntimeException(var4);
        }
    }

    public String decrypt(String encryptedMessage) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, BadPaddingException, InvalidKeyException {
        String key = encryptedMessage.split("data=")[0];
        String data = encryptedMessage.split("data=")[1];
        String rawKey = this.rsa.decrypt(key);
        String secret = rawKey.split(",")[0];
        String salt = rawKey.split(",")[1];
        return new String(SymmetricalEncryptionUtil.decrypt(Base64.getDecoder().decode(data), secret, salt));
    }

    public MixedEncryptionUtil(RSA rsa) {
        this.rsa = rsa;
    }
}
