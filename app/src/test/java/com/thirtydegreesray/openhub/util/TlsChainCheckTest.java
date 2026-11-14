package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import java.util.List;

/**
 * TlsChainCheck with a test PKI made with openssl (EC P-256): "Test Root" plays a known root,
 * "HttpCanary Root" an interception proxy. Valid from 2026-11-14, leaves until 2029-02.
 */
public class TlsChainCheckTest {

    /** 2027-06-01T00:00:00Z: inside every certificate's validity. */
    private static final long NOW = 1811808000000L;

    private static final String ROOT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBjDCCATOgAwIBAgIUQh3zvRq+OuY3DPj8RBnjyuDVaKcwCgYIKoZIzj0EAwIw\n" +
            "FDESMBAGA1UEAwwJVGVzdCBSb290MB4XDTI2MTExNDE2MDgxNloXDTQ2MTEwOTE2\n" +
            "MDgxNlowFDESMBAGA1UEAwwJVGVzdCBSb290MFkwEwYHKoZIzj0CAQYIKoZIzj0D\n" +
            "AQcDQgAEtr7BA23L7HThDjcfns3XMg5+OTEM1clT7q199KGe6Xb+GmlsIoUYLbiP\n" +
            "I99al8+3Hoqy0Kt413ugaQUC1l+/yaNjMGEwHQYDVR0OBBYEFOOtdacGiF2mbNG6\n" +
            "OQOw3fWcEvDGMB8GA1UdIwQYMBaAFOOtdacGiF2mbNG6OQOw3fWcEvDGMA8GA1Ud\n" +
            "EwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMCA0cAMEQCIAbl\n" +
            "tmP1f9iTZsubAmVVjaP5VrfpY0Ynwr5GMRZGww0SAiBfv9xeuDjpLWgUBW1cAALd\n" +
            "GaFYoRdr0CuTp92gAe5XnA==\n" +
            "-----END CERTIFICATE-----\n";
    private static final String INTER =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBlTCCATugAwIBAgIUD49c11rGQ1u9vS/hL+d3ELGwDNswCgYIKoZIzj0EAwIw\n" +
            "FDESMBAGA1UEAwwJVGVzdCBSb290MB4XDTI2MTExNDE2MDgxNloXDTM2MTExMTE2\n" +
            "MDgxNlowHDEaMBgGA1UEAwwRVGVzdCBJbnRlcm1lZGlhdGUwWTATBgcqhkjOPQIB\n" +
            "BggqhkjOPQMBBwNCAARscJbmLLc5CMeYEU5xzQ2q9kU5KGURArrxSO55FUuhNBpd\n" +
            "uzooViqsXLv7skoOfJBpk6qx/f0D1j9z8c9Az3tUo2MwYTAPBgNVHRMBAf8EBTAD\n" +
            "AQH/MA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUQeDGMQT90iIRbid2qkhpwqFE\n" +
            "jS4wHwYDVR0jBBgwFoAU4611pwaIXaZs0bo5A7Dd9ZwS8MYwCgYIKoZIzj0EAwID\n" +
            "SAAwRQIgXZ3IcueT3olVfGXhql824VIFC3ijqrIL1GM8xhNuxxwCIQD3P/Jouf0x\n" +
            "UxcGTFERkBiy6mTaPV5FBsHcDG01n9Qqrw==\n" +
            "-----END CERTIFICATE-----\n";
    private static final String LEAF_API =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBojCCAUigAwIBAgIUCkwBPeK+Cgqs0urMrG+5eESexS0wCgYIKoZIzj0EAwIw\n" +
            "HDEaMBgGA1UEAwwRVGVzdCBJbnRlcm1lZGlhdGUwHhcNMjYxMTE0MTYwODE2WhcN\n" +
            "MjkwMjE2MTYwODE2WjAZMRcwFQYDVQQDDA5hcGkuZ2l0aHViLmNvbTBZMBMGByqG\n" +
            "SM49AgEGCCqGSM49AwEHA0IABHGRVSNxnFGQFkX931sIKraDuG67YQRJN/qFjvBG\n" +
            "v+SqwdgjmnD0aBQZuam/nR1TCbXb8R3QAi03PX35YQQJcP+jazBpMAwGA1UdEwEB\n" +
            "/wQCMAAwGQYDVR0RBBIwEIIOYXBpLmdpdGh1Yi5jb20wHQYDVR0OBBYEFGbJKAYN\n" +
            "fYlWggs6dJD+Cha8p1pNMB8GA1UdIwQYMBaAFEHgxjEE/dIiEW4ndqpIacKhRI0u\n" +
            "MAoGCCqGSM49BAMCA0gAMEUCIQDQwkxOUaDf+CNuufKueV0r31emSGs4NuPtcuaN\n" +
            "BuZp2wIgTPCINnIkT7Q5Qj+N7WJPoSvpj7Oiy6xwESL193w117M=\n" +
            "-----END CERTIFICATE-----\n";
    private static final String LEAF_WILD =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBpzCCAU6gAwIBAgIUCkwBPeK+Cgqs0urMrG+5eESexS4wCgYIKoZIzj0EAwIw\n" +
            "HDEaMBgGA1UEAwwRVGVzdCBJbnRlcm1lZGlhdGUwHhcNMjYxMTE0MTYwODE2WhcN\n" +
            "MjkwMjE2MTYwODE2WjAVMRMwEQYDVQQDDApnaXRodWIuY29tMFkwEwYHKoZIzj0C\n" +
            "AQYIKoZIzj0DAQcDQgAEa6ZsYueN/HDju6wX2E5boGyHQd6m0EAF7OBuJpnc6ROp\n" +
            "FDLvZSJg3AiZAMzNswF9COEItivyJ5tW/Yxo5fnREKN1MHMwDAYDVR0TAQH/BAIw\n" +
            "ADAjBgNVHREEHDAaggpnaXRodWIuY29tggwqLmdpdGh1Yi5jb20wHQYDVR0OBBYE\n" +
            "FDK4C3IV3FwNKoSDxec9oWayJJ3/MB8GA1UdIwQYMBaAFEHgxjEE/dIiEW4ndqpI\n" +
            "acKhRI0uMAoGCCqGSM49BAMCA0cAMEQCICiV0FuOP1WFvgP4sqvk22E+djhcFoAI\n" +
            "E2WzJj/I+ljBAiB/UWVBG6Y5JM2VBgVPtlBiF2ZoNKTA3qU/yEnGqxFt9g==\n" +
            "-----END CERTIFICATE-----\n";
    private static final String EVIL_ROOT =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBiTCCAS+gAwIBAgIUbogUJLO5COe8eRw/HuFKTd9rMqAwCgYIKoZIzj0EAwIw\n" +
            "GjEYMBYGA1UEAwwPSHR0cENhbmFyeSBSb290MB4XDTI2MTExNDE2MDgxNloXDTQ2\n" +
            "MTEwOTE2MDgxNlowGjEYMBYGA1UEAwwPSHR0cENhbmFyeSBSb290MFkwEwYHKoZI\n" +
            "zj0CAQYIKoZIzj0DAQcDQgAEFocCgDY9O35udQqVhTDUIrs4kY3fZGV1fu/PdoKF\n" +
            "C0XOH5AB2CXnsb+H7W+QNw4/Xm8NZz26nHqzzg0rc2JRrqNTMFEwHQYDVR0OBBYE\n" +
            "FK0KUAJHvjsMNLKSClU53XLupPgEMB8GA1UdIwQYMBaAFK0KUAJHvjsMNLKSClU5\n" +
            "3XLupPgEMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIgPDatFc3b\n" +
            "/7Su2k5pYdoDR9c6hzNpI6cY9+gNw0kDwAcCIQDjp3hFdLqFT2q6MymGjKmr97lI\n" +
            "9RwHuQQjigAq7BwK9g==\n" +
            "-----END CERTIFICATE-----\n";
    private static final String EVIL_LEAF =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBnzCCAUagAwIBAgIUE+1S0m73D5d4UBdNeYPwmmK6fvUwCgYIKoZIzj0EAwIw\n" +
            "GjEYMBYGA1UEAwwPSHR0cENhbmFyeSBSb290MB4XDTI2MTExNDE2MDgxNloXDTI5\n" +
            "MDIxNjE2MDgxNlowGTEXMBUGA1UEAwwOYXBpLmdpdGh1Yi5jb20wWTATBgcqhkjO\n" +
            "PQIBBggqhkjOPQMBBwNCAASi7XrgYmn3GIfcrEroIe6Dm1WCGi7SaqR9W74aLKah\n" +
            "ZhOU5q5mES2GGIJBaVCcLTGekULcNccFq+9BKkhu/QxZo2swaTAMBgNVHRMBAf8E\n" +
            "AjAAMBkGA1UdEQQSMBCCDmFwaS5naXRodWIuY29tMB0GA1UdDgQWBBSAhcwzkRG/\n" +
            "fticBz3iyDRXWod4SjAfBgNVHSMEGDAWgBStClACR747DDSykgpVOd1y7qT4BDAK\n" +
            "BggqhkjOPQQDAgNHADBEAiB+TcrqGFvNuw0kUfVVun2ZPHGKm0Hxw9LGhZih9OK8\n" +
            "FgIgO5oUqIwPnY+i9LFdlNbx6pQGI5+TOmi2CdK4tytV8TQ=\n" +
            "-----END CERTIFICATE-----\n";
    private static final String ATTACKER_LEAF =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBnjCCAUSgAwIBAgIUCkwBPeK+Cgqs0urMrG+5eESexS8wCgYIKoZIzj0EAwIw\n" +
            "HDEaMBgGA1UEAwwRVGVzdCBJbnRlcm1lZGlhdGUwHhcNMjYxMTE0MTYwODE2WhcN\n" +
            "MjkwMjE2MTYwODE2WjAXMRUwEwYDVQQDDAxldmlsLmV4YW1wbGUwWTATBgcqhkjO\n" +
            "PQIBBggqhkjOPQMBBwNCAATqbc6YsuyMZQbjxUIY+lhWyy6Qe665dgvC81eQ4YzQ\n" +
            "xTLPx46z9x7ywRryIi3PvtAf3ZLkYREFpYpkxiRrJBKMo2kwZzAMBgNVHRMBAf8E\n" +
            "AjAAMBcGA1UdEQQQMA6CDGV2aWwuZXhhbXBsZTAdBgNVHQ4EFgQUfcNLLfKWcmC1\n" +
            "eQcXCzYgo9rpfywwHwYDVR0jBBgwFoAUQeDGMQT90iIRbid2qkhpwqFEjS4wCgYI\n" +
            "KoZIzj0EAwIDSAAwRQIhAJjoKF9ZSrx9tDHuxdsrTjXENSTrXh/qvb6H52YryyjA\n" +
            "AiAuzOuCjijDgTApc/D118uHWLZ67a/yMsi5lTn00ELi8Q==\n" +
            "-----END CERTIFICATE-----\n";
    private static final String FAKE_ON_LEAF =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIBnTCCAUOgAwIBAgIUC9T8CCc69rfKnokKgplrGxzDMjcwCgYIKoZIzj0EAwIw\n" +
            "FzEVMBMGA1UEAwwMZXZpbC5leGFtcGxlMB4XDTI2MTExNDE2MDgxNloXDTI5MDIx\n" +
            "NjE2MDgxNlowGTEXMBUGA1UEAwwOYXBpLmdpdGh1Yi5jb20wWTATBgcqhkjOPQIB\n" +
            "BggqhkjOPQMBBwNCAAR7AGaBVDk+nBC6QVxPEkVsAWeWLumY70Q10rFwEKafKTi+\n" +
            "pfHGShIbvan+VNAGbCQfEmkmVTBwY4k6zKdAaKMzo2swaTAMBgNVHRMBAf8EAjAA\n" +
            "MBkGA1UdEQQSMBCCDmFwaS5naXRodWIuY29tMB0GA1UdDgQWBBQR04GC50+MCVWn\n" +
            "9giKfxrQxRpnuTAfBgNVHSMEGDAWgBR9w0st8pZyYLV5BxcLNiCj2ul/LDAKBggq\n" +
            "hkjOPQQDAgNIADBFAiAp6hlS3l3nzfpXSAJNpEelimfyZSmBvAOqZJTkMqediwIh\n" +
            "AM2DdBZWF98KLkdII1mBfvFa5krDbdR0ytFD6BoTSmUI\n" +
            "-----END CERTIFICATE-----\n";

    private static final String ROOT_SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtr7BA23L7HThDjcfns3XMg5+OTEM1clT7q199KGe6Xb+GmlsIoUYLbiPI99al8+3Hoqy0Kt413ugaQUC1l+/yQ==";

    private static Certificate cert(String pem) throws Exception {
        return CertificateFactory.getInstance("X.509").generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
    }

    private static List<TlsChainCheck.Key> anchors() {
        List<TlsChainCheck.Key> keys = TlsChainCheck.keys(Collections.singletonList(
                new TlsAnchors.Anchor("Test Root", "EC", ROOT_SPKI)));
        assertEquals(1, keys.size());
        return keys;
    }

    private static TlsChainCheck.Result check(String host, long now, String... pems) throws Exception {
        Certificate[] chain = new Certificate[pems.length];
        for (int i = 0; i < pems.length; i++) chain[i] = cert(pems[i]);
        return TlsChainCheck.check(host, chain, anchors(), now);
    }

    @Test
    public void genuineChainPasses() throws Exception {
        assertEquals(TlsChainCheck.Result.OK, check("api.github.com", NOW, LEAF_API, INTER));
    }

    @Test
    public void rootSentAlongPasses() throws Exception {
        assertEquals(TlsChainCheck.Result.OK, check("api.github.com", NOW, LEAF_API, INTER, ROOT));
    }

    @Test
    public void wildcardNamesOneLabel() throws Exception {
        assertEquals(TlsChainCheck.Result.OK, check("uploads.github.com", NOW, LEAF_WILD, INTER));
        assertEquals(TlsChainCheck.Result.OK, check("github.com", NOW, LEAF_WILD, INTER));
        assertEquals(TlsChainCheck.Result.WRONG_HOST, check("a.b.github.com", NOW, LEAF_WILD, INTER));
    }

    @Test
    public void interceptionProxyIsRefused() throws Exception {
        assertEquals(TlsChainCheck.Result.UNKNOWN_ROOT, check("api.github.com", NOW, EVIL_LEAF));
        assertEquals(TlsChainCheck.Result.UNKNOWN_ROOT,
                check("api.github.com", NOW, EVIL_LEAF, EVIL_ROOT));
    }

    @Test
    public void fakeLeafWithAGenuineIntermediateBehindItIsRefused() throws Exception {
        // a proxy's own certificate followed by a real intermediate: not signed by it
        assertEquals(TlsChainCheck.Result.BROKEN_CHAIN,
                check("api.github.com", NOW, EVIL_LEAF, INTER));
    }

    @Test
    public void certificateSignedWithAnotherSitesLeafIsRefused() throws Exception {
        // someone with a real certificate for his own site signs "api.github.com" with it
        assertEquals(TlsChainCheck.Result.BROKEN_CHAIN,
                check("api.github.com", NOW, FAKE_ON_LEAF, ATTACKER_LEAF, INTER));
    }

    @Test
    public void genuineCertificateOfAnotherSiteIsRefused() throws Exception {
        assertEquals(TlsChainCheck.Result.WRONG_HOST,
                check("api.github.com", NOW, ATTACKER_LEAF, INTER));
    }

    @Test
    public void expiredOrNotYetValidIsRefused() throws Exception {
        long after = 1924992000000L; // 2031-01-01
        assertEquals(TlsChainCheck.Result.NOT_VALID_NOW,
                check("api.github.com", after, LEAF_API, INTER));
        long before = 1767225600000L; // 2026-01-01
        assertEquals(TlsChainCheck.Result.NOT_VALID_NOW,
                check("api.github.com", before, LEAF_API, INTER));
    }

    @Test
    public void nothingSentIsRefused() {
        assertEquals(TlsChainCheck.Result.NO_CERTIFICATES,
                TlsChainCheck.check("api.github.com", null, anchors(), NOW));
        assertEquals(TlsChainCheck.Result.NO_CERTIFICATES,
                TlsChainCheck.check("api.github.com", new Certificate[0], anchors(), NOW));
    }

    @Test
    public void hostPatterns() {
        assertTrue(TlsChainCheck.hostMatches("api.github.com", "api.github.com"));
        assertTrue(TlsChainCheck.hostMatches("*.github.com", "api.github.com"));
        assertFalse(TlsChainCheck.hostMatches("*.github.com", "github.com"));
        assertFalse(TlsChainCheck.hostMatches("*.github.com", "evilgithub.com"));
        assertFalse(TlsChainCheck.hostMatches("*.github.com", "a.b.github.com"));
        assertFalse(TlsChainCheck.hostMatches("github.com", "api.github.com"));
    }
}
