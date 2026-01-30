package in.wynk.payment.utils;

import com.amazonaws.SdkBaseException;
import com.amazonaws.SdkClientException;
import com.amazonaws.annotation.GuardedBy;
import com.amazonaws.http.apache.utils.ApacheUtils;
import com.amazonaws.internal.FIFOCache;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.util.SignatureChecker;
import com.amazonaws.util.IOUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.exception.SnsHttpException;
import lombok.SneakyThrows;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.PublicKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class SnsSignatureVerifier {
    private static final String SIGNING_CERT_URL = "SigningCertURL";
    private final HttpClient client;
    private final DefaultHostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
    private final SignatureChecker signatureChecker = new SignatureChecker();
    private final ObjectMapper MAPPER = new ObjectMapper();
    private final SigningCertUrlVerifier signingCertUrlVerifier;
    private final String expectedCertCommonName;
    @GuardedBy("this")
    private final FIFOCache<PublicKey> certificateCache = new FIFOCache(2);

    public SnsSignatureVerifier() {
        this.client = HttpClientBuilder.create().build();
        this.expectedCertCommonName = this.resolveCertCommonName("us-east-1");
        this.signingCertUrlVerifier = new SigningCertUrlVerifier(RegionUtils.getRegion("us-east-1").getServiceEndpoint("sns"));
    }

    String resolveCertCommonName(String regionStr) {
        Regions region;
        try {
            region = Regions.fromName(regionStr);
        } catch (IllegalArgumentException var4) {
            return "sns." + RegionUtils.getRegion(regionStr).getDomain();
        }

        switch (region) {
            case CN_NORTH_1:
                return "sns-cn-north-1.amazonaws.com.cn";
            case CN_NORTHWEST_1:
                return "sns-cn-northwest-1.amazonaws.com.cn";
            case GovCloud:
            case US_GOV_EAST_1:
                return "sns-us-gov-west-1.amazonaws.com";
            case AP_EAST_1:
            case ME_SOUTH_1:
            case EU_SOUTH_1:
            case AF_SOUTH_1:
                return "sns-signing." + regionStr + ".amazonaws.com";
            default:
                return "sns.amazonaws.com";
        }
    }

    @SneakyThrows
    public boolean verifySignature(String message) {
        final JsonNode messageJson = MAPPER.readTree(message);
        return this.signatureChecker.verifySignature(this.toMap(messageJson), this.fetchPublicKey(messageJson));
    }

    private synchronized PublicKey fetchPublicKey(JsonNode messageJson) {
        URI certUrl = URI.create(messageJson.get("SigningCertURL").asText());
        PublicKey publicKey = (PublicKey) this.certificateCache.get(certUrl.toString());
        if (publicKey == null) {
            publicKey = this.createPublicKey(this.downloadCertWithRetries(certUrl));
            this.certificateCache.add(certUrl.toString(), publicKey);
        }

        return publicKey;
    }

    private String downloadCertWithRetries(URI certUrl) {
        try {
            return this.downloadCert(certUrl);
        } catch (SdkBaseException var3) {
            if (this.isRetryable(var3)) {
                return this.downloadCert(certUrl);
            } else {
                throw var3;
            }
        }
    }

    private boolean isRetryable(SdkBaseException e) {
        if (e.getCause() instanceof IOException) {
            return true;
        } else if (e instanceof SnsHttpException) {
            return ((SnsHttpException) e).getStatusCode() / 100 == 5;
        } else {
            return false;
        }
    }

    private String downloadCert(URI certUrl) {
        try {
            this.signingCertUrlVerifier.verifyCertUrl(certUrl);
            HttpResponse response = this.client.execute(new HttpGet(certUrl));
            if (ApacheUtils.isRequestSuccessful(response)) {
                String var3;
                try {
                    var3 = IOUtils.toString(response.getEntity().getContent());
                } finally {
                    response.getEntity().getContent().close();
                }

                return var3;
            } else {
                throw new SnsHttpException("Could not download the certificate from SNS", response);
            }
        } catch (IOException var8) {
            throw new SdkClientException("Unable to download SNS certificate from " + certUrl.toString(), var8);
        }
    }

    private Map<String, String> toMap(JsonNode messageJson) {
        Map<String, String> fields = new HashMap(messageJson.size());
        Iterator<Map.Entry<String, JsonNode>> jsonFields = messageJson.fields();

        while (jsonFields.hasNext()) {
            Map.Entry<String, JsonNode> next = (Map.Entry) jsonFields.next();
            fields.put(next.getKey(), ((JsonNode) next.getValue()).asText());
        }

        return fields;
    }

    private PublicKey createPublicKey(String cert) {
        try {
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            InputStream stream = new ByteArrayInputStream(cert.getBytes(Charset.forName("UTF-8")));
            X509Certificate cer = (X509Certificate) fact.generateCertificate(stream);
            this.validateCertificate(cer);
            return cer.getPublicKey();
        } catch (SdkBaseException var5) {
            throw var5;
        } catch (Exception var6) {
            throw new SdkClientException("Could not create public key from certificate", var6);
        }
    }

    private void validateCertificate(X509Certificate cer) throws CertificateExpiredException, CertificateNotYetValidException {
        this.verifyHostname(cer);
        cer.checkValidity();
    }

    private void verifyHostname(X509Certificate cer) {
        try {
            this.hostnameVerifier.verify(this.expectedCertCommonName, cer);
        } catch (SSLException var3) {
            throw new SdkClientException("Certificate does not match expected common name: " + this.expectedCertCommonName, var3);
        }
    }

    private class SigningCertUrlVerifier {
        private final String endpoint;

        SigningCertUrlVerifier(String endpoint) {
            this.endpoint = endpoint;
        }

        void verifyCertUrl(URI certUrl) {
            this.assertIsHttps(certUrl);
            this.assertIsFromSns(certUrl);
        }

        private void assertIsHttps(URI certUrl) {
            if (!"https".equals(certUrl.getScheme())) {
                throw new SdkClientException("SigningCertURL was not using HTTPS: " + certUrl.toString());
            }
        }

        private void assertIsFromSns(URI certUrl) {
            if (!this.endpoint.equals(certUrl.getHost())) {
                throw new SdkClientException(String.format("SigningCertUrl does not match expected endpoint. Expected %s but received endpoint was %s.", this.endpoint, certUrl.getHost()));
            }
        }
    }

}
