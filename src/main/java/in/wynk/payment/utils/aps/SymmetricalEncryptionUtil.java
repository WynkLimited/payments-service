package in.wynk.payment.utils.aps;

import in.wynk.payment.dto.aps.common.Secret;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SymmetricalEncryptionUtil {

    private static final Map<Secret, byte[]> secretMap = new ConcurrentHashMap();

    public static byte[] encrypt(byte[] plainBytes, String secretKey, String salt) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, InvalidKeyException {
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        Secret secret = new Secret();
        secret.setSecretKey(secretKey);
        secret.setSalt(salt);
        byte[] key = generateAESKey(secret);
        byte[] cipherText = processSourceBytes(1, iv, plainBytes, key);
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + iv.length + cipherText.length);
        byteBuffer.putInt(iv.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);
        return byteBuffer.array();
    }

    public static byte[] decrypt(byte[] encryptedBytes, String secretKey, String salt) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, InvalidKeyException {
        Secret secret = new Secret();
        secret.setSecretKey(secretKey);
        secret.setSalt(salt);
        byte[] key = generateAESKey(secret);
        ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
        int ivLength = byteBuffer.getInt();
        if (ivLength >= 12 && ivLength < 16) {
            byte[] iv = new byte[ivLength];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);
            return processSourceBytes(2, iv, cipherText, key);
        } else {
            throw new IllegalArgumentException("invalid iv length");
        }
    }

    public static byte[] generateAESKey(String secret, String salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), 65536, 256);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return f.generateSecret(spec).getEncoded();
    }

    public static byte[] generateAESKey(Secret secret) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (!secretMap.containsKey(secret)) {
            KeySpec spec = new PBEKeySpec(secret.getSecretKey().toCharArray(), secret.getSalt().getBytes(StandardCharsets.UTF_8), 65536, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] key = f.generateSecret(spec).getEncoded();
            secretMap.put(secret, key);
        }

        return (byte[]) secretMap.get(secret);
    }

    public static String generateSalt() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[8];
        secureRandom.nextBytes(salt);
        return EncodingUtil.base64Encode(salt);
    }

    private static byte[] processSourceBytes(int cipherMode, byte[] iv, byte[] sourceBytes, byte[] secretKey) throws InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "AES");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(cipherMode, secretKeySpec, parameterSpec);
        return cipher.doFinal(sourceBytes);
    }
}
