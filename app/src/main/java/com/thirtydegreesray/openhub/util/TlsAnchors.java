package com.thirtydegreesray.openhub.util;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The root certificates of the four certificate authorities GitHub allows for its domains
 * (its DNS CAA record: DigiCert, Sectigo, GlobalSign, Let's Encrypt), as their public keys
 * (SubjectPublicKeyInfo, DER, Base64). {@link TlsGuard} accepts a connection to GitHub only
 * when the certificates the server sent lead to one of these keys. The same keys, as SHA-256
 * pins, are in res/xml/network_security_config.xml (TlsAnchorsTest checks both lists).
 *
 * Old and new roots of each authority are listed on purpose: GitHub can move between these
 * authorities and their roots without the app breaking.
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

    /**
     * After this day (UTC, epoch ms) the extra check is switched off and only Android's own
     * check stays, so a forgotten update can never lock users out. Must match the pin-set
     * expiration in network_security_config.xml. 2028-05-14T00:00:00Z.
     */
    public static final long CHECK_UNTIL_MS = 1841875200000L;
    /** The same day as written in network_security_config.xml. */
    public static final String CHECK_UNTIL_DAY = "2028-05-14";

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
                    "7e8vgg8CAwEAAQ==")
    ));
}
