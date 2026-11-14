package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The public root certificates, as their public keys (SubjectPublicKeyInfo, DER, Base64):
 * every root of Mozilla's list for websites (certifi 2026.10.23) and of Chrome's root store
 * (2026-11-14, all of them were already in Mozilla's list), plus every root of the
 * certificate authorities GitHub allows for its domains (DigiCert, Sectigo, GlobalSign,
 * Let's Encrypt), old ones included. {@link TlsGuard} accepts a connection to GitHub only
 * when the certificates the server sent lead to one of these keys. A proxy's own root is never
 * on such a list. The same keys, as SHA-256 pins, are in res/xml/network_security_config.xml
 * (TlsAnchorsTest checks both lists).
 *
 * The list is wide on purpose and has no end date: GitHub can move to any public certificate
 * authority, and the app keeps working for years without an update. When it is renewed
 * (from certifi's cacert.pem and Chrome's root store), roots are only added, never dropped.
 */
public final class TlsAnchors {

    /** One root: its name (for tests and messages), key type and public key. */
    public static final class Anchor {
        @NonNull public final String name;
        /** "RSA" or "EC". */
        @NonNull public final String algorithm;
        /** SubjectPublicKeyInfo, DER, Base64. */
        @NonNull public final String spki;

        Anchor(@NonNull String name, @NonNull String algorithm, @NonNull String spki) {
            this.name = name;
            this.algorithm = algorithm;
            this.spki = spki;
        }
    }

    private TlsAnchors() {
    }

    public static final List<Anchor> ALL = Collections.unmodifiableList(Arrays.asList(
            new Anchor("AAA Certificate Services", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvkCd9G7h6naHHE1FRI6+RsiDBp3B" +
                    "Kv4YH47kAvrzq11QihYxC5oG0MVwIs1JLVRjzLZuaEYLU+rLTCTAvHJO6vEVrvRUmhIKw3qy" +
                    "M2Di2olV8yJY897cz++DhqKMlE+faPKYkEaEJ8d2v+PMNSyLXgdkZYLASLCokflhn3YgUKiR" +
                    "x2a163hiA1bwihoT6jGjHqCZ/Tj29icyWG8H9Wu4+xQrr7eqzNZjX3OM2gWZqDioyxd4NlGs" +
                    "6Z70eDqNzw/ZQuKYDKsvnw4B3u+fmUnxLd+sdE0bmLVHxeUp0fmQGMdinL6DxyZ7Poolx8Dd" +
                    "neY1aBAgnY/Y3tLDhJwNXugvyQIDAQAB"),
            new Anchor("AC RAIZ FNMT-RCM", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAunGAekyGbn/IE23Axn0cAJePLAwj" +
                    "uxCaQKkat4eI+JtWavvme46Lko6nJV1ZEds2LrdRFx+pCB8EFyRYqjdKGN/lOdRX/dfBLJEB" +
                    "keIi1APAWPx3R+yPPnRDuqw0jU04dmeOsMhvMDNYcVy09Wtu1AFQuBN+bEqjSdEgGe68wCkY" +
                    "Zafe/u/dCpAh5xpnkkIQmF9PMLw+HEW0ENdoQBTAQPrndxd65guPZVs82ZpS27W9nkbPPeuR" +
                    "BQLAlrJ2TE0QljuS+px/D5nfviM1RR4CXP61qJuZJdpe8yLDOfXkKi7Txh/EbKrFHGoBBUov" +
                    "0sXBqDQmXWal0gIh+Ri3BvVOmW+oq0xR6M9QGMV3yDkJLEmSMpmouxcXebBaxeajxFllRzWD" +
                    "XqnoNQuZu+TNIMabSgY5tWj8IrruVYwrTurzseP8tpma1UL6cU0Iz4ceanF9+dO06aVxgXvC" +
                    "TkeWpfZ2haMoj+mAboFTpW1fuEj5wvk2pi5J/7iWwowHs5uIWPzrGxzeLXDil5IwoYnjvFWo" +
                    "J9ZL7ZCti/pjJVktqDXdypczvOXNx53R7O9eDkqQBiZjrbnZNS0HunZlLKxXj330B5TXgQKW" +
                    "XaMHSdV60Ff5G+dTRnWqsHlCy2hxCOlgvTlpzvSvw1ZAx61Sognkb4ZHih/rKCddgyCvBMls" +
                    "VpqLRvUCAwEAAQ=="),
            new Anchor("AC RAIZ FNMT-RCM SERVIDORES SEGUROS", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE9rpXU8jKq982SlIh5JfSg2ee8GVR0F6Hx0exWfJX" +
                    "R5sAApNEF2nbQsexsjoYDrRdjLNmXaE0+TYsSdvzRvyzRGlEE2b918X9rzZNzgNNB3HPr2oF" +
                    "0qJDWgpSbwEDTo6L"),
            new Anchor("ACCVRAIZ1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAm6mrv2FKl68vl2aadF/Q2Zb9z+Lk" +
                    "Zu8fH0czwkSj35reH7VU3RV8aTURb7vIDI5qGB7Yj9kWvBBINlzwY7OQWlwkN9ej1ssJcbnx" +
                    "AXKEsH3bTYDN/NNvyfjatg6C0kWFqBtoqD3o9ERsvaHCywO+jD4TAITfSkjA4yIK6Ok3pxhM" +
                    "sQkNI1Z/BE3ZF4QYpcjaQJRz684OVzwDgTqdCqFXQ2msV215kHjltbQ72LxMjSihp6OnugJO" +
                    "JdEqru2uAyK4ayAPMChUlX/g7s4KZp3RQC1uIq+dGsEFGdJvwPKf+HuzAkL7UKkdLZMPI6vG" +
                    "wQ+S/9CiFfVTCXEc/0UThOYmXvjgiBwK/Ba2qHMGuPBjhAKgxlrs53TfcK6jgyXq1seXh5On" +
                    "xoqKM5dgNxA+lz5uKRXWoQ/RiCwSn2+qpMZC60Gi45VD0wGFbY67O/MjNsf+O+ChJQdIq8mJ" +
                    "dP8Ij4C/wJZl8+7sS2i9nYjDMbNA8ejP9ji7nOTRf9TlWJt8+tTzDpt1keS6Ui4ZftH1zVoZ" +
                    "/LoG9vtSqEuZBN34+bSLUKNOYonwhyT6g0LBh/rVLSkqWnF6ZGrXJ2BjDdvOSfWNH5CJMhf4" +
                    "c0O40lqThmHW4XUK6nlmdohPcesEJdYKWnqT5blLF0APsba59d5P3OCzrDsRcGCESkNumSDA" +
                    "KXEKwGUCAwEAAQ=="),
            new Anchor("Actalis Authentication Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAp8bEpSmkLO/lGMWwUKNvUTufClrJ" +
                    "wkg4CsIcoBh/kbWHuUA/3R1oHwiD1S0eiKD4j1aPbZkCkpAW1V8IbInX4ay8IMKx4INRimlN" +
                    "AJZaby/ARH6jDuSRzVju3PvHHkVH3Se5CAGfpiEd9UEtL0z9KK3giq0itFZljoZUj5NDKd45" +
                    "RnijMCO6zfB9E1fAXdKDa0hMxKufgFpbOr3JpyI/gCczWw63igxdBzcIy2zSekciRDXFzMwu" +
                    "jt0q7bd9Zg1fYVEiVRvjRuPjPdA1YprbrxTIW6HMiRvhMCb8oJsfgadHHwTrozmSBp+Z07/T" +
                    "6k9QnBn+locePGX2oxgkg4YQ51Q+qDp2JE+BIcXjDwL4k5RHILv+1A7TaLndxHqEguNTVHnd" +
                    "25zS8gebLra8Pu2Fbe8lEfKXGkJh90qX6IuxEAf6ZYGyojnP9zz/GPvG8VqLWeICrHuS0E4U" +
                    "T1lF9gxeKF+w6D9Fz8+vm2/7hNN3WpVvrJSEnu68wEqPSpP4RCHiMUVhUE4Q2OM1fEwZtN4F" +
                    "v6MGn8i1zeQf1xcGDXqVdFUNaBr8EBtiZJ1t4JWgw5QHVw0U5r0F+7if5t+L4sbnfpb2U8WA" +
                    "NFAoWPASUHEXMLrmeGO89LKtmyuy/uE5jF66CyCU3nuDuP/jVo23Eek7jPKxwV2dpAtMK9my" +
                    "GPW1n0sCAwEAAQ=="),
            new Anchor("Amazon Root CA 1", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsniAccp41eNxr0eAUHR9btjXiHb0" +
                    "mWj3WCFg+XSEAS+sAi2G06BDek6ypNA2ugG+jdtIyAcXNkz07ogjxz7rN/W1GfhJaLDe17l2" +
                    "OB1hnqT+gjal5UpW5EXh+f20Fvp02pybNTkv+rAgUAZsetCAsqb5r+xHGY9QOAfcooc5WPi6" +
                    "1an5SGcwlu6UeF5viaNRwDCGZqFFZrpU66PDkflI3P/R6DAtfS10cDXXiCT3nsRZbrtzhxfy" +
                    "MkYouEP6tx2qyrTynyQOLUv3cVxeaf/qlQLLOIquUDhv2/stYhvFxx5U4XfgZ8gPnIcj1j9A" +
                    "IH8ggMSATD47JCaOBK5smsiqDQIDAQAB"),
            new Anchor("Amazon Root CA 2", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEArZafLZxKTEqBeVGZ7IrLa2BRE7xN" +
                    "bQb8sAiN3RkQascmDDXYwG8ghOmUsZuFA8Nb20royPiQdtlbT+NM6AY2TcyarD0MkCuS1AYZ" +
                    "YKw3RHmFgYKtWjfgDcydpkxSdupDnbcE0VD2VeDV0qZJhek36cp+rlyVTUiaP64gWm2Ildk0" +
                    "uFIaQ5Cwv2wFubZ4t+rQ5Do8ElNi/0rye741BakSNOPzZHRiLD0ASVoo/jJEu4fdZScCcTva" +
                    "Svcf2s33IVWQTw/sroLhn2vZRdO78F+H7TwsOYbaP97sclXreaOt2918sLoczvzeTzV2zw/4" +
                    "eB9qNlFGJ2Fb6Z7P8KJVfXwlim8vtMXPhC4r/Q1REGz7Xxu8G37FrjuYATGS/wtX9JqyuVfp" +
                    "q+8NdtHw7vTOhqfgbum0aaHfafYzxmkulxOepYewVxCBN8lTs7t/9pLRnNAY9JJu2oNPpmOZ" +
                    "TKX7Xu8hZHogX2xkhRXLN+liDAsqFtwBLjLaPkv1njr2F0CU756RCIb6vmOoWjPsy3RDlfls" +
                    "aVI2xylv/FUDXB/7n71H6+dJR5ULTokiCUng9WEe8b8uinJugFn/Vzr5dTKjTl/s7Shi2U1z" +
                    "8syBF2Dtzevc26fKxX4CvfJUCFT9tC0JLBdUSpjRVOFRZwjS7W5+bz/SLYFZKWbLkDmVER50" +
                    "J/7d668CAwEAAQ=="),
            new Anchor("Amazon Root CA 3", "EC",
                    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEKZenxkF/wA2b6AEbVsbyUqW6LbIS6NIu1/rJ" +
                    "xdiqbR9zgTs7mGs5fDOlxU6GjoAXaGJFV31EWB2zN+VnCOtm3g=="),
            new Anchor("Amazon Root CA 4", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE0quKN0+jUw3+wYp7S6h7RktjsGL2LRvbCHEh0gDo" +
                    "Y72aJ/vwOW5d6j2lyYGqo1sgmEVdFtv96BBt45zg471fhGLzcGQzoMskL3C6iKEqoHX4ga5i" +
                    "BsSB2zluKbAe+i5c"),
            new Anchor("ANF Secure Server Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA2+trK+ZkVJWCkKNypBkBnZwLgV9z" +
                    "SbqnrPMETnuWC+wR4FumHM4b0g2DHCu4nh1+RTJgDwfpd1h+n2rIYU62JsFMjf9M7zSyH2XY" +
                    "uXj1ralxue9PWB2l3nQgl6HtaEzekhdLvKv/ZZqe+0fZV3LzCaGudkQTbpwtRDm8+cc7pFg9" +
                    "Qb20wkmjyA3Sly8HZVIAp27Ir2js9BSWtlcfVsM5nytt5PM+9jVk2gwcoYRLL0tL4iwknW2T" +
                    "QOu1I44yym9F06iJex7PHvpbQ4vNzagPasoMXrmeR4/w2bYKC1hlFzO5I+R3GX3LSi6Se08v" +
                    "EHexjS9onGLM4FD47JGnVExXCdV2Y8XoZR7ubWrPCZ36fE+tYAj9VpkPFSx7qYCrjGGPSgd2" +
                    "Qt499N2yJDNbuLWjRMmsf3c8HSPsgqmm4sgGTAL+rFyZmQsvEIqm9H/Vh3QNWUlF9vBxXDkp" +
                    "1r9KI4v1XwFj0odzKLVLCvX4q4IsfnMlMh0LYwoXgQD/tnZe57SxQMohu9WAUeVIUmcs0mGJ" +
                    "Bw0PzkJ3wERznERQoNsQCi2VHIGv5BzlFB7xNkEBAi99c6feQsxM6YkNVvefkdQDxmzJj9vY" +
                    "HOBAmF1mmZiAbi3/AcXOy0YfrALGQ+auooQ8xU4ePW3JFEzjLkG7yjm/NjwqGapBh06lzksy" +
                    "ed2QSX8CAwEAAQ=="),
            new Anchor("Atos TrustedRoot Root CA ECC TLS 2021", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEloZYKDcKZ9Cg3iQZGeHkBQcfl+3oZIK59sRxUM6K" +
                    "DP/XtXa7oWyTbIOiaG6l2b4siJVBzV3dscqDY4PMwL502eCdpO5KTlbgmClBk1IQ1SQ4AjJn" +
                    "8ZQSb+/Xxd4u/RmA"),
            new Anchor("Atos TrustedRoot Root CA RSA TLS 2021", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAtoAOxHm9BYx9sKOdTSJNy/BBl01Z" +
                    "4NH+VoyX8te9j2y3I49f1cTYQcvyAh5x5en2XssIKl4w8i1mx4QbZFc4nXUtVsYvYe+W/CBG" +
                    "vevUez8/fEc4BKkbqlLfEzfTFRVOvV98r61jx3ncCHvVoOX3W3WsgFWZkmGbzSoXfduP9LVq" +
                    "6hdKZChmFSlsAvFr1bqjM9xaZ6cF4r9lthawEO3NUDPJcFDsGY6wx/J0W2tExn2WuZgIWWbe" +
                    "KQGb9Cpt0xU6kGpn8bRrZtkh68rZYnxGEFzedUlnnkL5/nWpo63/dgpnQOPF943HhZpZnmKa" +
                    "au1Fh5hnstVKPNe0OwANwI8f4UDErmwh3El+fsqyjW22v5MvoVw+j8rtgI5Y4dtXz4U2OLJx" +
                    "pAmMkokIiEjxQGMYsluMWuPD0xeqqxmjLBvk1cbiZnrXghmmOxYsL3GHX0WelXOTwkKBIROW" +
                    "1527k2gV+p2kHYzygeBYBr3JtuP2iV2J+axEoctr+hbxx1A9JNr3w+SH1VbxT5Aw+kUJWdo0" +
                    "zuATHAR8ANSbhqRAvNncTFd+rrcztl524WWLZt+NyteYr842mIycg5kDcPOvdO3GDjbnvezB" +
                    "c6eUWsuSZIKmAMFwoW4sKeFYV+xafJlrJaSQOoD0IJ2azsct+bJLKZWD6TWNp0lIpw9MGZHQ" +
                    "9b8Q4HECAwEAAQ=="),
            new Anchor("Autoridad de Certificacion Firmaprofesional CIF A62634068", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAypZrjur4+/GiNeB/TNrgw1LXfbYQ" +
                    "yAJes0MqxE9qssocXSiaeBEaaVlXr7UgQuSLD+bfW6YDki/1EeRi1zJxONkEDHGrPVF+Dwff" +
                    "YwVc6b+Ub8EpgsC02lGwwTy7rTdKXMrxSzYOJKu/w4R3/ahQ9LHnxi/SLVmNegpOlmlSAqo2" +
                    "mOz8+hSDDDcfyZI3f9eBLeXEueA+NP5n9D5m0dP0QM9eYjQPcAY+IBhazvdyGyVsk3QUk6Nz" +
                    "sQ6qhxAjWV8gBRlH7WiOkhLKXfzWK7KSPCDP4V+vIL6gdn925ewahmEzPud7tD+gD46iuWpv" +
                    "uYcmb0FsiKZQ/WpjC/WTFhsZj7Ltm5vJkPUBDN8ZPQ8+OCPJL48M0QL+G1XWTtCNPK9PpPP+" +
                    "ryrTBZ15CKHLVzG0nMiQsmf0GBaTOvxH2NF4ljEfuisMX12ZrWOJWiQgdtjf/atOpiKqnV7m" +
                    "J4p9aCmj54q42hG7Fy2ZnRMkRvfF4tifjn/Hj3RtWrLocvWs7iQQrS8U2v8tmkZxR75C37sB" +
                    "2/R/0yiPMVlb08kCprRSym6X+0PFCCZvivS7/Z8oqg3VRfMTOh3YwHiPQWc8HpRkrnsLxejZ" +
                    "AYg5GpeGZEHVO4cMbvoPxr1IFL85TdSeQbaPlh1jlpPZlQZ4MWieNwY7gIlFYTkjxxtEoxXl" +
                    "HPiSMLsCAwEAAQ=="),
            new Anchor("BJCA Global Root CA1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA8WYIvdnFFWHLhARBpWk3dx3BsHv6" +
                    "w3dIkBNyZNG4fJA1nRh5iOOXATxHgfIOopgNnj834BmykPJGHJKxOmHO+rdGngOG1zNu7fdF" +
                    "jHY33m6WkffXfiuHF9WLNe6EkXJX3GDDw7nnx2ckI09jCmP2Zn1LVac/eGRJaRKX4EwN0wmg" +
                    "MjA6+p/A8pzFEiouHLUEM9qkOBFq3sYY9kc6IkGHIvzEiShU2IylMAr4FxbKrDf9eaeRF3g4" +
                    "ma1Y7bLezIl9A5yziWXn4zuxIoaPBm14B/2REn+waxyJDfm4y3RbB8LI9DXRZGN66W6aKNYw" +
                    "veYb3RWvhOqcx8r1DuryXSmHj2lzOb4uJG9FIazF1GklBoOtekiFEywNBrhseVb8o2cygfVX" +
                    "pcpXQmnpXCRh7+IwGE5EmFVvesKT2Bm23nxHihFOSUfbKJQCC5RKLPkS0E/oMX5ser+mP5s5" +
                    "PQIWoxizZ6xbPyyDK2c5gVy5fpTVZN2ej26u6HxbtNdqR0jXfrPULY5Wdk7PafFuRGzUJOqN" +
                    "JKEYv71X/qmZNbXbEHe4PUi61sHn8SM+19+FnSc81EC9Cgy99eeNJdaBdIdG1Cl1okJs93OJ" +
                    "532/ekof0yLJFVXP3298VdCkiwcRN1+DpiZXpgFbfv5YaAep6XrZuej/UB+rwrTAzujq/Q+9" +
                    "jU24vHECAwEAAQ=="),
            new Anchor("BJCA Global Root CA2", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEncuAkY1TZ7W5ULED+OVJH0EiCbBRUljWKzSPxRJG" +
                    "FMWLLyyE/yxuqNXxCeMDIRTEQz18wSzES2pKzemH4H32Ir76SlG4MIr94d4YEgr2R7fnF78n" +
                    "itRBTJY8YJbB/RUc"),
            new Anchor("Buypass Class 2 Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA18de98EH1Hf7QyH09PVp5O4yAduj" +
                    "hh/kWQ2653WDUuvqHGEVSLsdB8qMrrDclp3qw2CShoIoc5xWBv9LZPAMKjdJteXPDHzu8Uq7" +
                    "czBl89Uvg7Z+4+f1nqtg+dPxnZJ0iuQclqxbgOm19DGHo1H8x36hb45Td9SXwVUzkj4YL3XU" +
                    "rYZJy5WvVAZs2AYTjVv/4SYZWcAkuoFxeZBEUGgklF+4sxHxKUFho0HLIzbVwfEyUBBOf/SG" +
                    "k+yE0468S79cAU4HPdwUipQKpOpz+wtR6BMHGPoO8SvRVBV9POH3tBlCZ2Jed+CiVey22WkX" +
                    "1TqvRO1KxZ7keid85XXXqssl599rCtsPTZNOqKDNey7yWQFqtw24B4F+izgbOOYKV5k97iHo" +
                    "o/UMFt2L7DSOnCocABUXjWiD0nCfGAjNEWjVyWtSzcRGj9y189hXcx7plDkEv9PeON60U+xp" +
                    "HKJ+xI/kG3Ct8qL5+/cWZGZpn0lRouIVGGcGSn/VbLVNszPgYetdvumYDzLXHUs8LloBUpEJ" +
                    "8t/qjdgGQGOqEeT+wzeeFFI/9OLM8mGT0f1na9dSrr9oq0BDoFc1U3jwU/hhQgdkxtdvm0w4" +
                    "DWOsYq82i6JzCg31Ib10qk3qcgNJ28dfHWJjx/3dkewz7vVttG4waN7I1iawdV57tAcgmKF2" +
                    "MrhNbE8CAwEAAQ=="),
            new Anchor("Buypass Class 3 Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEApdoKlRZQ45XyXp12MQYyepvxEHa4" +
                    "AJq1UjbNJEewnxhkvJr2+tV52JBiTCIv3jg91uCo6Rws23gR6Y5oURVyx/Mzh+SgXQtc4FcH" +
                    "KjD1zcQ3dyhNGJHmv9VS/XEtcD7nxsSK4/AoC/R2mKGLh1WyOhP8tz4nN44i46hPKu9guz23" +
                    "OcMOAUeZXRJP20P6V6Ht+Z2+EUcmWxOYq10WirA3HFedRf+Ilja/u8oHe2+HY9fQMmrWXWwM" +
                    "8bNuOeJrMS45ACcU3jjA7BlmhhLonXIWE2RSx6k3HP2CMO2EGB30rlz/cBMA67H1M3pL1lX4" +
                    "BY1LabD1syg2XBTEUXNNawvxNAfbFznX3Ch7a/Wf8y7BTxcqEPPMyujr/WurLpqfLYJuBNRS" +
                    "AZMtPYb8fvzf70IdpmvvuSDG972gp5X9p+aJJNjMjDRs4iMv2RIaIblVkW8LkXkZDK1AiAtw" +
                    "4nrSDthoSLuCEzkQWOnYKgfGEttY29I7VRBHBRVnYn4YY6ZGPwkOVDJevw1ieifvgOjb2UsG" +
                    "WjdaJdAIEnfUbwlQlz3IHcPfjEUwVsbTZKtm88BelpzDxO/DfGuLOnl/s0nPPeKJn6AwS4W5" +
                    "nJQkeY99a6lFaA8r0PHaHMtpuMpJYm3I0GNi3WAPWKqPobwFpWaizxt2soRksUw5UsAwuvCM" +
                    "SwKwtrcCAwEAAQ=="),
            new Anchor("CA Disig Root R2", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAoqPEAAnWhV0tbRT2wsNznjXCcVV+" +
                    "gfurRlDgwXxJeOareVg82v98HJ/YlwJ4PmtBBOlBvb4DLEX2L2TUq12jRz1km+lomsbMGz+6" +
                    "vrKLNAIumFUZ/Ixvql/aTM5NAyGj2NI0k1aWy0wMABY8XxrNyMdspq3TMae86OXhZtbS+wO0" +
                    "QWXJEK4OBWPGgGppMP3S7pDvDSffn5Vz9OEl2mwW3kE4NOqL/NHoBBRhLUF+rMd3TstRVPte" +
                    "khgbBFpoxsnE+rcToJi3ESu31lfMfJ4X0csl/oZOJC5WDHhNngESpiunAWVufGIdhITf6sBr" +
                    "taUqlYPDUxEMcx0LskaQ0UI6zkBula3/xpStbpeEjn1vnoqADUltc+J7kh7D88Hz6y4Fb9kb" +
                    "zzd2BMi0WuQXp8vddh/QGXboLAWz1pw02JbcYYeRBeRECDPB2rkIZdSusjYN67o4ugzlm57r" +
                    "jWbdmc/WiUH2BJKKKSltazoc53V9AnEO88DnvcsZ3Z1gssJmYLaxBO7J5oa5mmZAqOcR7YFF" +
                    "A4v2Z1nowQYRvd3PgAJPZUB4XEdQyJvmH4F75ESoW4Wa4t5a1cf5OkRmS+QyVHzkbJyzDj0X" +
                    "orI0EtZ+sqhJu9F6KEC+ohYf3+Q3HxFz+5AKZUOiDXz4BgFVM32wDbj09a6lQld8NhGMe17E" +
                    "A52MeZ0CAwEAAQ=="),
            new Anchor("Certainly Root E1", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE3m/4fxzf7flHh4axpMCK+IKXgOqPyEpeKn2IaKcB" +
                    "YhSRJHpcnqMXfYqGITQYUBsQ3tA3SybHGWCA6TS9YBk2QNYphwk8kXr2vBMj3VlOBF7PyAIc" +
                    "GFPBMdjaIOlEjeR2"),
            new Anchor("Certainly Root R1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0DbUH+rdq+TRtub7IsDdEw1qeyIT" +
                    "HJc8aGNmMpwDtY2kgYPaeDARz9yyK76Sv47kxBO+pGhM2gJoFnS+st0E5Gsq3TcfYCzb9feh" +
                    "fJW3DHCGLvE671L3zNOb+Yu+Dt8xt51oXJKm9eXzCjS1/3ui5Iehxq8XAO8Dke2pHE5xPdKL" +
                    "bIn0eIbmakmgzrXSsKub9vTULuNy+TbG6xW3JYw6/CUNsyJzIXTISpZhkvUvCxil9K3i7kG9" +
                    "AXn6loyNFwIwtPmveBqMtDYQEAcFcND0MZCKUcWGJnmyEYhexfAKVM1Jpr8CnNJEp+3jeO9G" +
                    "Xm1x0XlwHEZfUenJN9xffml7Qd80ReA7hPShigo2njfMYlLhiQ0o+XojsQ09PZr9nYHvLJDA" +
                    "e0ROu0ngDkpWkrzLtd15F4mR3mGJdJKo4zKFvk6FpEtZyyvFeI5xVNACN5mM5Unq4FRypBEG" +
                    "LwuMwVu+taGwU26cuGCRH1lr+S30lAqXtezFdgNUG2VSukySVlE1oEDYKduuUnY7LTBAm4rQ" +
                    "Qla0t4gBpIc7U5bNoxaP82aqF7HHYODBQwUM7ptbYG8GXIdbJ/lAEZ6cM8G35TVXBX8nzhcg" +
                    "jBz88fvaMSlJ7fULhKdPwfZOwiic+u7grwf7MxF6IU8LIRC2QDqrIjoEnIubhIZymtKnpcS0" +
                    "dZGpKyMCAwEAAQ=="),
            new Anchor("Certigna Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAzRg5ZRpZsepkFg6MlCSVfIPTxTkm" +
                    "3AzvFleN19iso0J/gsrtzVvbDrct7UUIF7LZs8vWF1JyKNuOTp6Ktgv5noSaTXbeIilc0rPS" +
                    "Bj4wOal0o5JWHKFvTAogbZ8jerTG2izkHSzcsyjQE/JMTgJJoVRAnublBaAthMj/mGzQ64oa" +
                    "hAget2gj7iPVcM5tUWkQ7qF6wtEiMcKChdLyVXZQfCV6yYRcC6zdQk4r54KiJInLkLLQ7iO6" +
                    "Zky7YqT5U1pke3yY+qNIng+VrqcY9GrsLgNFr/B0+CrNel3RvkQmMinx8fVszH4CIQufb6Q/" +
                    "vp1T4s99qSx8WBqX4T03NxhmKNJAxVGKjMMtzlOIJFhkMBbFquDWCqZA33j29QR8aROEvNHR" +
                    "pwbPAfdowKhXuzphrQSMk+Ot/PDbRG1Z3ElZrqyamTYwQXt2MyKHo8KShm75cO6uh4eVG8R6" +
                    "vTHz1NLlmf++SOx19XgWHaZwwX88G6GS+8/IPNbFkwqP9VU6dpXOWZiKCZV3MpqDuiwEOpe9" +
                    "1C++12ybosp9bSbJVdXPw3lSCAmZByQtZCVrpiFpm2rddE1rl3pBvasX+ZAXSI82+S3Vxdvu" +
                    "qoVFQfrNOkWxaOY2TJuQV+wjuYcIwsQJ8ZeGKihN4nTA2sSM29/ioRdZziRZdDHaf/0wbdnc" +
                    "4Wrh/F8CAwEAAQ=="),
            new Anchor("certSIGN ROOT CA G2", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwMV1GZF9RHR0h/4OO5bc2AEWzO5j" +
                    "kecLb847CmkafMLjr4KOhtdej1fr0yFZ/Tk3QjC+UOq2D6mI2C4taSHn0TcYTn2R1RZfa1sA" +
                    "wjlDDTaFUrlTZQ8dQuWPzwXT7twMGtm4i3giZ+RpsGjFPORsWkbnzcf678TsS71qpKz9zChR" +
                    "75K0KaurNZpM5MQIxibM+Gmf5JzwKdNc+cYWJZ4jwyDBPQ8/OECw/oJEOKpaGoprY1g4tBXT" +
                    "thFpex5U7owaIqxylz8jWZvJIoTBB0/Mf+JXyhJwu6Zl82l1Y72V+xuXzeSor/bRTqjZinEk" +
                    "zTY9vJbE8WypruXPDW4oDbAOtcpRe3gUwyAvf/sUVeERmf3VCqGeAuNiX+s1Syy4cug+PU+s" +
                    "LLsuhuKjdo/lkyrPpavIXI1LBv8SRqx4yxQHNeCp34vprxVPFolbvfaNxlmuiIUOwYnrH2fF" +
                    "RY7/bTc2K3hmg5FRKz3/UXd2YqHsZz4+gYPgVqlQHx96matjv4QXd/ENO9/3nGGzNZiKOrLs" +
                    "PBo3P36Pks/ZEhRk2hACFUH/T8TrHKPJ+pn3RunhGNmxuDItyxQMUNiDZYPuuVzPywVaTPoZ" +
                    "l2vWXRPTwlxUvDJzoHj18W0ey5+lpp8i3NFRnoJ5ZGApEz6j/U9yaqvi1OW4JFUsREuKiESc" +
                    "yoTTKjsCAwEAAQ=="),
            new Anchor("Certum EC-384 CA", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAExCiOqxhbar5uZDdj5M3sqzr3zKG4DoJJ14Ypn6GU" +
                    "8uNgeJiBeAZN8uyaDldgg5+05hcvGrNdAluJIzzCEQUqp4gTGPNQhNe9NCwniVX/zkzn36Yf" +
                    "KMTwVMO5fLdTrevC"),
            new Anchor("Certum Trusted Network CA", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4/t9o3K6wvDJFIf1awFO4W5AB7pt" +
                    "J11/91sts1rHUV+rpDKmYYe2bg+G0jACl/jXaVehGDldamR5xgFZrDwxSjh80gTSSyjoIF87" +
                    "B6LMTXPb865Px1bVWqeWifrzq2jUI4ZZJ88JJ7ysbnKDHDBy3+Ci6dLhdHUZvSqeexVUBBvX" +
                    "QzmtVSjF4hq79MDkrjhJM8x2hZ85RdKknvISjFH4fOQtf/WsX+sWn7Et0brMkUJ3TCXJkDhv" +
                    "2/DM+44el1k+1WBO5gUo7Ul5E0u6SNsv+XLTOcr+H9g0cvW0QM8xAcPs3hEtF10fuFDRXhmn" +
                    "ad4HMyjKUJX5p1TLVIZQRan5SQIDAQAB"),
            new Anchor("Certum Trusted Network CA 2", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAvfl4+ObVgAxknYYblmRnPyI6HnUB" +
                    "fe/7XGeMycxca6mR5rlC5SBLm9qbe7mZXdmbgEvXhEArJ9PoujC7Pgkap0mV7ytAJMKXx6fu" +
                    "myXvqAoAl4Vaqp3cKcniNQfrcE1K1sGzVrihQTib0fsxf4/gX+GxPw+OFklg1waNGPmqJhCr" +
                    "KtPQ0WeNG0a+RzDVLnLRxWPa52N5RH5LYySJhi40PylMUosqp8DikSiJucBb+R3Z5yet/5oC" +
                    "l8HGUJKbAiy9qbk0WQq/hEr/3/6zn+vZnuCYI+yma3cWKtvMrTscpIfcRnNeGWJoRVfkkIJC" +
                    "u0LW8GHgwaM9ZqNd9BjuiMmNF0UpmTJ1AjHuKSbIawLmtWJFfzcVWiNoidQ+3k4nsPBADLxN" +
                    "F8tNorMe0AZa3faTz1d1mfX6hhpneLO/lv403L3nUlbls+V1e9dBkQXcXWnjlQ1DufyDljmV" +
                    "e2yAWk8TcsbXfSl6RLpSpCrVQUYJIP4ioLZbMI28iQzV13D4h1L92u+sUS4Hs07+0AnacO+Y" +
                    "+lbmbdu1V0vc5SwlFcieLnhO+NqcnoYsylfzGuXIkosagpZ6w7xQEmnYDlpGizrrJvojybaw" +
                    "gb5CAKT41v4wLsfSRvbljnX98sy50IdbzAYQYLuDNbdeZ95H7JlI8aShFf6tjGKOOVVPORa5" +
                    "sWOd/7cCAwEAAQ=="),
            new Anchor("Certum Trusted Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0S2Ou7c26m03kZ9Ok6cF5CkDJc4c" +
                    "gvd8mZ9BBs3to7rA2wkswXzfKX5LZS+Tp9QBawMoGKPYnQXBKthF8ZHe3zvQgAKMzzgP6qdc" +
                    "eBGkwciFXCXT07LnJc8RVJerNcAedhzvAFOfOdwUpSwiJbNycvyNs+U+CB4UKjcLiDzKsPTI" +
                    "wqGuvMG+KWdV4vytWVz+vVcssJCNwu03tnyZiLXVA5o9FQ09OqioRfCVTiVZHc2YabvTzDLJ" +
                    "je+B/q19ibu6YBPKZZVnoPMZ9gNW1GrTJ+KhrYPwShIidxwFc+IZcULA7HVGmpBY4GqOK6VG" +
                    "MASOGbIX476pun9W8SQD17IhKHYONjBMedVBmpqouDW6DDryRBsgiPfFJdc9xuM+Q92H/sTq" +
                    "9VM+TGX/O0rLeFprF18Nx8NPTpoqou1XTSLiRpo/D5E0JH1V44yVN9Ma8AkrLNLJjbQNAKtn" +
                    "KSjYAfUZBLYdvnb+clzEhcrSgEHfBaij1YSQTwvz4D+bGdI3iT/ye1IcjPbh9zwHl4wOolmB" +
                    "DLKQPdPjWUbtD6mn3oBrWqoHthnLvFfzlyF6DLErdD7r2qdnLUzEmJ42CXZmZvwaP+pIVBy+" +
                    "ML2AUL98tc4A9gxh2eckA+DjAYEOvdiFNIi9sjaoe1wI5USAjG/4L9Uhyh0c0PvEtYfROk7H" +
                    "drU1SLUCAwEAAQ=="),
            new Anchor("CFCA EV ROOT", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA111rzRA/HwVZ1QVNN7EO7JgrjhUd" +
                    "+pNLF4IhcRBS11FkcBbCVWlNjhVtn78MG8Lgo2fWDKzPIq6vd1QqS0yKU1J6w+4u3rNxJcHp" +
                    "XT3uoS+j9yo8ySMdaqsdoafx8+yg1UTPFc9yLx1jl+iZ+f2TpFSATFLUUqsuSd+Qzbhfvj/e" +
                    "ocpNINQl6IQpU7exiB//+tqQnwqpLUE/sfEYKe4WWSw0SRqoBteoiNIDcnoy4upoTW4slmV7" +
                    "yln68uLd7jAs+8xGrMRj629/Nis0cxKUf9/MJp7xcl1QZVmPabOHXjJvwxiKtZWPsHo33lpF" +
                    "O8c24e9n0TnTl1tzYhlILYccBvt0mCBJc/AF0huxoKO3G3DTiGm5WtY49GLcJYt4v/jofrhc" +
                    "yZVPX6ctuSBrz2vd9Q30grf0smYuECj2l1p7lhaPARktbG5/OVgGZIMBg4PDTZLdMsaHpDfp" +
                    "Fs6qLWivCoFlOnDBm61NbVTKKi1LhRuzgOZwRQ1rXjXwfzu4nOQEcIkSJZPaCpkiYGpjYE52" +
                    "BphOvYOtHViKJYXSx2UeLY7G37bG4X+KBCEVKXTwPpyQnQwu8Yo+WqoMCR7H1Tyj7ZfDHjT6" +
                    "OPkIDuPAXSuD0VZqybaoVFMueDJnPYJ/dND74bYFYLlw244L+RNYb3FgEFIQucFBCe9yH2cx" +
                    "eP+WBY0CAwEAAQ=="),
            new Anchor("COMODO ECC Certification Authority", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEA0d7L3XJghWF+3XkkRbUq2KZ9T5SCwbOQQB/l+EK" +
                    "JDwdAQTuPdKNCZcM4HXk+vt3iir1A2BLNosWIxatCXH0SvQoULT+iBxuP2wvLwlZW6VbCzOZ" +
                    "4sM9iflqLO+y0wbp"),
            new Anchor("COMODO RSA Certification Authority", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAkehUktIKVrGsDSTdxc9EZ3SZKzej" +
                    "fSNwAHG8U9/E+ioSj0t/EFa9n3Byt2F/yUsPF6c947AEYe7/EZfH9IY+Cvo+XPmT5jR62RRr" +
                    "55yzhaCCenavcZDX7P0N+pxs+t+wgvQUfvm+xKYvT3+Zf7X8Z0NyvQwA1onrayzT7Y+YHBSr" +
                    "fuXjbvzYqOSSJNpDa2K4Vf3qwbxstovzDo2a5JtsaZn4eEgwRdWt4Q08RWD8MpZRJ7xnw8ou" +
                    "tmvqRsfHIKCxH2XeSAi6pE6p8oNGN4Tr6MyBSENnTnIqm1y9TBsoilwie7SrmNnu4FGDwwlG" +
                    "Tm0+mfqVF9p8M1dBPI1R7Qu2XK8sYxrfV8g/vOldxJuvRZnio1oktLqpVj3Pb6r/SVi+8Kj/" +
                    "9Lit6Tf7urj0Czr56ENCHonYhMsT8dm74YlguIwoVqwUHZwK53Hrzw7dPamWoUi9PPevtQ0i" +
                    "TMARgexWO/bTouJbt7IEIlKVgJNp6I5MZfGRAy1wdALqi2cVKWlSArvX31BqVUa/oKMoYX9w" +
                    "0MOiqiwhqkfOKJwGRXa/ghgntNWutMtQ5mv0TIZxMOmm3xaG4Nj/QN370EKIf6MzOi5cHkER" +
                    "gWPOGHFrK+ymircxXDpqR+DDeVnWIBqv8mqYqnK8V0rSS527EPywTEHl7R09XiidnMy/s1Ha" +
                    "p0flhFMCAwEAAQ=="),
            new Anchor("D-TRUST BR Root CA 1 2020", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAExsvHKNH7hPWa70IUIOFDa251rfwrA4TUdpMl11k7" +
                    "QWVrHuY0Krt09hLO6G3nq+Q8Tj9ECIvNFnHLv5KZ9KTXPFBUUpCFg3iUZ2ejHAkZPXU0hd7t" +
                    "YH3HDLRBUrlu5e5C"),
            new Anchor("D-TRUST BR Root CA 2 2023", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEArv8JWZGACkpo5iQ/uKfkyDoKOhbN" +
                    "ySNhoJNx8quLc4+gZ2Vg0lRrY1FvSTPgcgcTfTjNBpIHKVJrTndsBNOV+t1MjNldwWF9S+co" +
                    "s0SBe1Gv3TOxaHzWTkz+K2i5ymZpxOxeV3/3DcecNjblB2CswEzqCGzvBnxPWyh6CPyTXZv2" +
                    "nLSLhrohufTw6FlaKKE0hBolkba1j++y+YD6+T08EXLY4y+GdsV5LMGpkJNGmGfLg2qgUCOn" +
                    "O/aBOeDt8Lm/ZfHYy3r773MDzgD0fdfgXTtmuNyOuoPLh3YD/CXZ5yNvBv1n8+D/hLxHv7UW" +
                    "GEZpFMwF99vTSaxrzKvktQtDJF5La01n39a1Pk94H5RxJOrecPzxk/6ek1rklFqXVAw1e19s" +
                    "7gAfJOwDugL1dvSf1JrthSw4Ii/H2C92EU/9bFzo9Y4nh38ZSiFHkB15jRxb+M9KheTts1uN" +
                    "vsRkKF1BxG6sOFpPI3R0qRLD9tK5ERUzB5HYOzc6YzAG0cUiNihiIxDgRsyXrNYrXWQk1e4c" +
                    "Dt77CFp1KvZjbc4LQr7RunAcnCHlDzFpF9f8CrTe7YCcy5K0i/XeWaJYCaVjRwvhQTI0Qdma" +
                    "sdmosBta3g0N9OKyXTWAuYHUhGmRAst10I3FtT0JkQmPFKEUdHk+1skVHaRZWSLc9opFPTwS" +
                    "1j5dMi8CAwEAAQ=="),
            new Anchor("D-TRUST EV Root CA 1 2020", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8QvdhkMgGd+XhegiSpvPnZi/tAUmycvjptKPxZ54" +
                    "ezGJqYmtJzxlEIL838OdTvAzI8TSMvUcsN8zF13F8LGK+e+5txTKKUrCD6l/dWVJKjBn9GT3" +
                    "1hp32sPCl2FCe0mt"),
            new Anchor("D-TRUST EV Root CA 2 2023", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA2I6jiYALsldS3KlTTDe5f2MXE++n" +
                    "WyNbaXWwmQoXwYvE26jgzDG6wvLNXem3+B2vasSVh9dHyZXYggRQPYEI/+Q9s7HWxbL9iAnb" +
                    "nITsJRcUh38weJtqWMm2cyg8NPeZ93/TpvgcRXytLIyUP9hnEFN+Is1OJVHwJSQ1EV4QxuyH" +
                    "ZomBaLrMK51Hcx+9zZGkcmqcohsYoG/sUPR9QMKoMM+9c8gTKxATHouaqDqUc9MYaQpK/8EB" +
                    "A/95f7VIf3vu6ClvNkyVYYbY+aJziu6uL5buaM09TShC+UUrMhtGVRZqpksp+buVVr9GHewd" +
                    "kx3AZbIfoUOuVp6gsY9rErdgbXgLyopc7R6WDoOmSJWNO6MhxK5YxgCyhLQjpJaGNbjYntis" +
                    "NEmYY5XFy21IR+LyLhge0DGr3XTs+dyMuByOaCO60PNQ3M9lj3M6Msd8/sqCIk++jmJHZuXN" +
                    "h+Lo1Q8Yn+UEcktGPBDyRMJkVnFOdeicySZ0xX1Z0QpbD23+nnUcGMYaOnzYDQTMzbdFZXqx" +
                    "j7iuhEg+s3pNqAPi4n4BFlloGEMzsNLcsBpDNe6l2qlGXK6GgUEBSnQm7J8Gv8IFN2R1eClo" +
                    "/cX16/5H+eSFsOF7MZ2mf3KjucQsLsyZVw4hDEUBlGXrZQnGYyILM0mSSDz8zc6wPo6ei/j+" +
                    "ScU1ckcCAwEAAQ=="),
            new Anchor("D-TRUST Root Class 3 CA 2 2009", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA07JKz3pH73WbI/o6L9ZQRYk1OsZr" +
                    "2/7bAGio4AMRHTdQCJ9NSmiUNbNT0ZRjpyBWr95ReOwqPfNISFA+Ct9GVYsnbcMQTQ2RUkPY" +
                    "h+BdTja1IcpfOUAEX1t+zKPGK6lAHtk2hNZI85IeNEYgJMGkUY5KGu9QP2ldGX9Fw8cBj1HJ" +
                    "I+hyrrS8Vgl/Esscsa8pkArJVcwP07Qa7Uc1WkrtnHMEIdCqvQwTtQDKJmzEawyUWpWU2lCa" +
                    "8f+lK2YxpMk4oN8dH7gJLvOn6GdSq5Uf4EY+2KTDylrFMYDoSJqflGn+Gd3Yc3yBypbeju2z" +
                    "MgVlhDTm5v1XELVfdr8vsBANxQIDAQAB"),
            new Anchor("D-TRUST Root Class 3 CA 2 EV 2009", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmfGENHC6L7cwoI69fATPvmK8mf2C" +
                    "l9J6CmeWOAn2EE6VInOZjdoVLecF/BlzIreOmAC8PD2soWz71nklS63wzGTaiD4puA8J0zTd" +
                    "M/Vi0eHNGenuGE9MWK7iHtYMWxVa2Dq4xBhkHuMzsrWJd04Mv9mUaxOXbxKj/pmpBMwV7GBo" +
                    "Nu0Ie7f1v5PtZjGDjMZxNIdOF+qvi5GNHFZBriI3XjfyHdnRLQ0vaVGnvmamijoqvccaseEU" +
                    "8L46HbnPW7Fq/rSxRiCi+x47cO+TmH2Mc5byxe+FcK0pJvweBD4coNgPy1KDYnzui1OVkKlX" +
                    "ouphBdj5TcQn+m6t7fnXUfdrpQIDAQAB"),
            new Anchor("DigiCert Assured ID Root CA", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArQ4VzuRDgFyxh/O3YPlxEqWu3CaU" +
                    "iKr0zvUgOShYYAz4gNqpFZUyYTy1sSiEiorcnwoMgxd6j5Csiud5U1wxhCr2D5gyNnbM3t08" +
                    "qKLvavsh8lJh358g1x/isdn+GGTSEltf+VgYNbxHzaE2+Wt/1LA4PsEbw4wz2dgvGP4oD7On" +
                    "g9bDbkTAYTWWFv5ZnIt2bdfxoksNK/8LctqeYNCOkDXGeFWHIKHP5W0KyEl8MZgzbCLph9Ay" +
                    "WqK6E4IR7TkXnZk6cqHm+qTZ1Rcxda6FfSKuPwFGhvYoecix2uRXF8R+HA6wtJKmVrO9spft" +
                    "qqfwt8WoP5UW0P+hlusIXxh3TwIDAQAB"),
            new Anchor("DigiCert Assured ID Root G2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2ecoL1I/NnJJiJM08/hqHjFUgJ+t" +
                    "VEG1R9+WqNSvgC25Cs91/YmlfST64yIMK7yVFwszvxlNQQaQAL0MTRD+B7XnHG4iVTFll73T" +
                    "F9IeYvPb6mxQjD+EDJbPt8sD4MptoRRMG4nd7QCwUnyvkWyxOBPR6RIIwACwHCsR2ndwNpuu" +
                    "znmH3IJw5gl0cFVpr6Non7/dtnmz8p1wKVX0q/+VYfPJQG8d0b6Tu9OIKrudv3JaVnE7P9Tz" +
                    "0Qr+KO+j7tmZrwPTj2C38pKhsb2JiR8wzcOmLmIzrhYCd0Ra54EKPKdELnm4PwS8XKCH4Ruv" +
                    "UY7N7Cz6+P5t8Dp8qovkZ5UxjQIDAQAB"),
            new Anchor("DigiCert Assured ID Root G3", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGee8rERl7c24P1j7jbFXqUQtBRXy7wv/EHSftWJS" +
                    "X2Z+H+XcG0V5C8zGUwqdjV0C2alZ3gJa9pUqDo04SopJxrzGAzgHX1Xafglu4n9e0EUgD1l2" +
                    "ENagJPAt3jbybCk5"),
            new Anchor("DigiCert Global Root CA", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4jvhEXLeqKTTo1eqUKKPC3eQyaKl" +
                    "7hLOllsBCSDMAZOnTjC3U/dDxGkAV53ijSLdhwZAAIEJzs4bg7/fzTtxRuLWZscFs3YnFo97" +
                    "nh6Vfe63SKMI2tavegw5BmV/Sl0fvBf4q77uKNd0f3p4mVmFaG5cIzJLv07A6Fpt43C/dxC/" +
                    "/AH2hdmoRBBYMql1GNXRor5H4idq9Joz+EkIYIvUX7Q6hL+hqkpMfT7PT19sdl6gSzeRntwi" +
                    "5m3OFBqOasv+zbMUZBfHWymeMr/y7vrTC0LUq7dBMtoM1O/4gdW7jVg/tRvoSSiicNoxBN33" +
                    "shbyTApOB6jtSj1etX+jkMOvJwIDAQAB"),
            new Anchor("DigiCert Global Root G2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuzfNNNx7a8myaJCtSnX/RrohCgiN" +
                    "9RlUyfuI2/Ou8jqJkTx65qsGGmvPrC3oXgkkRLpimn7Wo6h+4FR1IAWsULecYxpsMNzaHxmx" +
                    "1x7e/dfgy5SDN67sH0NO3Xss0r0upS/kqbitOtSZpLYl6ZtrAGCSYP9PIUkY92eQq2EGnI/y" +
                    "uum06ZIya7XzV+hdG82MHauVBJVJ8zUtluNJbd134/tJS7SsVQepj5WztCO7TG1F8PapspUw" +
                    "tP1MVYwnSlcUfIKdzXOS0xZKBgyMUNGPHgm+F6HmIcr9g+UQvIOlCsRnKPZzFBQ9RnbDhxSJ" +
                    "ITRNrw9FDKZJobq7nMWxM4MphQIDAQAB"),
            new Anchor("DigiCert Global Root G3", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE3afZu4q4C/sLfyHS8L6+c/MzXRq8NOrexpu80JX2" +
                    "8MzQC7phW1FGfp4tn+6OYwwX7Adw9c+ELkCDnOg/QW07rdOkFFk2eJ0DQ+4QE2xy3q6Ip6Fr" +
                    "tUPOZ9wj/wMco+I+"),
            new Anchor("DigiCert High Assurance EV Root CA", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxszlc+b71LvlLS0ypt/lgT/JzSVJ" +
                    "tnEqw9WUNGeiChywX2mmQLHEt7KP0JikqUFZOtPclNY823Q4pErMTSWC90qlUxI47vNJbXGR" +
                    "fmO2q6Zfw6SE+E9iUb74xezbOJLjBuUIkQzEKEFV+8taiRV+ceg1v01yCT2+OjhQW3cxG42z" +
                    "xyRFmqesbQAUWgS3uhPrUQqYQUEiTmVhh4FBUKZ5XIneGUpX1S7mXRxTLH6YzRoGFqRoc9A0" +
                    "BBNcoXHTWnxV215k4TeHMFYE5RG0KYAS8Xk5iKICEXwnZreIt3jyygqoOKsKZMK/Zl2VhMGh" +
                    "JR6HXRpQCyASzEG7bgtROLhLywIDAQAB"),
            new Anchor("DigiCert TLS ECC P384 Root G5", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEwUShzxGXUJreI4I1B83Qyxid0vF/dzVPO92UclLt" +
                    "wjv47Pp7a1gg7Jmuyfxos3W52wnsyBP1TsYKHWYwTLsfRwo8YRBCKXylCA7gIunTNWjOm2Of" +
                    "hLWZTVigjvVU55XJ"),
            new Anchor("DigiCert TLS RSA4096 Root G5", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAs9D0yXkRnf38ZoHnzNXkvOyBPmo1" +
                    "ji63596v+QdNzzCd6gkLmb1sV9oYSrh4rDo5qKZIrC5y5b3r8RrN56QDqT8RtNgviRb7lAE9" +
                    "uy/4EwWheByOKOBF4IP0WRuVs65+A0XlvsJC/u7yPLaFE5gynRaoKcILHDjcnzF3XL8no/wn" +
                    "rLcrvXSbFy3ygdpdsOEjFz6IShIj0OrPnd4DF7FCSqAWTKRtk+k/Ou46fJ1YnfROj/w7I8ht" +
                    "uOIF2szr7MMx9NenKVSAz0RbTG8wnvPM3R+UQ51Nf3BwDdQ60TfwbJ2bwBSTWO/NQTh1vBMD" +
                    "lXx/41zp1Q3V4nwQYqpr8D128z+j6LDB/e+qV02shqcYtCnBLA6/ZL4pjNgCLc1cL/J/7xX0" +
                    "DBWsCrDx0w1Pak13lwGg8Wa3t87vzuzspXXKrOPhY/e4oQTIvHs/XS0WIlbtSEn+py95MCWb" +
                    "umstP507xBfnHS778s+m/OMULJaYIYy0kekZYIPyMCsGc1DVmDsG6ceKDGCMKPhSm27h9k27" +
                    "BiSb1ysmP/0qL3H11iS+fzGeD23oj09Noz//NerfSV5Bj4b58Xd5Sxu0o14v+0YC0GYTXl6F" +
                    "T87YcIh7zgG1lpfXzX39gvjCJMHKATlPjaLBFEAfnGbVDAlG1vLQ0Uh2VjpDy7YKETm6jBNs" +
                    "BrWez+sCAwEAAQ=="),
            new Anchor("DigiCert Trusted Root G4", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAv+aQc2jeu+RdSjwwIjBpM+zCpyUu" +
                    "ySE98orYWcLhKac9WKt2ms2uexuEDcQwH/MbpDgW61bGl20dq7J58soR0uRf1gU8Ug9SH8ae" +
                    "FaV+vp+pVxZZVXKvaJNwwrK6dZlqczKU0RBEEC7fgvMHhOZ0O21x4i0MG+4g1ckgHWMpLc7s" +
                    "Xk7Ik/ghYZs06wXGXuxbGrzryc/NrDRAX7F6Zu53yEioZldXn1RYjgwrt0+nMNlW7sp7XeOt" +
                    "yU9e5TXnMcvak17cjo+A2raRmECQecN4x7axxLVqGDgDEI3Y1DekLgV9iPWCPhCRcKtVgkEy" +
                    "19sEcypukQF8IUzUvK4bA3VdeGbZOjFEmjNAvwjXWkmkwuapoGfdpCe8oU85tRFYF/ckXEaP" +
                    "ZPfBaYh2mHY9WV1CdoeJl2l6SPDgohIbZpp0yt5LHucOY67m1O+SkjqePdwA5EUlibaaRBkr" +
                    "fsCUtNJhbesz2cXfSwQAzH0clcOP9yGyshG3u3/y1YxwLEFgqrFjGESVGnZifvaAsPvoZKYz" +
                    "0YkH4b235kOkGLimdwHhD5QMIR2yVCkliWzlDlJRR3S+Jqy2QXXeeqxfjT/JvNNBERJb5RBQ" +
                    "6zHFynIWIgnffEx1P2PsIV/EIFFrb7GrhotPwtZFX50g/KEexcCPorF+CiaZ9eRpL5gdLfXZ" +
                    "qbId5RsCAwEAAQ=="),
            new Anchor("e-Szigno Root CA 2017", "EC",
                    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEltw9itiwe2/GJ75EkLGzVhV7jkMkfRqEWe5j" +
                    "aLLGXofQFUgeqJCtvVOi2t46kKZgX2gytYZB34dbLHvF/nx62g=="),
            new Anchor("e-Szigno TLS Root CA 2023", "EC",
                    "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQAaA/fonw8qnSIYQqNwkylASIU1Pdgd0KcCjhg" +
                    "oYxnPrNj6fqRsItL5jnfAsIwAVIAv9+M7VmtMmWrCVlQtRnCaBwA4AVf2lAmHMOsBCLFOk3v" +
                    "6VdYNqPBGVMQCtHNP+9LNRpDj0ITTLksGpy+MLbE3txLneSkPMsu2a3f330J3y6S/6E="),
            new Anchor("emSign Root CA - G1", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAk0u76WaK7p1b1TST0Bsew+eeuGQz" +
                    "f2N4aLTNLnF115sgxk0pvLZoYIr3IZpWNVrzdr3YzZr/k1ZLpVkGoZM0Kd0WNHVO8oG0x5ZO" +
                    "rRkVUkr+PHB1cM2vK6sVmjM8qrOLqs1D/fXqcP/tzxE7lM5OMhbTI0Aqd7OvPAEsbO2ZLIvZ" +
                    "TmmYsvePQbAyeGHWDV/D+qJAkh1cF+ZwPjXnorfCYuKrpDhMtTk1b+oDafo6VGiFbdbyL0NV" +
                    "HpENDtjVaqSW0RM8LHhQ6DqS0hdW5TUaQBw+jSztOd9C4INBdN+jzcKGYEho42kLVACL5HZp" +
                    "IQ15TjQIXhTCzLG3rdd8cIrHhQIDAQAB"),
            new Anchor("GDCA TrustAUTH R5 ROOT", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA2aMW8Mh0dHeb7zMNOwZ+Vfy1YI92" +
                    "hhJCfVZmPoiC7XJjDp6L3TQsAlFRwxn9WVSEyfFrs0yw6ehGXTjGoqcuEVe6ghWinI9tsJlK" +
                    "CvLriXBjTnnEt1u9ol2x8kECK62pOqPseQrsXzrj/e+APK00mxqriCZ7VqKChh/rNYmDf1+u" +
                    "KU49tm7srsHwJ5uu4/Ts765/94Y9cnrrpftZTqfrlYwiOXnhLQiPzLyRuEH3FMEjqcOtmkVE" +
                    "s7LXLM3GKeJQEK5cy4KOFxg2fZfmiJqwTTQJ9Cy5WmYqsBebnh52nUpmMUHfP/vFBu8btn4a" +
                    "Rjb3ZGM74zkYI+dndRTVdVeSN72+ahsmUPI2JgaQxXABZG12ZuGR224HwGGALrIuL4xwp9E7" +
                    "PLOR5G62xDtw8mySlwnNR30YwPO7ng/Wi64HtloPzgsMR6flPri9fcebNaBhlzpBdRfMK5Z3" +
                    "KpIhHtmVdiBnaM8Nvd/WHwlqmuLMc3GkL30SgLdTMEZeS1SZD2fJpcjyIMGC7J0R38IC+xo7" +
                    "0e0gmu9lZJIQDSri3nDxGGeCjGHeuLzRL5z7D9Ar7Rt2ueQ5Vfj4oR24qoAATILnsn8JuLww" +
                    "oC8N9VKejveSswoAHQBUlwbgsQfZxw9cZX08bVlX5O2ljelAU58VS6Bx9hoh49pwBiFYFIeF" +
                    "d3mqgnkCAwEAAQ=="),
            new Anchor("GlobalSign ECC Root CA - R4", "EC",
                    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuMZ5049sJQ6fLjkZHAOkrprlOQcJFspjsbmG" +
                    "+IpXwVfOQvpzofdlQv8ewQCybnMO/8ch5RikqtlxP6jUuc6MHQ=="),
            new Anchor("GlobalSign ECC Root CA - R5", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAER0UOlvt9Xb/pOdEh+J8LttV7HpI6SFkc8GIxLcB6" +
                    "KP4ap1yztsyX50XUWPrRd21DosCHZTQKH3rd6zwzocWdTaRvQZU4f8kehOvRnkmSh5SHDDqF" +
                    "SmafnVmTTZdhBoZK"),
            new Anchor("GlobalSign Root CA", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2g7mmY3Oo+NPin778YuDJWvqSB/x" +
                    "KrC5lREEvfBj0eJnZs8c3c8bSCvujYmOmq8pgGWr6cctEsurHExwB6E9CjDNFY1P+N3UjFAV" +
                    "HO9Q7sQu9/zpUvKRfeBt1TUwjl5Dc/JB6dVq47KJOlY5OG8GPIhpWypNxadUuGyJzJv5PMrl" +
                    "/Yn1EjySeJbW3HRuk0Rh0Y3HRrJ1DoboGYrVbWzVeBaVounICjjr8iQTT3NUkxOFOhu8HjS1" +
                    "iwWMuXeLsdsfIJGrCVNukM57N3S5cEeRIlFjFnmusa5BJgjIGSvRRqpI1mQq14M0/ywqwWwZ" +
                    "Q0oHhefTfPYhaO/q8lKff5OQzwIDAQAB"),
            new Anchor("GlobalSign Root CA - R3", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzCV2kHkGeCIW9cCDtoTKKJ79BXYR" +
                    "xa2IcvxGAkPHsoqdBF8kyy5L4WCCRuFSqwyBR3Bs3WTR6/Usow+CPQwrrpfXthSGEHm7OxOA" +
                    "d4wI4UnSamIvH176lmjfiSeVOJ8G1z7JyyZZDXPesMjpJg6DFcbvW4vSBGDKSaYo9mk79svI" +
                    "KJHlnYphVzesdBTcdOA67nIvLpz70Lu/9T0A4QYz6IIrrlOmOhZzjN1BDiA6wLSnoemyT5Au" +
                    "MmDpV8u5BJJoaOU4JmB1sp93/5EU764gSfytQBVI0QIxYRleuJfvrXe3ZJp6v1/BE++bYvsN" +
                    "bOBUaRapA9pu6YOTcXbGaYWCFwIDAQAB"),
            new Anchor("GlobalSign Root CA - R6", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAlQfoc8pm+ewUyns89w0I8bRFCyyC" +
                    "tEjG61s8roO4QZIzFKRvf+kqzMawiGvFtonRxrL/FM5RFCHsSt0bWsbWh+5NOhUG7WRmC5KA" +
                    "ykTec5RO86eJf094YwjIElBtQmYvTbl5KE1SGooagLcZgQ5+xIq8ZEwhHENo1z08isWyZtWQ" +
                    "mrcxBsW+4m0yBqYe+bnrqqO4v76CY1DQ8BiJ3+QPefXqoh8q0nAue+e8k7ttU+JIfIwQBzj/" +
                    "ZrJ3YX7g6ow8qrSk9vOVShIHbf2MsonP0KBhd8hYdLDUIzr3XTrKotudCd5dRC2Q8YHNV5L6" +
                    "frxQBGM032uTGL5rNrI55KwkNrfw77YcE1eTtt6y+OKFt3OiuDWqRfLgnTahb1SK8XJWbi6I" +
                    "xVFCRBWU7qPFOJabTk5aC0fzBjZJdzC8cTflpuwhCHX85mEWP3fV2ZGXhAps1AJNdMAU7f05" +
                    "+4PyXhShBLAL6f7uj+FuC7IIs2FmCWqxBjplllnA8DX9ydoojRoRh3CBCqiadR2eOoYFAJ7b" +
                    "gNYl+dwFnidZTHY5W+r5paHYgw/R/98wEfmFzzNI9cptZBQselhP00sIScWVZBpjDnk99bOM" +
                    "ylitnEJFeW4OhxlcVLFltr+Mm9wT6Q1vuC7cZ27JixG1hBSKABlwg3mRl5HUGie/Nx4yB9gU" +
                    "YzwoTK8CAwEAAQ=="),
            new Anchor("GlobalSign Root E46", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEnA6xz7fonlJ3dTT6pUanrTIZMrQHqSfKlLsM0goQ" +
                    "x9qJsJcMcBMJAY7Y6kfqvrKAK838KA3brLykhjftcAgAdeqTC3suUpwjaCMGQ+ySL1OE2/tH" +
                    "FAfoX5RnXcl6gTwg"),
            new Anchor("GlobalSign Root R46", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEArKx0MuizZeW67UMmHaaJDUW6KYiy" +
                    "pB1j3dPBLAlXiTmhVelnNHcMbuRVHVIl0hNrXuEdqbd9iTJfDZ6fLHpjYEAfprC2eI+ZVJYI" +
                    "WK7kBrxiBQIWv6+oIwO2lA+8bmzCy9Wmuwzp9sEC+yHeZt0Xq3RC7/B0LyX06mtVW5Dbnd9e" +
                    "hwpA+60Za/v3ymCI3trBj9au1X/UPIPu1xZMg0UzayfQhtAcLWvzq33xhan1KNKt7/OESxyH" +
                    "/BOjOnKiWhEr1idxJ+2BLW1mgZKHtBtYesw/CvpGT014XPgrSOMEhMtd9rRqs2X8Qp5RJiMg" +
                    "yz0U+YHtZRYATxpkl2YIz4x74yvAnfkU8hvxVmoWvyyFhc14OJrrQmoCNBiDF06UVvi2grXz" +
                    "lt09875/IHc+exkjayzUcnNDV33g+NdpTxc2BPnAkGA3Rd7mDNh0ja6com10XUK+BvXZZG4C" +
                    "EKyJsEw7B01AfiTFipiCeY6kp4IgjSP6J3HJ38ZBdKBN9pEW3EaMXyljMVlxDNhvwrYyffvm" +
                    "XVOmfhX8u3V8Xez49hcc7MdrGcvze/ArB6XZbHlUdmydHKZuDul5DKgjaqPfGzAxn7FUe/5q" +
                    "y2aq3GXQop5Kmgcha4GP28RZ+t4iwASc46pbNpPoPb16oZ0LdrELx539z5ioBsL4KqOhg6C3" +
                    "JXKlAuMCAwEAAQ=="),
            new Anchor("Go Daddy Root Certificate Authority - G2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAv3FiCPH6WTT3G8kYo/eASVjpIoMT" +
                    "psUgQwE7hPHmhUmfJ+r2hBtOoLTbcJjHMgGxBT4HTu70+k8vWTAi56sZVmvigAf88xZ1gDlR" +
                    "e+X5NbZ0TqmNghPktj+pA4P6or6KFWp/3gvDthkUBcrqw6gElDtGfDIN8wBmIsiNaW02jBEY" +
                    "t9OyHGC0OPoCjM7T3UYH3go+6118yHz7sCtTpJJiaVElBWEaRIGMLKlDliPfrDqBmg4pxRyp" +
                    "6V0etp6eMAo5zvGIgPtLXcwy7IViQyU0AlYnAZG0O3AqP26x6JyIAX2f1PnbU21gnb8s51ir" +
                    "uF9G/M7EGwM8CetJMVxpRrPgRwIDAQAB"),
            new Anchor("GTS Root R1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAthECix7joXebO9y/lD63ladAPKH9" +
                    "gvl9MgaCcfb2jH/76Nu8ai6Xl6OMS/kr9rH5zoQdsfnFl97vufKj6bwSiV6nqlKr+CMny6Sx" +
                    "nGPb15l+8Ape62im9MZaRw1NEDPjTrETo8gYbEvs/AmQ351kKSUjB6G00j0uYODP0gmHu81I" +
                    "8E3CwnqIiru6z1kZ1q+PsAewnjHxgsHA3y6mbWwZDrXYfiYaRQM9sHmklCitD38m5agI/pbo" +
                    "PGiUU+6DOogrFZYJsuB6jC511pzrp1Zkj5ZPaK49l8KEj8C8QMALXL32h7M1bKwYUH+E4EzN" +
                    "ktMg6TO8UpmvMrUpsyUqtEj5cuHKZPfmghCN6J3Cioj6OGaK/GP5Afl4/Xtcd/p2h/rs37EO" +
                    "eZVXtL0m79YB0esWCruOC7XFxYpVq9Os6pFLKcwZpDIlTirxZUTQAs6qzkm06p98g7BAe+dD" +
                    "q6dso499iYH6TKX/1Y7DzkvgtdizjkXPdsDtQCv9Uw+wp9U7DbGKogPeMa3Md+pvez7W35Ei" +
                    "Eua++tgy/BBjFFFy3l3WFpO9KWgz7zpm7AeKJt8T11dleCfeXkkUAKIAf5qoIbapsZWwpbkN" +
                    "FhHax2xIPEDgfg1azVY80ZcFuctL7TlLnMQ/0lUTbiSw1nH69MG6zO0b9f6BQdgAmD06yK56" +
                    "mDcYBZUCAwEAAQ=="),
            new Anchor("GTS Root R3", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEH08zhzMpiqGE3svHIVhBiepWnStLhcYdTCe8fyZR" +
                    "cm/in9ajysxFFEaLre9+hozssX4v/6lxnRiERQRBVW4r6iZ/u5AB40sZuuRUlkUJsdVskUSt" +
                    "hBOOmowNgAwy9uAn"),
            new Anchor("GTS Root R4", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE83Rzp2iLYK5DuDXFgTB7S0md+8FhzubeRr1r1WEY" +
                    "Na5A3XP3iZEwWus87oV8okB2O6nGuEfYKueSkWpz6bFyOZ8pn6KY019eWIZlD6GEZQbR3IvJ" +
                    "x3PIjGov5cSr0R2K"),
            new Anchor("HARICA TLS ECC Root CA 2021", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEOAj+saCW0nqsr0k60MDgwzsoqvFybWUAR4iE/Jom" +
                    "a6pLumwECoheF/JVh/wwsDTiNFhXGoRT6TDZqfKWdMNRH1hJMcyYTmARh3XTcpSQT5sQJSqo" +
                    "eC2+kEFYkBVyp6G3"),
            new Anchor("HARICA TLS RSA Root CA 2021", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAi8Lnr2WbBWeWyQ0kudAOZPzO4iQY" +
                    "LIR/d1HLBBE2uF7taXGnnuQlCZdnwUfCz5EWNmI9OAThUYL/rNK0ad0u7BGjRe5raztMv4yN" +
                    "pB6dEbnpOPl6DgyY4iMd0U5j1Oe4QUT7a69r2h/TxZGIW6SJktGB5ow5WKDWaUOprZhSWG7b" +
                    "Cvtrz2j646ReOkVzmAfqXwJy3gyls5+uqR23HbP8ilnnbnJlrfUwlCMH84IWSzWYnFO7L8rk" +
                    "WtnHjR38mJn7LKSCa/AqH44LX3FcXK5CeymJgcsDo5nKiJ4LQAlBM9vmWHr9rplwwFoP1hOG" +
                    "cS92afyQ3dstbtHym/Uaa55vFYx68EsooCI4gCRsNqQ78jCR83gTz8E/NavxHREjtUMingGS" +
                    "txgC5RHRgtsVAMxhN8EqfJrh0LqzUEbugqydMfj7I+IDAEhwowkmeRVTYPM4XK046oEAYxS5" +
                    "M17dC9ugRQcaMwn4TbSnAqZp9MJZBYhlhVauS8vg3jx9LRrI6fsfo2FK1ioTrXdMGhibkQ9Y" +
                    "2AZUxZf4qj8giqaFpnf2pvwc4u5ulDMqg1CECuVPhvhQRXgAgetbaOMmjcx7XFH0FCxAvhpg" +
                    "HXpyYR0fYy2Iqs6iRZAI/Gu+s1AqWv2oSBhG1pBAkpAKhF5oMfjr7Q3THcZ9mRhVVidlLo1F" +
                    "xSTszuMCAwEAAQ=="),
            new Anchor("Hellenic Academic and Research Institutions ECC RootCA 2015", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEkqBB6EuChFzi+DERmYZkTgklL51BLwquNU90lbJR" +
                    "ZGuNa+Y/cJXwBURHpnI4UHaVAlqOriie+S1Ome8sSG9MJSno0XFb3x3BdTe01/p7ekKcagpW" +
                    "WnxpC6qACSRsfsFG"),
            new Anchor("Hellenic Academic and Research Institutions RootCA 2015", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwvipPxuJ/Dw8BF09kDawkTp5PGZa" +
                    "7205AUkatLfPf00jU7eQAOMTKiimMfGRAOMo7K4hQc4f2v19ElsBgw+5sF+Z4fISg4BNBj7f" +
                    "rK/noYhrMa/wi9AYM7jbRWo09AKAJCgKAhWVXnYqDZk6FFv2y8tTvBNNAYg3lCUbQrwi2I6j" +
                    "ll462TLbPujwEGXtdOEvp3yvJzS7KX2bts8JyOXTCvyIZWV0CtxzHFzNQLEc1LaEjExQz2iO" +
                    "qFmuwidOgqI13RT0H/+yd9WHL6pufSQn58bLJubl/mcHY9hFDd06WWU5WHqSmXI9nIReiCG4" +
                    "1fQs/NlwUk94uL08K4uVmPWz0WjPIBR+TFxf54vl9TWBGTfXEQi3Zr7TSs6DVwA6w4H4F8uS" +
                    "Nl3Ro9h1G+GLJ+p6SEH9RRkGrSeZTsFwR921n4FTEuWxjEhdMUMX44zGemOWSykwToROYhle" +
                    "PM6XkKV/Aeud4PiLid0lmD2Stn7v2fFRUX0tJshpWWHgrGq4KjYRBHpQvTKEvi/cctXXHRZH" +
                    "5EdmID/0lsWvjgF6pQ96ZPUNGIfZrojV+oTBOsBpKC3yDWhRquOld8akkA6hN4sxI0fBCQjr" +
                    "bvd4m9eC/IQgmUkZthJGsftFVRapo2WsnAcP6mvcHy4GcuyGiBLkLdtfBS/k8APTJjPngMLN" +
                    "QqEXNAsCAwEAAQ=="),
            new Anchor("HiPKI Root CA - G1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA9B5/UnMyDHPkvRN0o9QwqNCuS9i2" +
                    "33VHZvR85zkEHmpwINJaR3JnVfSl6J3VHiGh8Ge6zCFovkRTv4354twvVcg3Px+kwJyz5Hdc" +
                    "oEb+d/oaoDjq7Zpy3iu9lFc6uux55199QmQ5eiY29yTw1S+6lZgRZq2XNdZ1AYDgr/SEYYwN" +
                    "Hl98h5ZeQa/rh+r4XfEuiAU+TCK72h8q3VJGZDnzQs7ZngyzsHeXZJzA9KMuH5UHsBffMNsA" +
                    "GJZMoYFL3QRtU6M9/Aes1MU3guvklQgZKILSQjqj2FPseYlgSGDIcpJQ3AOPgz+yQlda22rp" +
                    "EZfdhSi8MEyr48KxRURHH+CKFgeW0iEPU8DtqX7UTuybCeyvQqww1r/REEXgphaypcXTT3OU" +
                    "M3ECoWqj1jOXTyFjHluP2cFeRXF3D4FdXyGarYPM+l7WjSNfGz1BryB1ZlpK9p/7qxj3ccC2" +
                    "HTHsOyDry+K49a6SsvfhhEvyovKTmiKe0xRvNlS9H15ZFblzqMF8b3ti6RZsR1pl8w4Rm0bZ" +
                    "/W3c1pzAtH2lsN0/Vm+h+fbkEkj9Bn8SV7apI09bA8PgcSojt/ewsTu8mL3WmKgMa/aOEmem" +
                    "8rJY5AIJEzypuxC00jBF8ez3ABHfZfjcK0NVvxaXxA/VLGGEqnKG/uY6fsI/fe78LxQ+5oXd" +
                    "UG+3Se0CAwEAAQ=="),
            new Anchor("Hongkong Post Root CA 3", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAs4jX6s4PIE6+5tYDbe5Z/MJX3ylo" +
                    "oYMOPmjHaFicHGBLiUMMudQVsu7BTnXptafv5ek1meTMHOdLX40zMCAzU9mmu9U+E47pH4dJ" +
                    "rVAtUMoYvgFYohNwlruJiFaAXPi9LDzhTFeIu9O5le/Lx/baMXQopuZUifVBMcrlJhrNguBw" +
                    "2jspu9UD9Zm6VfVk0WAOs4lJuIovBdKERSh8j2hQEnj8C7VTy8KYHISjnrC+I6Ta3MgrHtpu" +
                    "RR6JmNr5AC4G6Qw7cNVQJYiZy81zYPfV/zVnxaG8XqvNSrhF68hoHg0NFEYS49JkYopCmLy0" +
                    "xggI+P2oTGScdgG9L6lsMw/YPyi4PGkBQoZ+acHJBsrlekZl6cLWUEEuP7fk7WzXvyYBEaIW" +
                    "KUprNAaQ7BPStvtqdtI87fDWLd3hFeyjmy8syT4r5Gk7/3IlsTaGW8d/a4tVG0rFIGE9rstQ" +
                    "4Qg6vrCPY0FTMAhZPJgdd7pjkXrKEFBgv/DXvJWHj5fF/pdqAZSjfFuFHSo5OtBUodE5cZ39" +
                    "Ifm1e/Di4AKPbpYkJSygHiyoxImn7+2ZBi+2CkxP26LMNxqvR4Util/ENDRMAP0Yk2cT0Tfm" +
                    "SLSLBsVXexmGCnnLAMlSr0L/N4/hox56PVCrYwbnFbU/tkU3lDexfvJIw3/Fdf6XjUWPGqca" +
                    "cigaQA8CAwEAAQ=="),
            new Anchor("IdenTrust Commercial Root CA 1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAp1AZ3j+ZPdQzRvFvUWGCsqlPj2eJ" +
                    "XYTZU90MKNnX8P+ulUNymfm1XXyKwULhMVB00YENfM2bIatD4qytXoZu8wmKH1oyvaLrlPno" +
                    "XArs/5jSr3GztFOfTofvkry97E8yMIhLF15XxFPC9gKXjdliK78kH2KN38O4KUtJeDyTYIgi" +
                    "/JnaNsjCotQsVABnNW5zvwJY8KTd5bCiJnrK4DalGRb1/bfvrj9A9W1aBP3ONMok3HQjG10z" +
                    "ExJdxAEl9jDdAl2f4NVHvbTrG6G7SUnYn1sC84rkJJDkYk9Pwa+LDnQXqNFyiGp6AUnMtEZ5" +
                    "xhex2pgeB1n6dSGFZd2QVs77q6VgncSd+VKwi72H+Y8rIwojdjv3M+HJAPNp+Uui4E68fpM5" +
                    "hAf3RHB+/gda5bGs0RjM8jXlSUkIylbJPfsPGH2LO8ETwk2PyU8ON+kfoQ5q32IuyzUGUXks" +
                    "yCU49PpLp4lcnNLjDTmGSnR81VmHwj9ODFxS9D33UoLx6qOs/Uk0GijzQYg6E+7o3v+ZHV+6" +
                    "y+ge8rlQYMAx03Pl776g7TMLdL4gIMRnbPAIA3pVgH9GTpan9B4+4fbYCeEzZCtj1zJen/nA" +
                    "ew94b5e8k5r5nBKQeHqAhxXXcnScVXR4sbrhbnAEuk+gumjDe/8x8HM9PZQqsQtBDqD+TYhl" +
                    "a3kztNcCAwEAAQ=="),
            new Anchor("IdenTrust Public Sector Root CA 1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAtiKU/KRIr+hHawr7J3bk8j+KO3pK" +
                    "LDEqjI2wqcMxa6h3doQmtqyBQg0I61VYu3r4vGV98qBti6hH6WJ2HhHuCBTRskQW9OrQ+h4v" +
                    "XtvLc0GuvACwSitAsqzhO0vCLZ3koZvsGjoe8Aiz0OQkNQefnLTJUm3bB8qPtVvwg/NPxy2l" +
                    "yK3LlSCkMShXWFrkjRuaq54NDPIKMzkiOQqXLvNTd7lERf2EyzYggVktmm9tSEhhykzfU9Gv" +
                    "UrxEn6sva4Ny73WA2gYzG13I2mPGTc2sZjHN0d4+hxA24bmkeu9gULLLyqZW4DevqzQTOSXo" +
                    "OWbkmHqqEpicWWaGPq3xsMo+Bg978BFLN6BEbXvLqIxx9NW1kTbM8BXGK95RF7GXTFA9sZVZ" +
                    "fAV9LSHVAL8BZ6Jee6Zc8vci8ZANk9uqRFFmzH12A+tqqCo4GZd2DWuKYfm89u52/XAr3Sk8" +
                    "+AoeW0Ici1YvVRscoS61xxbm+Ko8ko5ptgHBtYadiQ8LOJRU6Orcnj0lvFMm7dWrOarFQExU" +
                    "q7K02dn413LbHLxtvWVf74g1KmYv7vazZfAzjXyYQWlGD0McafqbtdBhas3KS9lMkEarFVmh" +
                    "R1QpLoMoXxzCoqtyFwAGjkXsi+IzPX/aGUTkYnLD3yLG8lbU3V+Vcu1tX/dIA1v9xSqg9nMj" +
                    "hBAbAecCAwEAAQ=="),
            new Anchor("ISRG Root X1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAregkc/QUN/ObnitXKByHvty33ziQ" +
                    "jG485legePd1wqL+9Wpu9gBPKNveaIZsRJO2sWP9FBJrvx/S6jGbIX7RMzy6SPXded+zuP8S" +
                    "8SGaS8GKhnFpSmZmbI9+PHC/rSkiBvPkwOaAruJLj7eZfpQDn9NHl3yZSCNT6DiuTwpvgy7R" +
                    "SVeMgHS22i/QOI17A3AhG3XyMDz6j67d2mOr6xZPwo4RS37PC+j/tXcu9LJ7SuBMEiUMcI0D" +
                    "KaDhUyTsE9nuGb8Qs0qMP4mjYVHerIcHlPRjcewu4m9bmIHhiVw0eWx27zuQYnnm26SaLybF" +
                    "0BDhDt7ZEI4W+7f3qPfH5QIHmI82CJXn4jeWDTZ1nvsOcrEdm7wD+UkF2IHdBbQq1kHprAF2" +
                    "lQoP2N/VvRIfNS8oF2zSmMGoCWR3bkc3us6sWV5onX9y1onFBkEpPlk+3Sb1JMkRp1qjTEAf" +
                    "RqGZtac6UW6GO559cqcSBXhZ7T5ReBULA4+N0C8Fsj57ShxLcwUS/Mbq4FATfEOTdLPKdOeO" +
                    "HwEI0DDUW3E2tAe6wTAwXEi3gjuYpn1giqKjKYLMur2DBBuigwNBodYF8RvCtvCofIY7RqhI" +
                    "Kojcdpp2vx9qpT0Zj+s482TeyCsNCij/99viFULUItAnXeF5/hjncIitTubZizrG3SdRbv+8" +
                    "ZPUzQ08CAwEAAQ=="),
            new Anchor("ISRG Root X2", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0HttwW+1qL" +
                    "FNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7AlF9ItgKbppbd9/w" +
                    "+kHsOdx1ymgHDB/q"),
            new Anchor("Izenpe.com", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAydN6yg8erKeG6BZlarHCG0UycZXZ" +
                    "/hBbzK/npXkBj4nDyvJVcfd3vneU83KkLETYnpKbFDqh5ySQCgpWjsXYJpTh2UjhLT7aCnLd" +
                    "o5kV2oGih/R7biZ3iVit1usMskF6c25t23p4QekIiBJ+hy5mEWNsVPs8nXLAvC7/wrfdDXbj" +
                    "Otf3tGi+ovXjgW7BRm9djeBNxlRViRozMQqxV7mjipjD7Ds0xZVBaX51wjwgxWG6UUegIJCT" +
                    "oZBL8058hUVUmtEFJkGwtU0dM77EA8glfMFw2zv0CS1UJ0isL+HErD7Iy5JMUzk3I+zTAfng" +
                    "CURNTWTA4Q1ahyK8rRuj/ia1FfOn/IQZ6eyhiLREaYSD84nRdAapzAvWwt4nhVAmyhe4yXqH" +
                    "ViwaAR5svhOtEKy1JPU4kaHWS9rxu9LeR7XxvIH2WWvPGVPpjRXLSsupb0TlG0HP4YanytBq" +
                    "n7xMjQYzWqKF5ZA1oGJcFk7w46L6Axq0LHGzWCzeewvbGg/r3iEfBncGA7DJ75n8wLlPC4Yo" +
                    "/tK56uPapcNHaRLg2/D2GYvte3DXAtbthxgoLAQkTHfkSIoaxjua1A/K+nXSAUBajXm/i89L" +
                    "z6oWwZXkrUyKPheR1LFi5YLlgASkA36Nv9p/og+XTwzTDfvX0eVyfhzId/9bmg+3rgVG5fGo" +
                    "FuxHpBcCAwEAAQ=="),
            new Anchor("Microsec e-Szigno Root CA 2009", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA6fiP82Ot2obYp+BC+8+R3qYm+Jml" +
                    "Y3Ctm67KM0B9bZZuoQ5E7uETnZRCUpq9dYV0LKgOHZO2GLeMLKjP+1xxudrs/uh+j+QvHbKo" +
                    "dYfYt6HlO8+ZSkbQgxl9wKESHJVtSvTYx6VNMy6FOUB1fhR8gBKYUMdBZ7iggGFUpmxOH+Cd" +
                    "Dgfpyboz5/7AVSgsAoCnGfWe3FVTA5d7B0j/mfs3iiTEWcxQEGOOqqkasIQahvlfu7FQbqTR" +
                    "CszVcX4fpxt89VNuIl/LK+bUfF2u1sLGTOUFAdntV/zBI3n8+sgkg5XztWpRAdB31ukSofka" +
                    "g/uCG7mwl/R2BjNDSaD/C7X6tQIDAQAB"),
            new Anchor("Microsoft ECC Root Certificate Authority 2017", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE1Lw9AkJ1QRMjzYAEhgJRL2qogWILZcz2yp0eb0pm" +
                    "UaID2Z2R+rYWsYxu3nzN23mmL867znEv5aWrKOxjBGaZ+PrykxAF4YEoQuPGaPTmG4RgSomv" +
                    "7XkPO87x9kT1AXjA"),
            new Anchor("Microsoft RSA Root Certificate Authority 2017", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylu+lDOMKZWRFgqVvUdiwYnzmTbf" +
                    "RpDJpe14am9HkWj4J2dQMx2hpvvg5UOjhAJXAV2cSECCUxC8v8c7aJC2gi3l9GXQzG0ZzJX5" +
                    "e6xKlK0O3ktDHYcHkhOQgINkNTkE/OXpbLO2H1CUOGVQXBdGubaFtRy1F+jWRZ3YsiawysRw" +
                    "Sq5gpN2z2ez8O9VXcrw/yMmy3ktr+CNsA8AFvZXHzXM7ZoBk4xqsLvlHBfIGtptz9XgzW8eh" +
                    "+ycqobSakYyR0zqCPnZAtM1SYVFwKD/FxVryyYxJuxRbTcj/Z01MEpat9f54qJeH1/1eIIDc" +
                    "oUsi+9SJrbrOR5dHVXuPRchnKISVHGgw7+9J4DV7ZOeYsJTaTYU7PlXEKK9X854T20Ynnx6i" +
                    "XkSDpKXK1ROzSz/E48LmhmGkUjC5eiBPbw84U8szDBMrj9aavSrILbEcfUtRykfRSCdyXYfr" +
                    "1UXmSGWdr1KQuluiGGVXEp9oudQVa5TEaSKY9DPg7flRjkFQyTRPdpCs/DjB2OF7uePjlOFG" +
                    "acsOClBrE7qsDzdatxK1kIEeVq5XIobZydLR11HjqzvGVf0eDtN0CtHaquppuJcoj0jEB/hS" +
                    "Qzr0ylU1LLCmasCc+fKB4RJqwEXZZ7PO/yOiiQpU1BS5KqjX7PmrzSVYMnmPkFuYOcQIBsGs" +
                    "fw49AKUCAwEAAQ=="),
            new Anchor("NAVER Global Root Certification Authority", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAttTxk1y1QIkKqw2QW1BjrpCUdBdF" +
                    "ctZ7ZVopS6dWoEu4L0J16dl7JFoxZasXF9EzOtkR3EA2h9/HaukmXlmKd+PoSJwxFvo+kbHK" +
                    "yaPin84hU6MCNjDLUgLl2jJdw8Xm+e4Rx4vJRB6EkxhKtJ/lEmRp0CaFYgG2yQIdvoNRu1za" +
                    "+K0Vapn3klT3NFvpv+opgRLUU5GWs5Fa3f6Qcyj7MEa1yggHx3FyyWbTNJf2jPQYSuHQPVpF" +
                    "tmmnKfsjzojYEpwASKimD7M7ko1xDnTFi8hM+fSbjrg8ae1vO1AvWO3EsNAcG2oM4rxEqtjN" +
                    "FF2UeGG/Dm7aKrwvDAtxprMWP5zm+cyfUzXiA6CgGL+78b701oyHDUL3Brnxbe0ElKj+ttMG" +
                    "xkBh352d81R2zlM6AaaSQewEo48NotUJytbLmvHvQ13Aq6VBz1xTcHDJiKYt1Gthc1AmhmEO" +
                    "XxvCK+KM1budwQNCupTaX6mwysxNCu9HaQMvIvvxKM6/XVBlqJBts3SwCMesqNHrPpz8XRqD" +
                    "LivLtfNEnTqnF2GWonHTcJYVTbdMc+4ZXMVbPkH+rHVgOxtjzgDd2giQYrTlLe5Ip2sXmVS+" +
                    "h0rjqV4ETOsQbVTW7/Ho8mIWy4Br7T3t9R8wpa5LyRPtigEBybhRWMBmOrFmS8TVMQJi6XSE" +
                    "DNtNRi0CAwEAAQ=="),
            new Anchor("NetLock Arany (Class Gold) Főtanúsítvány", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxCRec75LbRTDofTjl5Bu0jBFHjzu" +
                    "Z9lk4BqKf8owyoPjIMHj9DrTlF8afFttvzBPhCf2nx9JvMaZCpDyD/V/Q4Q3Y1GLeqVw/HpY" +
                    "zY6b7cNGbIRwXdrzAZAj/E4wqX7hJ2Pn7WQ8oLjJM2P+FpD/sLj916jAwJRDC7bVWaaeVtAk" +
                    "H3B5r9s5VA1lddkVQZQBr17s9o3x/61k/iCa11zr/qYfCGSji3ZVrR47KGAuhyXoqq8fxmRG" +
                    "ILdwfzzeSNuWU7c5d+Qa4scWhHaXWy+7GRWF+GmF9ZmnqfI0p6m2pgP8b4Y9VHx2BJtr+UBd" +
                    "ADTHLpl1neWIA6pN+APSQnbAGwIDAKiL"),
            new Anchor("OISTE Server Root ECC G1", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEFy/6ErysGPMK9ETWdkKes+gft3mpWLb4ZdE6IU+o" +
                    "66O+pDJy87YBySv9d4VuU92t6qouJZK26SERqK+1TAvzlmCaO+fqGngutD3lKN4cgLpcbg3b" +
                    "GaXjP5wquEBL3eZP"),
            new Anchor("OISTE Server Root RSA G1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAqq70q4LP++U3C+fVlq2Q6Esp3FVg" +
                    "48y8s7wtkrnko3rxgbSecnJD3z+rCya07nsaafso0HJcSm1pmfBjHgzSsf/WjDTQ7tusSLnq" +
                    "sBSO2AepJJjd6Qm+qCIbWTnRJ4fcHM34+7Pr6ZN47Q/ON3wmd26h2CwhTOSKT8cTPG7H1ROX" +
                    "sqjbJGmDVtNpy4ISu50b8vg08pgrKo4EZ/bjh6EdrW7ONnQOXjM7y9tRl5RqlTzOGFpuS8b8" +
                    "B48uGrlK92Q0KdymjVDhjYtL5Ugbbi6AED/knxtlPxG06ldpn7QA64UkmST1IZ2Xqvs0fwJr" +
                    "FZCtu55aGX+kjNj6bSj8OMfjTK1qztlOk5KOzAxnvwtLls5mZ1NoyxcRjln3rJwbuY5oRLcY" +
                    "r+blD2XclQmwkxK1Hz6UpceIdSGx3gkkKkzivOxMZ0fCKYi5Crr5wXTOjBgmZdr3b8aMe2hc" +
                    "C+5jwF5L8U7Mny8P4eiaepPx4MjbvyfmZSl7NuAzFXPynWuECGgrNgcrJ8x42GqHDkd09Kqg" +
                    "E11kfvTbFK77OuQvwWXjuXpAbPAGt3som9fhXzhzlKzZcJMt3ISvRhyieiw/gSZC59TYxWyE" +
                    "ZhGLd2tUHKO12BDwrin3ZwiIF1y4l3nP6isq7lgz5W3pKaplAQ2CE+wlC10sQHIVKdOQLPca" +
                    "Q9Vq8GkCAwEAAQ=="),
            new Anchor("OISTE WISeKey Global Root GB CA", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2Be3HEokKtaXscriHvt9OO+Y9bI5" +
                    "mE4nuBFde9IllIiCFSZqGzG7qFshISvYD06fWvGxWuR51jIjK+FTzJlFXHtPrby/h0oLS5da" +
                    "qPZI7H17Dc0hBt+eFf1Biki3IPShehtX1F1Q/7pn2COZH8g/497/b1t3sWtuuMlk9+HKQUYO" +
                    "KXHQuSP8yYFfTvdv37+ErXNku7dCjmn21HYdfp2nuFeKUWdy19SouJVUQHMD9ur06/4oQnc/" +
                    "nSMbsrY9gBQHTC5P99UKFg29ZkM3fiNDecNAhvVMKdqOmq0NpQSHiB6F4+lT1ZvIiwNjeOvg" +
                    "GUpuuy9rM2RYk61pv48b74JIxwIDAQAB"),
            new Anchor("OISTE WISeKey Global Root GC CA", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAETOlQwMYPchi82PG6s4nieUqjFqdrVCTbUf/q9Akk" +
                    "wwsin8tqJ4KBDdLArzHkdIJuyiXZjHWd8dvQmqJLIX4Wp2OQ0jnUsYd4XxiWD1AbNTcPasbc" +
                    "2RNNpI6QN+a9WzGR"),
            new Anchor("QuoVadis Root CA 1 G3", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAoL5QEI7p8mxAtASchbkxytwt5BGp" +
                    "BDwbVcHnWDAdJLTD74XejCzhwT3fguZPrUeHbOxbScFK1buP7Iesf4Kahuw9A5lSAdI1nqza" +
                    "8FPJZjzUrAIB2iTTO6gCRq+kHOP4c1h2t/YOkA218M/M+vnGTOXDhjAKjRd+NevF37sOnMCN" +
                    "h+OIOIVn+j7Hq+ATnAUYmM+T9bGStPwj08/VxCdJ4J48mwiji10qIeD8OapT2n1+zxoJU7xd" +
                    "BQTPoUqPi3aCDaH40scUd1uQNgeBmz4G+lJeY8WmAP6l6VIbUrWSOXIDCWK9sGAWbqbdJcID" +
                    "Zt3zBNFA4k6LhvRv5YOgJ4ReBMH1kL0wPcTvqGm8OJukpJbRYtppwAGWrsvEUTTqDKr/IY5Z" +
                    "j0pc5GGap9LpKniNUT06Fe6iWY6pXN7F+ZAi5YhFcd2RmWx6nz09mHxe9r4WaKBergsj/FoP" +
                    "qiJ2LcmhEB3k00QjkIifxirm1/Was1geLzCJCBtUorWYI+wIdxyVXWHRy4mcX6JKkZrvIapJ" +
                    "FgiovWEoMcl0rYX22cWxi9HlEDJNX4sgOjxJHzOFWQ3bywl1Q2lz+2txffDfxEx9xqMuyJV5" +
                    "y3Oijk5NJPte5AS+chumJy1JWpl611wJILd/lLlP8Q0cXohCGxG355Hbnmz0at+MBpgDrcwo" +
                    "76VH81MCAwEAAQ=="),
            new Anchor("QuoVadis Root CA 2 G3", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAoa4lsgEY3FeIP0br+a/i6yNx4prR" +
                    "YWYhX6qvJ1HlbhsW1C19ULBTd714OmDiZAKbfIab1hqOrf8fFX/VlR4Sy+YUhATB3zazFp+K" +
                    "48nbmDTO2DMXKEb8p8nw0rTVTQlySfnyh+Op2n2hfWuyOiWpbVJErPi+bvvcpnORkGGmAxQg" +
                    "8ueHo4itraCM/6YLJVIl5xYB1cu4NYEMozvw4eH8Wl3OgHFt+EmrPju6uNeAAful61uzxV5g" +
                    "KjGgrzfoIDqfqDIsDMwJHdOejl28TJjuxRpoe+xTpukUNaPfzYCfDEj7HPTxv0q4+tWMcUrH" +
                    "H63+QZqzg13yhFbvpVdDzimtjKtVv8T7WwHdIyGhWACOw9BqE+0T4xIrgNxn5pWyzR4ibir4" +
                    "QdTyyhQHjYpVEsZp9biGaC9TXrDSqiHBmOYw42dVx5turBmoVaZFBtAjOtvrZV0qERHwO0/K" +
                    "bfQ0xHHk/wBa9lyuI2CFc/HkELElrtWSuxPBDOA52rQ5V7WrNapyITuDNecx33ohbrgyCH0d" +
                    "MpEVSmJyz+N3obzVERt2AWcI4EELw+sVbvikGdmiq6/iJ1JWKwKKLBQk+b9CAr8myMaP4G44" +
                    "fVMt5e2Ys5VjaH/5NfTfiMVgNZLAfGkcYZUW0OveC68+BBBFZVhQOK9I8lm2FvI8DZACxnAu" +
                    "Aa08FdcCAwEAAQ=="),
            new Anchor("QuoVadis Root CA 3 G3", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAs8sOEGeO6hSXpzIqClY2f2hMx7Nv" +
                    "OiMUkf8Zf6XKrO6zdp166Ysbq2sx2/oLU0yvxaUaeTyKTP+s3yXeTtmCMgtE3srbjKyjbhaD" +
                    "O6ZkSzKJ+xYWOH7rQ+LTdErCYgpzCt1Js1fSsAqFnXE83qPLwDLzATkgQxs10VOzse7Fk2mC" +
                    "Pha1KEah3uqJCe1DuAVGiob1WUe+G28BIRC5/anSKMoQOQnKEzbPnK2tQHR5KwI/NP/6IGl9" +
                    "0+5h9bqz5zDQNyOGcmFFKUhZaG93pi6BvgdNb6/OxEUTkRRwBo8fn/iHabEO78OJGevqHGH8" +
                    "emyK3NYDC54muhLd1FQ5qyajM+p1gdotzQ9P5APR7xWXG2uQxQKQk2YCIbFH3ouaSoC5VY+1" +
                    "oi/A1jNn2n7Ep7QEROtH++ZYufcM8HsrscBwKcNAYi07SGncIzxI63sJealt2qgwmM+AcgOI" +
                    "pltGrnJ5fAgDIWWut+EcpbEqojHeZgT3wHTocd7/PVnMliYSi4WVVxqra3ULRD0RKDx7Ybfi" +
                    "j2dP5ew8TGCAaVc4HgFbjVXox9/AzHcjNEl1fPaYEest3u1BLhQFAn/g/iDrNecRrCLOVz3e" +
                    "yTBtEAOFzfH/jBa1wbI+iGxgf5BPlff2La0BOQcE+nWAfb9JUO3vycR8HOuAftu20N0T/snT" +
                    "nNeyl6kCAwEAAQ=="),
            new Anchor("SECOM TLS ECC Root CA 2024", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE7NzFMtu9dzQXSNC12fabk0+GlC5finB3R7XaZonR" +
                    "Ud20aFiWObtuNBCLUZSfk6QXAE55BjEXsXQ/NG8yUqicXjsu9ksDK3JZBgCwLOVh6+nwJXTv" +
                    "so/dEj/GUYH5mBdo"),
            new Anchor("SECOM TLS RSA Root CA 2024", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA4TjizUwzxbInq8Tx11gaFYNk5fO+" +
                    "34y7TyM4neh0UgL5JIZbJNLTz2x//L/B71+5m6X6nGIr7d4lFJBGtjO677hXOz93zkcWaUTm" +
                    "3VbOAjBlt4YWxlcccBHXuZ7o3Q+4R+ormrBdHeJ1CTUEG8ttQbKIl3G7OZYbnH8/pP8cjPub" +
                    "/0kDVNuMzp7xsVRROOisQt53fMoJLlYgoebbuMphOqMCtjkJ7R6efEMfLp8UAVi9ZaLRn76E" +
                    "T/CJkk925nduuufC4BatS4mnXFmxN0vUXb0ij9B8O/D8gixQEsVSD4GK8FWRPh3bVd/6bzdk" +
                    "HGJjy21XI0yejVomZUbRrOfNuz0boPGV1pt18fFC39IHQEth3OFqb5NDO3L+A9bNqTgAyUgR" +
                    "mIn4ucgDc/Ri/Km3V51ueZjy1/yk0qwJVadAVVrCt56iNeXOyEvzJADGgDQ8E1Pdaqct8Cyn" +
                    "z/47ReQM62vFYO08wcQkrjmX/tesiko1V1yyaf6EfPzUFzmaGy9xvkCwdbm15EdTolOjE0H2" +
                    "Vb5/APDOyCFEokiYGmXTLdAUl0wKZ4IyjkHGzy0jhpaXEXE/GJcEvI6VzEchjaBL03EJ0h9p" +
                    "G4OqeIOycKvAo3A+TbetyfsrgYyHzU0a7/qUjGat1AAq1nVljMpKqpinPTsf/d9H39FTUeJL" +
                    "7TpzzjUCAwEAAQ=="),
            new Anchor("Sectigo Public Server Authentication Root E46", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEdvqZqW4g7fnXd+MHO6jbPV846KtVplZP1kjq7H8t" +
                    "qsOyxXnsmWF/EHnHAlr5BDf1NDUrd85/II9SowCJ7NWnom1b40uSk6CA9QGU3PBoBx7N7v4l" +
                    "UrUgQxwb/usZzkOj"),
            new Anchor("Sectigo Public Server Authentication Root R46", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAk77VNlJ12AEjoBxHQknuY7a3If3E" +
                    "ldVIKyZ8FFMQ2nn9K7ctpNQs+uoy3UnCub0PSD17WphUr55dMXRPB/xQId2kz2hPGxJjbSWZ" +
                    "TCqZ80gwYfqBfB6nCErcPiscHxhMcao1jK34bug7StnllALWiYQTqm3ITzPMUJY3kjPcX4jn" +
                    "n1TZSPCYQ9Zm/Z8XOEPFAVEL1+MjDxRdWxTnS77d9MjaAzfR1jmhIVEwg7Bt1zBOlluR8HAk" +
                    "q79FgWRDDb0hOi886Z4NyyC1QifM2m+b7mQwkDnNk2WBITG1I1AzNyLjOO34MTDMRf5i+dFd" +
                    "MnlCh99qzFYZQE3Oqrv5tXZJlPEn+JGlg+UGs2MOgNzgElWApjtmtDmHLcjw0NEU6eQNTQ72" +
                    "XVdyxTscR1ad4tX7gWGMzE2AkDRbt9cUddzYBEifwMEoiLTpHMqnsfFWt3tJTFnlIBWohAIp" +
                    "+jiUaZpJBo/NH3kUFxIMg3reH7GX7vmXeCikyESS6X0mBaZYcpt5E9gRX67FOGI0aLKGMI74" +
                    "kGGeMmz1BzbNokxu7Io27fLmmRVEcMN8vJw5wLTha/eDJSNX2RKA5UnwdQ/vjescm1QotCE8" +
                    "/HwK/+97a3X/ix2gGQWr+vgrgULoOLq7+6r9PeDzyt9Ol5cp7fMYVumllqy9w5CYsuD5otSm" +
                    "R0N8bc8CAwEAAQ=="),
            new Anchor("SecureSign Root CA14", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAxdJ6odaKvxYx0JjROpT8WrhuIsFi" +
                    "96cKJ+9Q9i6xnmgS8GwkYznx8N8Qxt63UiDVUltCmZ7zoL5SH1/MZ22nLlCiwZeNtviV9bC6" +
                    "3J3gvsvf9zjyR/WmmpKVKmJZUAuisTXnZbJhsuqScWnkKfBPgYEEPLKlW9TFqFlne1UcSat6" +
                    "ncLnc03vzQnCxFcS2wEOI3kJBzui6PyKz4/ARiScOCfgg50boL94FRDrhk4KWv3f2iyCfu7K" +
                    "9inh+nGh94honJzwjb4PSZHY6jr5/dBocdvptStOgpJvZh/g8NxM7MrR6rp0BvmzhJCU0V+O" +
                    "cxkQXQLlcKXAENAQfG/FWEm0sG6a2n2V9czaAq+4LH15j75D8fkoKI0JQ/gI3WvIiywksY1S" +
                    "B714m8vKaLKk3QxMeWDGmdGT8TAaB9OuIsLqzvGECczgFG5/P37SgoWs3KkWToWgYMv2nNfI" +
                    "s47txpuYdQ1V6F/llYsCpK5DKSgRpOYSMAFLdWseZp15L6V2Lx1AtG3JfXkI7NFqtl0qsqVm" +
                    "vWuF9HRWw/XndVIoLKX/Zkel1P7+nlS/ZX4B1jCPpTacolAc7jiAAUjGx3T0xqzDQEkWYXQs" +
                    "r4xvNe17GABbNjycUA3KkjMQ8SZJbd91JDeCItfolv0VSwKWPgdylX6rPUwu18rw3+BYPy0v" +
                    "BJo4owECAwEAAQ=="),
            new Anchor("SecureSign Root CA15", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEC1B0jWQymZmz0mAIuCKORnQseMArRC1tXx3JrktS" +
                    "IIM9uBRtU4dgnl9shdsGFJXgxyj/nV/kqvGzi23tTy9LyUqUkWR1/gHswdjrepR4VhhDX2uB" +
                    "y/a82rQMtimTCGmP"),
            new Anchor("Security Communication ECC RootCA1", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEpKVvYAMDw70x9NMXnCuEdazl/T1Xbtdjv+YEiZKO" +
                    "gZzj6UduypASyBPgp533ZXQfbBCy6OTp722FMplEsV79zHYQ2Fu9osb51kLkV3bckMI1qUuI" +
                    "PBJHbVz/SU8aSlCx"),
            new Anchor("Security Communication RootCA2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0BU5UrFSs7rFWYLEXVKuOkNlgEvH" +
                    "8pa82zaX1qZkjKhe8OMKHPfflz1LrvZd7CG1QavNuX52n775PjY0oDvB9jERRXSTPVeAxfmJ" +
                    "mcrlq2rUtdpBkBDB1tZCicK/9DgSlUxUBfc25EWDexRl1twMTdHefgyrO8QVvjpWplpvdmlS" +
                    "qXq5yOtqml1S0C0KazUWCRCE0GrKOgYAN0fkfldPP4vrZ7iIqsW+U1WykcR9ubCFGQZ4Ltth" +
                    "GvqF9UqRoecW1Y6iOd+UuHAfKD+L/EBeY4M8gyoamWvP3llqO/xvFtcf/UoQ606CFjqsJwxT" +
                    "8a3VJLBrA1DBLTwW3UQ0Jxp1+wIDAQAB"),
            new Anchor("SSL.com EV Root Certification Authority ECC", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEqhJHkJgb++/DQAeDIE7xMIKiBtHykoZh8vYhaMoA" +
                    "xMfqQwBUhtz9H98AuEFiXNxwFjLeH5nUzMUHyAgfYRYHUT19XAdT4zU4jN/Nn9kuDUq2GS5a" +
                    "cFoG7b7wobDK0Akp"),
            new Anchor("SSL.com EV Root Certification Authority RSA R2", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAjzZlQOHWTcDXtOlG2mvqM0fNTPl9" +
                    "fb69LT3w23jhhqXZuglXaO1XPqDQCEGD5yhBJB/jchXQARr7XnAjssufOePPxU7Gkm0mxnu7" +
                    "s9onnQqG6YE3Bf7wcXHswxzpY6IXFJ3vG2fThVUCAtZJycxa4bH3bzKfydQ7iEGonL3Lq9tt" +
                    "ewkfokxykNorCPzPPFTOZw+oz12WGQvE43LrrdF9HSfvkusQv1vrO6/PgN3B0pYEW3p+pKk8" +
                    "OHakYo6gOV7qd89dAFmPZiw+B6KjBSYRaZfqhbcPlgtLyEDhULouisv3D5oi53+aNxPN8k0T" +
                    "ayHRwMwi8qFG9kRpnMphNQcAb9ZhCBHqurj26bNg5U257J8UZslXWNvNh2n4ioYSA0e/ZhN2" +
                    "rHd9NCSFg83XqpyQGp8hLH94t2S42Oim9HizVcuE0jLEeK6jj2HdzghTreyI/BXkmg3mnxp3" +
                    "zkyPuBQVPWKchjgGAGYS5Fl2WlPAApiiECtoRHuOec4zSnaqW4EWG7WK2NAAe15itAnWhmMO" +
                    "pgWVSbooi4iTsjQc2KRVbrcc0N6ZVTsj9CLg+SlmJuwgUHfbSguPvuUCYHBBXtSuUDkiFCbL" +
                    "sjtzdFVHB3mBOagwE0TlBIqulhMlQg+5U8Sb/M3kHN48+qvWBkofZ6aYMBzdLNvcGJVXZsb/" +
                    "XItW9XcCAwEAAQ=="),
            new Anchor("SSL.com Root Certification Authority ECC", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAERW6pUMSmIzaeXyiNF8uWImQ/3HqOHcwIs6JxJLqO" +
                    "SbkEG0eWWKstlcjtngg1yCfriYxTWOtiiv7wWw9rMVJjQTuJzezsto0Z0zQH3LvGBn/CRZXs" +
                    "y3+oI+AJ6YH680fT"),
            new Anchor("SSL.com Root Certification Authority RSA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA+Q/doyt9y9Aq/uxnhabnLhu6d+Hj" +
                    "9a+k7PpKXZHEV0drGHdrdvL9k+Q9D8IWngtmw1aUnheDhc5W7/IW/QBi9SIJVOhlF05BueBP" +
                    "RpeqG8i4bmJeabFf2yoCfvxsyvNB2O3Q6Pw/YUjtsAMUHRAOSxngu07shmX/NvNeZwILnYZV" +
                    "Yf16OO3+4hkAt2+hUGJ1dDyg+sglkrRueiLH+B6h47LdkTGrKx0E/6VKBDfphaQzK/3i1lU0" +
                    "fBmkSmjHsqjTt8qhk4jrwZe8jPkd2SKEJHTHBD1qqSmTzOu4W+H+XyWqNFjIwSNUnRuYEcM4" +
                    "nH49hmylD0CGfAL0XAJPKMuucZ8POsgz/hElNer8usVgPdl8GNWyqdN1eANyIso6wx/vLOUu" +
                    "qfqeLLZRRv2vA9bqYGjqhRY2a4XpHsCz3cQk3IAqgUFtlD7I4MmBQQCeXr9/xQiYohgsQkCz" +
                    "+W84J0tOgPQ9gUfgiHzqHM61dVxRLhwrfxpyKOcAtdF0xtfkn60Hk7ZTNTX8N+TD9l0WviFz" +
                    "3pIK+KBjaryWkmo++LxlVZve9Q2JJgT8JRqmJWnLwm3KfOJZX5es6+8uyLzXG1k8K8zyGciT" +
                    "aydjGc/86Sb4ynGbf5P+NGeETpnr/LN4CTNwumamdu0bc+sapQ3EIhMglFYKTixsTrH9z5wJ" +
                    "uqIz7YcCAwEAAQ=="),
            new Anchor("SSL.com TLS ECC Root CA 2022", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAERSk1c/rCuCPOFH2osU2gWzbuKixTw2AJNbIkZiZp" +
                    "wLOV1l2SQBkOxqUTcPTvElEoXefMvfk8hcHPlJDJK86SQlhZZ/2UJxBkjE8EsU1J5HtPm/Xn" +
                    "CPgDiPenw5JLGVSB"),
            new Anchor("SSL.com TLS RSA Root CA 2022", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA0KQJck9AiBJhPjUjnu72dM8ve1g9" +
                    "zjwNECiQL5f3jEjYoNglsUywEUwXc1DQIkpju4HTKW7VtQk+Jhh/shJ/k5i3r/A2v/LuGJ6c" +
                    "O1LFRxlddPNkZtVdx2i0vxscBqO8j0Ajth7GhL1RxBs5wZXSKexLrnstvzn9tGLelntBxpyg" +
                    "4AZy+/AHlwk5gXSv9zRZEVcKwlvBJPQxczCCxp26Avc+fERfgw3z8d0gaRYJUOLUVbbggHJ2" +
                    "bkxHt3VVWbRTdNmUxkGtWIoxZg8eohspQE4v33vmFiwt/L/s87T6vhj2m0nU7gVu2TTznPHs" +
                    "AYvRIMYPoLW8F05Ie1HC/OlcaTdHZrNo+BUo8LnTpBXMWk+6UnCjEkXdxrpO+8LQ96hSJ21u" +
                    "ebWM/HuMwRZM7oB/vvB2vkFTEjOuWjhCq9cPPkGNdgcy1auJ9k5n2bFCdSNu881CsvxV9VOH" +
                    "FzvAM1jxUtL5gKTw6PA7izjMpMaQfw+c/YvRo8/ag6dpyVA21VwF0gpBdNtjETfBpaCWSx6M" +
                    "FhJ3rpQ0ex5/wmYA5KqD6oqQrc42RE3RUem8H/NqBf3AdB8lGUBRbuqCUUDfm7kIKgYC1SMc" +
                    "E9bp29vGsHrLeyeb++DVRiTtEEtjS6UFj7q4HSum+pHiklK97Otnl22aLZ+BMgVnMvtICD/Z" +
                    "JbgEJS8CAwEAAQ=="),
            new Anchor("Starfield Root Certificate Authority - G2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAve3BA/z2j/wCsW9bn0jZnXniorcD" +
                    "YVYYw0e218o9NS6JQ/ehaZveihr9EyCctEl3MilW/bnsjN0i+nLcJ2GX7vZahOxuGbmJLNyE" +
                    "W9V0+2tfxYmlEFKJRlX0uHUc5n/kVK5L+FVyVwIZ+BdxWeseKAd0xZ1Ivmy09KSw82Q3eZLA" +
                    "7EZef+FtU0xir80fC2O7Op37/HkAmGF0zyaCQGPzsnJqGQ2ZytQOdcw3+4uJwVnxYn9fs19l" +
                    "MPint012Wh52XjTA6JZWmYqz8H+kzb3cMjF8kc/gXxH4a6pJXNGZlNGi42NbCXa1VmLhS3Qd" +
                    "ltQm1AgEWdCYDg7m3vzD7B+Q8QIDAQAB"),
            new Anchor("Starfield Services Root Certificate Authority - G2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1Qw6xCr5TuL1vhmXX46IU7EfP8vP" +
                    "nyATbSk6yA99PPdrdjhj2TZgqJteXACAsi9Zf/aH+SVDhudpG1KakOFx49gtDU5v9shJ2bbz" +
                    "GlauK7Z0FOvP+ybjGrodli5qO1iUiUdW/yWgk3BTg9qEdBTDZ54EaDrfjkBaHUpOz0ORO+dW" +
                    "1gBwy1Lue32uOue8MflF9sJgzxNZAiuAzDRH37nekGVtAs8skaam596FGEl8Zk6jOm2pte40" +
                    "LroNA7gz30frsWuNJdmbzoHRRUYylnCH3gIOSUOFtmxzu2TqYUGsydRU34cvxyKyJsyfWVRo" +
                    "n/y+Ki/EVRx1QGAXhQJVOYt/BQIDAQAB"),
            new Anchor("SwissSign RSA TLS Root CA 2022 - 1", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAyypo4gvDV7w1Y7xwpTvzjDxOV5Zu" +
                    "w042pPYCyh6qrrjeqK8ddtq6NdCRcAffswbyivIuVVF7uywky3+SJoCjtJT2gqGk6Pp1HVnz" +
                    "B2phZOLGjJWvo7uOb1bPccxegWENbfKrAi6kl+Vx/IqwkSBbnHRSba4VJ1l48gnKZQ5/y/Tr" +
                    "59ypTHf2KxYEla6ccaU/KtpBQuc8hBD04T2Ma+IrkUdVT7hWvkXeIlFNTijZXxlBBo8OTQbg" +
                    "cEAjAWrkyxObc6xNFEiSLf5tp/iHa3l14b4QsaqIQFlU18/E0JtEszhpZIyB0SN+qjk8Ow+f" +
                    "SnuCymtvyiI+MdCw0CockoqP2BmcR+Q+DLnCzb5BDPikRwXbwRcwODpp3M3DaSP9mg8CzhBq" +
                    "zsr4uSmjNomGrgvAT2O5BllJXg7BabMK83d+Lp2MsyeY0pmNJacfhrOmVHA4/H1d6E+DDNGT" +
                    "5RLkVNo+8q063j48RfAoDwa54duXe5lFnt3+lVkEL3U/066JmYasFLSohPrIXTsbWJPBF5RV" +
                    "yAvjgnmEn/MAhDTu3DHVj/L6T5ZMBqp4+95koiPNHz7FjLw3VA67WnJV78hbtXL4eN83IExX" +
                    "kXOSc6wYd0OCIGns6awpRuULTvg3c4mWihxtve++2Lb0ysD9R/CuC1ggxcgdNq6XjVCDJiQp" +
                    "9507DwUCAwEAAQ=="),
            new Anchor("SZAFIR ROOT CA2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAt7w+UKhLzUC1zmHnlsq0odoMIrD6" +
                    "tXt2AHeMC899qIbMJlHkID2FDNZY4+f0Khid2tGuJu7rU9z0kNYTSgyQPMP02tKODZI63LGx" +
                    "/zjew7otX4C5Ar1KnRsPtMPCwWcD3dwbnD2zsN4AHqg0R7ua6/4LFL02hNoNIL/6W8upFiCt" +
                    "OWDuL3W255ec+T79fk1vTS/viA1q+t3xPW4gpaAStE1wuc7XcjuJk6eAhBwnSXJJtf87lZ7B" +
                    "zMgB7OgOigqW57Omh+XW+QUrDZdAcDy6rHVanNVNnQIK0kubZktGBxdlrZ9siADcIong4WTU" +
                    "Z7wxeWE8u8pBzVxqAMg8OI5YrwIDAQAB"),
            new Anchor("T-TeleSec GlobalRoot Class 2", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAql/aG1/oc5Hl2lz0ouZH5fNoVWAF" +
                    "HQKks5tZ8x6KrzSt/A3C2UgZ7mmPySD8IaoHGe2wXKxlx1/tAnx7fC0b1rq5gMIYghaE+maw" +
                    "CMZUI4HkzblJP/ZPbjdIKDgPxb7naHD9OZdN0seYkVCqxESzI305R+lSYtYSk163MZZCBft2" +
                    "px6j9cL86XrFbKlxT+rLeLxgr8fe9NnLvn4zpW6Ug/A0+iGr6o5yoD+k3jBb74ZNapVbQ0So" +
                    "EBUc5QFXxZjx5gYokaogxbdTJlFDsgsRlVjhwA922cCNfIHzcnCeb/4ajtlfNcaybzR8vkhP" +
                    "4lo519ideJ6fhj4DXhmLRKLVxwIDAQAB"),
            new Anchor("T-TeleSec GlobalRoot Class 3", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvXWT8GIibySu4Hp2rH292STVuLf8" +
                    "zfBC4Ot4iFZem5pUHU0MivbTz3D0UrXYkwTjRoZxQUor8CosVQPWSMPgOTjt8lw8P0S8kz1h" +
                    "q07NDb7wICdYDkR/BBqHpdeWFDaQ0El7oXX7GmtzsfjOqQks8lPVwxREuIal9osrOdqjM1TZ" +
                    "+nIa9yIVHIiRa39m5cNqgLAk89+GRYj9GX91hx8fsRsKcyRbuWXgLFTIYNNmFz/hzFQzc5EC" +
                    "OqZ/e3Y5oh+WtjiutciTdB2eubTlYJ0vVtHg615bTBJwDGxEIKsR2PQZ9tKcUjfn+rbCMTtK" +
                    "1BSZrcca9V1f+ge4fA0f1oMeswIDAQAB"),
            new Anchor("Telekom Security TLS ECC Root 2020", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEzr/+V6i/1ar3EJrNvNERor1nQsyQ6xUYkNmizQwq" +
                    "Jes+T8610o8P8zXaQ4sCgL5vUSQdD2sryp/Cb1Ay5TcgtiD/iA0PbUm72wakh5CSlPQJ0M9/" +
                    "yIALwZezuzUnycIb"),
            new Anchor("Telekom Security TLS RSA Root 2023", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA7TWhgYDzy0ppW8L7UYOuJv3hbvOB" +
                    "En1xQP+HdUIpIe2BUizfEsEZhInBvcUo1dVLbETWTNsHlkpVeso2ggQ2qKX8J/ZJ8dVynpH5" +
                    "I9Zwe7v1m8Hsk88Z6mV+iHCgc/z2/7VWYuFzajSYPoK4rJVT9AGgJwdyowBToOSyq4M4VzMl" +
                    "lJ++SB2Y4aO6nlzNBHFRfXV4q/NZqsTgYL6Pg1K4dRpBNe288zpj6akURdfmUtFu0t684/UL" +
                    "O+bgxL1DZBOmzvSYN2yKlaiXyEcP8F4Qi+cdHP6xO6AFM2gFQYLBAysByOePTavotfbNa0S1" +
                    "592L7OoltAAiV02wsbIxwRbO//0UhLdH+rLxcN7bi2w2WKR8sxHRw3d/X7Yl4A3F0rP5uLh3" +
                    "2zdxcUfjYBhPJLZ1N3i5o2Kvvclyji/Mu67b5BVSGQcz+2q3LUuQKIJz/hiLNY3bpwRqvurB" +
                    "TTY7FjaRMu+2QImRQ+DyoqsELubyTA4WNCCsh8EtfslmRxcUEaTz96EkiavYGsihXLGj94xt" +
                    "yAHJT8nsxPysUTPRyIPRyZ8d1Ec0KT7LsA76gwsoWOUp3D98qJ/Jtgq7puhGFg+W5XvkanpI" +
                    "bXaYBaXcbR5CHkLaGuBS97WDwBp7eDUsOPUf/UmjLtJZY7+AsIyTc8s1ppmVImFlA2D7L5NL" +
                    "+pqcgDsCAwEAAQ=="),
            new Anchor("Telia EC TLS Root CA v3", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEwciWFUMtuYUpSlbSIrh2mvJPp6Zg55LfUk/BadY+" +
                    "aRZGsCRNs9fjSxB8cj2alHtFuS27eeCDpb4EFJdJ5yG0wKcGZZeP8RpWWZXlxjWMPYehN+EF" +
                    "DcBpykI0wskrg2ll"),
            new Anchor("Telia Root CA v2", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAstA/B7zie9Brmfjid2nnzp2kA7yC" +
                    "baH+gWUfTCesjgC6FnvrMGoAwLN0aH6yr8fVYrN6P1DKjDZEJGPSNukMhfZDdtVMoWByZ+Io" +
                    "M6XLMbg6IiM0uH29ViJAner0ewOtaPyygU+Y0HTqjeV9zWPDo/beksJYGeCWu8XEqT2ldJb+" +
                    "r/mJqr2VF1TYeETxDHcVkuCYQqek1qogks3BoLOWsjqEQo191ZXk1tvpYsRYs3nFjNM1M4Of" +
                    "daFSJ2E48Vk9jlDgvXk852yW/l7ZAmW0jlzQETTfXb9Sp4EAw3+ZRZkV1RfIClPsY/OZfcxp" +
                    "EobCF/ABnr+EvNFSyxuSZs6kU+Whv8TbCdbmiVYryON83uP/ieU1bijobAsjUaklBetI+N2x" +
                    "yvpsCFHvtxhsRMom4XPGiQaB5YqssOIpxrkks2tEEfSlQ8JMQ+VwNoy2M1d6lS6CoPRcELNh" +
                    "g/YCBYYufC1s3ANGbjWT1XqVL94g2Ft+lJAEarpZPQQFdZ03og4uPevBpFKD/tBr1GaO3Mbp" +
                    "Ek4dKleqELx8XoJ9pqbJ8i259RcnrdEOiVQrlfrArR2YFHgzQoYKqXO1+3QNtxswGcRaDhwn" +
                    "t9oY0P+KyAW68aocoje35kikRiyU6qh2YkeLEFMHSFds4pJNtq4Fy9zBSl6PrD0ZTsLtYHUr" +
                    "28HKQtUCAwEAAQ=="),
            new Anchor("Telia RSA TLS Root CA v3", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAsV89KG19hCf4S1Fvk8D3TyDERhmc" +
                    "vx8F7Kmb4WATx3ije1id3KHxRE0TKmcNCbAQ57bvHFEYa4hR2l20VjVadExqOW+2ld99MbEi" +
                    "O+jRVOz+BbxLxJnmGwCqI+BfuTjjVReDxsxjQvjgBsClaO/sm5i70nlZcWGRtIkvWDK3NNkT" +
                    "5RtwXc/O8NTFVpbUqT6cRjIj3olAblR+lRf4Ffy5o+Q9fabjYn9Z9S4itruElcEFf9Ljk7fw" +
                    "dTycT/rvJW9w/B3G2a3r0f/zXNOVruIBcqE6pkSospACU2bG42fYKrbM/GWnp7u+p9Frz4ja" +
                    "Nwpb4YHuEeS8BratNcP8X62jXIvvKHxlsMDJCnb4U8JzFOLsU6mohVY58BdZrvi0Gk9UOuqm" +
                    "goG6dskHoksjZTlK61D/InzmEoA1yAYJFDVysjRxDUOu9cAwANbqmq77WIFL6BpnZgVqPtMf" +
                    "G6wN8BrTKdapvilVsYR59BFgIsAVBMxrGh+W+QcvmJafUpASvlArKvVG2FI4i6PiLjSBT0+6" +
                    "F6EQLrYqefOQF/fBNEXb+njUQ0SUVrAqtH4Y+OjCI/a4/JJQppxeemZcQ0SUShgiI5AM5xHO" +
                    "5iyaUrTjYH4zxUz9j+1FEbDH/xpstr1gXBykspup+hRTaJcbA+UbpJqtWZndAPddJmt6YJQ+" +
                    "dU3pDu8CAwEAAQ=="),
            new Anchor("TrustAsia Global Root CA G3", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwDGCYZLklBsKKmXQvgaphztREupw" +
                    "Qa7i+3TqCo25s0zcj7cTUk9UGOEsc5WRxWY7as+sY22HU/D38Tm3oENjsMQDXVep50TOxKGD" +
                    "ZfZQPrF+Frg6igLQlh8AzQUh7wZt3SGcGUNFocXogMrCrUBiFwbGqrzz1ub8UH5mQh88i6Z5" +
                    "eYZANZ8g7z/ri0cfj47F1I62LMlEBOPUQ3U/1T+vHMx+Rl+s32QQiu9G8JDwDy30iAuxKaqv" +
                    "hapJWKi/Y6A4keaz5ndoxPkqGYS7DuH1r4nspS9QIHQeEkFzHiTZys4soVk1wMgdRidhWo/5" +
                    "TdNyeWYenxWQIS397YtWcANKST5/aTESacceXMp6E4vo5vVgD8yTLIR/8fxq/JtHndutiD3z" +
                    "dnUz10ukyIv59UNYT8vIA1SPpYV4BBrzc/LXhx1Bn+fYF84anA9K/NxEaFRo4kE8/iyEhjc8" +
                    "zT8votvn91QDX1nT95F4x4t3ahblSYWQRXJwL5Fd+D5lQAsZmckmIFpowTW/T6dR8dgRK1vg" +
                    "mp4oOwo6Ch/BgeUu8Ka5aaWIlOZrE3/RZD89nHBG5aKFe1iEJ9zEgD5nmprHmjEOMOzmF0CV" +
                    "2UXtAZaqvwzzS9Fj9xNYwLjz+mfdm31tSv8yTLUlO/8cZw+FIlkFkZFBd4HQhUyHEHH/nkMb" +
                    "rpV1LYECAwEAAQ=="),
            new Anchor("TrustAsia Global Root CA G4", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8bPNOOQlQ+XeGQm7gXmiFV8VYwHewqvds6YbZ0uA" +
                    "g6+Zy6wX2yuWynxSVeIa4T1W8C8WCPoVvJu7R+Y/7qjhTIz10zb5OF2rcJpHDeKBQQbrSfmw" +
                    "Kd0z7FClf3kpuCCY"),
            new Anchor("TrustAsia TLS ECC Root CA", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEuH+lWz8BPn3wiG2uKZjhm1xTmdv3CP/VauCOy0Sm" +
                    "8MGMvU/UztSIU+heV9dOviw/8xKnAOmC4ypbMnxLmxQpnvgtg7XrjjFJPSZh6R1yi4m2Cruc" +
                    "MzUP2uzeqUofyTOx"),
            new Anchor("TrustAsia TLS RSA Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAwxa4G2qkRHPl1k70uc9bC8HRmoH1" +
                    "Y7CPQ8G7CFoaeuEHdiYfj2lWvv42YNAMg82U6ufFLVwvBRYCngqoL+VgJlSW+JhAHayunXRn" +
                    "L1T7lGOY4yZKg8aVtglDUM0Nfd5EDmAST1s1vb+ZOG19bNriaHMf+TGlGRBzBSrDMhmF6jSq" +
                    "3UIeMI0/nrUeYdVvvQBycq0SP6qmSXPyhhWVDBBfUWTO/3e4yW2s5dWY8ZkubOPJRPm1QycI" +
                    "TfZ+3kR5u7KMHNrTS2wu1sN4t0zVpOTa3IqODv8Pw6ZgJijPNr/6VwrsWz/pMBZ7xNvE1GSg" +
                    "MPvl/R1xkt0pj0FY3gCtOv09fBqosRfwTjR4WCXWhSHreR3Q3KvPYjywx5eL1tCf0/483sXj" +
                    "/DqDcIQhHQnCofy7KCFlU2B6kG2WOFv/8dd6W23TyXBJSrodOtBQ2jIgGeSLP+vVFiY3PPt1" +
                    "nrAHcLgUYHfc9g9cUgq8XW2NUQraxRjIm23csIOzf6NOeEyYJavyfi4gXoIVptbYj6zlzTKG" +
                    "yPnk2oniOz6TxRNq/8f3GPxnte8YLFSrQNNsTe3FyLc4T2TElExPoM2upcYnJXUnbcbanI6w" +
                    "BZnVKBHFIdS4/MeWq0pkWWFrE/xLzJKmD6RRvw5YkNmCgzmmX7P6bpKzzTMOhajxKdOrWa3W" +
                    "297UK4ECAwEAAQ=="),
            new Anchor("TUBITAK Kamu SM SSL Kok Sertifikasi - Surum 1", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAr3UwM6q7a9OZLBI3hNmNe5eA027n" +
                    "/5tQlT6QlVZC1xl8JoSNkvoBHToP4mQ4t4y86Ij5iySrLqP1N+RAjhgleYN1Hzv/bKjFxlb4" +
                    "tO2KRKOrbEz8HdDc72i9z+SqzvBV96I01INrN3wcwv61A+xXzry0tcXtAA9TNypN9E8Mg/uG" +
                    "z8v+jE69h/mniyFXnHrfA2eJLJ2XYacQuFWQfw4tJzh03+f92k4S400VIgLI4OD8D62K18lU" +
                    "UMw7D8oWgITQUVbDjlZ/iSIzL+aFCr2lqBs23tPcLG07xxO9WSMs5uWk99gL7eqQQESolbuT" +
                    "1dCANLZGeA4fAJNG4e7p+exPFwIDAQAB"),
            new Anchor("TunTrust Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAw83T/L0EU90MIDrViC4FS0H1g4J+" +
                    "91mfnp5j6HPa9gapTx+0+QsfOYyaINB+BtTsNNmGvHVbh4jw0tnUowqybBvrSSw+rF3YlAOg" +
                    "7DTlMMQ1ffsmTRtuMFTY9YBFnDmtnMklBE2akD5OQG6Ka80pZ8bMLeB06AVXCkhQ+npD2n7s" +
                    "W5oOYnb+6p0dhXLsEbs16B8nv8Ghx7tIFt1W18xOoOG5rNvVgxkahdGUl9fKo2UL8zj5Aq7d" +
                    "9mfPyT/1iixHGplvBQ390B2CMfwpzABYl5FMgAAcM4WWL8tBwosQhMMJJIkftQ/Z2XdHGJKU" +
                    "YFzHmQM8/veVp31QoYDCqYOtWJZVIduGWdSvxrzdgW4H22Bi/uwQbtpoAfSDG6k+olsj12TG" +
                    "39yifdhLuoLSUfhmvwZG5HkqJjZ5jx9OmR2yjwwOHP/JXcD9kBCmsTfzzTokbrSFkL+AuQyM" +
                    "1ZvWyPFWPxqAiXqp4hsyUSw+8t979l16KRmO5ci9NnGLXUzCHT+tWKLPPXBNplCYJdwj+bhY" +
                    "QQhxv0+4hKCPAFQV/JFtWKeWO+tLlifNa6KhhqwNfFTmZkxmX5C+IZoCRi3kg8KAuc9LPuh/" +
                    "PAHsj17Nf9IoQgGViuKXPRAhffadHMU0oewsDgpSLBJVcCQ9y8IUNUNdJ06+wL2qfJbn/J5h" +
                    "rUTTAJcCAwEAAQ=="),
            new Anchor("TWCA CYBER Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAxvjKHtkJIH4dbE7Oj+NHM0Scx8lp" +
                    "qjpbeO5w0pL4BLNSUh1nciih34tdlQr+6s3t9ynO8G9/rM0977McRWr3KJDxYVfFDMSjUF3e" +
                    "1LXLGcqAuXXOKc7ShSLsAmPMRDAg2uqRW1bmHRzVnWbHP9+GyktTxNmNsh3q+NwnU6NH4WHM" +
                    "fbWw+O5zkcXOc2/O7hAfGgbP6SdgxU8Z5OvOIiZF12CZ3c5PN+B/52OtsLhZuNAGaDVg0zau" +
                    "cUME8WlleHzzH/PKKJ9aIJVmtM237o94pEUY6SYvjZspKLGktzptudQcOHJFWLFe6/Aom7eC" +
                    "yv3P1jMPn/uXnrEcnJ7qX17bqt1U6TAhKG2OefN1kowm/tzF9sOw30RZQ6O2Ayj2CDCqDTPh" +
                    "75ypByLjWVtAj9qIt2kIqLcjLkQJWTdbx+MX8iLrbjlSxd5Up5jJSyCV3EaJX7QS+YUpjuvI" +
                    "JxUgwEvUzHwMbDQMJpsmMaY8p/bZ0EuiZP87mUFyweBwl/EkuyvEdCKxrGsiMiTTeCrAwKEv" +
                    "8VIFyT/vdmbiRdgNPa2VyMeJJsgPrqcDLvvBX/og4XCtsGUgNzNgsNWv1wwcwpBw10oYvH4B" +
                    "sLDrFR5EBs2kT+gM0cMgEOFUZZ62UdAadmtCWlh2NOq3NxmuLnX5luXBWfeUVykljTpMq02a" +
                    "QdBfJgMCAwEAAQ=="),
            new Anchor("TWCA Global Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAsAXbyOuMxG6KIe+OTZxxCh9ScO1t" +
                    "gpyXxddMTkVJy0BCtRI0bBnCdKQxX4UCl+xDMwpT0pyMjre4edsr1WryjmbE7isBB5LUs9AC" +
                    "31D2Va9mDsvgR2AvKzI5NVI6KIP4exbGGLhi1kclkc7wGRJNrWP10z91XynwoTAcKqCYphW9" +
                    "7v0ZNvDikUOP+srWECdJTO/dwfGFcJvK6qhaQ/xthm9z6TdFqfA2x8yIdR67bAb/m2s+F+xh" +
                    "qnF8xh2i90npFbU81qFh9RH3BW8d/RG+0DAHwimwCU4m3OOiqJFqH8KRRYhc5Zi4caUVGcl8" +
                    "dRHMcHRPLZsdkUT9Viig/ruGasj6XAtY3MZLdsirItlzD6X0WgKJP0+eIoLuonRTKj1TJ2kd" +
                    "bI4yLGQAJmNhNk6jRrc/fbMtrG2QopWizs/agucHNBmW6bghqil+pji+jilKIWZ5H7PDtQln" +
                    "3tbUB0bzKtrmIjdgy4G2D6AP6ciVf79VkQV6zz0VwG/eCZQBg9c0G8xApfC4m2fVmJE7p4R4" +
                    "lSakWgj4K3S0AAQ837gUjujfqY1sZ5IzHcC30uySyL4JvywpBW8Ca57vvL8qvFvAUI9BcHGH" +
                    "sk23BKmEozKvru5rF4uysf5s4ZCMiKiXSM7ITcvzBs9fagpCsR4edy+OoOaSDgb8BSLSJuEx" +
                    "UX0y3A8CAwEAAQ=="),
            new Anchor("TWCA Root Certification Authority", "RSA",
                    "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsH5yuKQDlOan3gk4kUoRQIenfFlk" +
                    "FHu1ERDd/r/VwLtW4oUl9DVyD/hT0EHhRAHCtBzDMUIWR4UzInayCm8P5SVQT4WGvr+YLhBn" +
                    "Hr4RBYYFkMRZ0Hx4ELCAXLfhxyt1y3yfrrXRnSM3Y6fcQqItkgQbUMF7uD4byVYEiy9Sm62p" +
                    "VunB/62pWIcwtoH3l0X8GVc7K2/kR/SZRf4d8fiXo4gdNxxcj+B2JZpQ+KBU/0SQdiPSMsbD" +
                    "qwa//Pu/8619kmICWynTNaOTmkNkYF2y+jL/OwSvTUBq+cfj7yP9a8vlD4s4De4K/P4PmJ8w" +
                    "Md1sUmX5i4G+IuEcWAO6kRuJBwIDAQAB"),
            new Anchor("UCA Extended Validation Root", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAqQkHKBMCsJngZKoeQxZ6c7GRoHU+" +
                    "qPrjOAB67IlqIA+LxbCbMwNahsZYhtXBhbtPxpxATcq+7mmWuK2BMJp8kgXrBSuaSNC4dj6W" +
                    "yCC70rDxj9isRUb/qmdgtHd+ah88GlJ6BD0HPIUNhNAfdgr3ahTfcuM0fFdOVgE+efGqKTts" +
                    "+viPbU3INd+u69wk7nlFp4W2BYjeiF0lfJdkZwnZv1oVBYbzCR7sWDIzEfN3ZLB2H+QQNRcb" +
                    "8g6xbKQqo3P8CR8eMhlTEefZsywudi6ho95+aogJ6PIHiviyzRDn4nNAk7sI0T/h/AuUsyXv" +
                    "fKbX0a+f/5aa9ZF7mAt31H7oB9JitZU54/PxbQ8OZYSKY1TFgLbgnkt9RyanAQhd0Yie18My" +
                    "RPqCSgpoVH84UwPMpAAzZFFZC6OCkXpe7BbC8yrmYtoq21liECVKKoELRwdDBnCH0vqTESl6" +
                    "SE3rlMdwTa9n1VGxgCABAbR6CKaQf07g7wdBh69qpV6L+89QsppUr8OJulgt9TCYsTZyOX5J" +
                    "BP0pp0x55AVX25S5FlONRrMdlWFXVn+v8BZbYVhvNlARC9isK5UWGg4fCM02NGUQYmbVgF8U" +
                    "IF8tDKB4CmjWLNfpbyvSSgWT/J5va2f/iPFOpWlKUjcF6sYWjdLEmdGCKzu6NXX3UVFY88gH" +
                    "3eS0A38CAwEAAQ=="),
            new Anchor("UCA Global G2 Root", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAxeYrb3zvJgUno4Ek2m/LAfmZmqky" +
                    "wiKHYUGRO8vDaBsGxUypK8FnFyIdK+35KYmToni9kmugow2ifsqTs6bRjDXVdfkX9s9FxeV6" +
                    "7HeToI8jrg4aA3++1NDtLnurRiNb/yzmVHqUwCoV8MmNsHo7JOHXaOIxPAYzRrZUEaalLyJU" +
                    "KlgNAQLx+hVRZ2zA+te2G3/RVogvGjqNO7uCEeBHANBSh6v7hn4PJGtAnTRnvI3HLYZveT6O" +
                    "qTwXS3+wmeOwcWDcC/Vkw85DvG1xudLeJ1uK6NjGruFZfc8oLTW4lVYa8bJYS7cSN8h8s+1L" +
                    "gOGN+jIjtm+3SJUIsUROhYw6AlQgL9+/V087OpAh18EmNVQg7Mc/R+zvWr9LesGtOxdQXGLY" +
                    "D0tK3Cv6brxzks3sx1DoQZbXqX5t2Okdj4q1uViSukqSKwxW/YDrCPBeKW4bHAyvj5OJrdu9" +
                    "o54hyokZ7N+1wxrrFv54NkzWbtA+FxyQF2smuvt6L78RHBgOLXMDj6DlNaBa4kx1HXHhOThT" +
                    "eEDMg5PXCp6dW4+K5OXgSORIskfNTip1KnvyIvbJvgmRlld6iIis7nCs+dwp4wwcOxJORNan" +
                    "TrAmyPPZGpeRaOrvjUYG0lZFWJo8DA+DuAUlwznPO6Q0ibd5Ei9Hxeepl2n8pndntd978Xpl" +
                    "FeRhVmUCAwEAAQ=="),
            new Anchor("USERTrust ECC Certification Authority", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGqxUWqn5aCPnetUkb1PGWthLq8bVttHmc3Gu3ZzW" +
                    "DGH926CJA7gFFOxXzu5dP+Ihs8731Ip54KODfi2X0GHE8ZncJZFjq38wo7Rw4sehM5zzvy5c" +
                    "U7Ffs30yf4o043l5"),
            new Anchor("USERTrust RSA Certification Authority", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAgBJlFzYOw9sIs9CsVw127c0n00yt" +
                    "UINh4qogTQktZAnczomfzD2p7PbPwdzx07HWezcoEStH2jnGvDoZtF+mvX2do2NCtnbyqTsr" +
                    "kfjib9DsFiCQCT7i6HTJGLSR1GJk23+jBvGIGGqQIjy8/hPwhxR79uQfjtTkUcYRZ0YIUcuG" +
                    "FFQ/vDP+fmyc/xadGL1RjjWmp2bIcmfbIWax1Jt4A8BQOujM8Ny8nkz+rwWWNR9XWrf/zvk9" +
                    "tyy29lTdyOcSOk2uTIq3XJq0tyA9yn8iNK5+O2hmAUTnAU5GU5szYPeUvlM3kHND8zLDU+/b" +
                    "qv50TmnHa4xgk97Exwzf4TKuzJM7UXiVZ4vuPVb+DNBpDxsP8yUmazNt925H+nND5X4OpWax" +
                    "KXwyhGNVicQNwZNUMBkTrNN9N6frXTpsNVzbQdcS2qlJC9/YgIoJk2KOtWbPJYjNhLixP6Q5" +
                    "D9kCnusSTJV882sFqV4Wg8y4Z+LoE53MW4LTTLPtW//e5XOsIzstAL81VXQJSdhJWBp/kjbm" +
                    "UZIO8yZ9HE0XvMnsQybQv0FfQKlERPSZ51eHnlAfV1SoPv10Yy+xUGUJ5lhCLkMaTLTwJUdZ" +
                    "+gQek9QmRkpQgbLevni3/GcV4clXhB4PY9bpYrrWX1Uu6lzGKAgEJTm4Diup8kyXHAc/DVL1" +
                    "7e8vgg8CAwEAAQ=="),
            new Anchor("vTrus ECC Root CA", "EC",
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEZVBKrox5lkqqHAjDo6LN/llWQXf9JpRCux3NCNtz" +
                    "slt188+cToL0v/hhJoVs1oVbcnDS/dtitN9Ti72xRFhiQgnH+n9bEOf+QP3A2MMrMudwprem" +
                    "IFUde4BdS49nTPEQ"),
            new Anchor("vTrus Root CA", "RSA",
                    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAvVV8YdO4HQRiBaCubLdwtEHqSwNe" +
                    "ED+QWhyLO7Bmi2xIphwiutVAku4zsiNZyY68WNqLntAZ8i9ZxoxjWrqfowuws5pcuhG4EukM" +
                    "u89ubICHKRQDLI0kmshkg7VqrBMsM/Gf3CxhPBo/cFWbrQBSf88Euf42+pzAFq5i/pZMQ35V" +
                    "FL4as9Jtwq92ZpVrKrCUd4VeBA9iHWN192vny1uacOw+ZwXw/gcIgM8o2wXGFCcvhn3wJ97/" +
                    "5n4zSOcLHljRJytTDldKZdf7ooBg/Ey8NVMBapdygq/xHXDonPXvXsJsx0d+WpSFJk07uutM" +
                    "6LAJwmXCnZ0Jm061lwWs9Qag9zYFfvSQsmvEtPlk6ukaCsgNqO0nydTns7mrgiKQJz0q6HyQ" +
                    "77xP/eIKJKfeZSSkXerAdjDTd1D4DQSblDYBc8oGWKbTO9z6BEYTVYrJREe4UTkaLug04nnL" +
                    "WUoKf7ym7x8DZ2pZKyVik9lTGWY8J2Iphk2ka+7/1E661bTijkhaABkJ8QXZzpGx9+vpOU/2" +
                    "bwRDmlX1PgUUvb+zWbTYjjOEo5BSqrMClWD5DExo+e7VFw34cVe1JeQp7mVdr9HuPBcLWkPF" +
                    "pYbqJJ7iBQfcNEISkdY5dK5MQYLb8qZI0bOb8zOq86bAxU719J12Y+YCxiJLwZU/UGQsVOW2" +
                    "8Dwpz1cCAwEAAQ==")
    ));
}
