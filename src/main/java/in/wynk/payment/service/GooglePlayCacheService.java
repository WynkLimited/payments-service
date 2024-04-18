package in.wynk.payment.service;

import in.wynk.common.dto.ICacheService;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.gpbs.GooglePlayConstant;
import in.wynk.payment.dto.gpbs.request.GoogleApiRequest;
import in.wynk.payment.dto.gpbs.response.receipt.GoogleApiResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static in.wynk.common.constant.BaseConstants.ACCESS_TOKEN_IN_MEMORY_CACHE_CRON;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service(BeanConstant.GOOGLE_PLAY_BILLING_CACHE_SERVICE)
public class GooglePlayCacheService implements ICacheService<String, String> {

    @Autowired
    @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;

    @Value("${payment.googlePlay.authorizationUrl}")
    private String authorizationUrl;
    @Value("${payment.googlePlay.tokenUrl}")
    private String tokenUrl;

    @Value("${payment.googlePlay.music.privateKey}")
    private String musicPrivateKey;
    @Value("${payment.googlePlay.music.privateKeyId}")
    private String musicPrivateKeyId;
    @Value("${payment.googlePlay.music.clientEmail}")
    private String musicClientEmail;

    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock writeLock = lock.writeLock();

    @PostConstruct
    @Scheduled(fixedDelay = ACCESS_TOKEN_IN_MEMORY_CACHE_CRON, initialDelay = ACCESS_TOKEN_IN_MEMORY_CACHE_CRON)
    private void init () {
        generateJwtTokenAndGetAccessToken(GooglePlayConstant.SERVICE_MUSIC, musicClientEmail, musicPrivateKeyId, musicPrivateKey); //for music and airteltv as tokens will be same as same account
    }

    public void generateJwtTokenAndGetAccessToken (String client, String clientEmail, String privateKeyId, String privateKey) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", clientEmail);
        claims.put("sub", clientEmail);
        claims.put("scope", "https://www.googleapis.com/auth/androidpublisher");
        claims.put("aud", "https://oauth2.googleapis.com/token");

        Map<String, Object> headers = new HashMap<>();
        headers.put("type", "JWT");
        headers.put("kid", privateKeyId);

        String token = create(headers, claims, privateKey);

        loadAccessToken(client, GRANT_TYPE, token);
    }

    private String create (Map<String, Object> headers, Map<String, Object> claims, String privateKey) {
        Key key = getPrivateKeyFromString(privateKey);
        return Jwts.builder()
                .addClaims(claims)
                .setHeader(headers)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600 * 1000))
                .signWith(SignatureAlgorithm.RS256, key)
                .compact();
    }

    private void loadAccessToken (String client, String grantType, String jwt) {
        GoogleApiRequest apiRequest = GoogleApiRequest.builder()
                .grantType(grantType)
                .assertion(jwt).build();
        ResponseEntity<GoogleApiResponse> responseEntity = restTemplate.exchange(RequestEntity.post(URI.create(tokenUrl)).body(apiRequest), GoogleApiResponse.class);
        if (Objects.nonNull(responseEntity.getBody()) && writeLock.tryLock()) {
            try {
                GoogleApiResponse googleApiResponse = responseEntity.getBody();
                tokens.put(client, googleApiResponse.getAccessToken());
                if(GooglePlayConstant.SERVICE_MUSIC.equals(client)) { //credentials are same for music and xStream. So, access token will also be same.
                   tokens.put(GooglePlayConstant.SERVICE_AIRTEL_TV,googleApiResponse.getAccessToken());
                }
            } catch (Throwable th) {
                log.error(BaseLoggingMarkers.APPLICATION_ERROR, "Exception occurred while refreshing google token cache. Exception: {}", th.getMessage(), th);
                throw th;
            } finally {
                writeLock.unlock();
            }
        }
    }

    @Override
    public String get (String key) {
        return tokens.get(key);
    }

    @Override
    public String save (String key) {
        throw new WynkRuntimeException(WynkErrorType.UT025);
    }

    @Override
    public Collection<String> getAll () {
        return tokens.values();
    }

    @Override
    public boolean containsKey (String key) {
        return tokens.containsKey(key);
    }

    private static PrivateKey getPrivateKeyFromString (final String key) {

        String privateKeyContent = key.replaceAll("\\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "");
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            byte[] decode = Base64.getDecoder().decode(privateKeyContent);
            PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(decode);
            return kf.generatePrivate(keySpecPKCS8);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return null;
        }
    }
}