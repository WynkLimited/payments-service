package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.core.type.TypeReference;
import in.wynk.payment.utils.aps.JsonConversionUtil;

import javax.crypto.Cipher;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class RSA {
    private static final String KEY_FACTORY_RSA_ALGORITHM = "RSA";
    private final String cipherTransformation;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public RSA(String cipherTransformation, String privateKey, String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        this.cipherTransformation = cipherTransformation;
        this.privateKey = this.getPrivate(privateKey);
        this.publicKey = this.getPublic(publicKey);
    }

    public RSA(String cipherTransformation, byte[] privateKey, byte[] publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        this.cipherTransformation = cipherTransformation;
        this.privateKey = this.getPrivate(privateKey);
        this.publicKey = this.getPublic(publicKey);
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

    public String decrypt(String encryptedMessage) {
        return new String(this.decrypt(Base64.getDecoder().decode(encryptedMessage)));
    }

    public byte[] decrypt(byte[] encryptedBytes) {
        try {
            Cipher cipher = Cipher.getInstance(this.cipherTransformation);
            cipher.init(2, this.privateKey);
            return cipher.doFinal(encryptedBytes);
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

    public String encryptObj(Object message) {
        try {
            return this.encrypt(JsonConversionUtil.convertToJson(message));
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

    public String encrypt(String message) {
        return Base64.getEncoder().encodeToString(this.encrypt(message.getBytes()));
    }

    public byte[] encrypt(byte[] decryptedBytes) {
        try {
            Cipher cipher = Cipher.getInstance(this.cipherTransformation);
            cipher.init(1, this.publicKey);
            return cipher.doFinal(decryptedBytes);
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

    private PrivateKey getPrivate(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return key == null ? null : this.getPrivate(Base64.getDecoder().decode(key));
    }

    private PrivateKey getPrivate(byte[] key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (key.length == 0) {
            return null;
        } else {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(key);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        }
    }

    private PublicKey getPublic(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return key == null ? null : this.getPublic(Base64.getDecoder().decode(key));
    }

    private PublicKey getPublic(byte[] key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (key.length == 0) {
            return null;
        } else {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(key);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        }
    }
}
