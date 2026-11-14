package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The root keys of TlsAnchors and the pins of network_security_config.xml must agree. */
public class TlsAnchorsTest {

    private static String config() throws Exception {
        File file = new File("src/main/res/xml/network_security_config.xml");
        if (!file.exists()) file = new File("app/src/main/res/xml/network_security_config.xml");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void everyAnchorIsAUsableKey() {
        assertEquals(TlsAnchors.ALL.size(), TlsChainCheck.keys(TlsAnchors.ALL).size());
        Set<String> names = new HashSet<>();
        for (TlsAnchors.Anchor anchor : TlsAnchors.ALL) assertTrue(anchor.name, names.add(anchor.name));
        assertTrue(TlsAnchors.ALL.size() >= 100);
    }

    @Test
    public void pinsMatchTheAnchors() throws Exception {
        Set<String> fromAnchors = new HashSet<>();
        for (TlsAnchors.Anchor anchor : TlsAnchors.ALL) {
            fromAnchors.add(TlsChainCheck.sha256(Base64.getDecoder().decode(anchor.spki)));
        }
        Set<String> fromXml = new HashSet<>();
        Matcher m = Pattern.compile("<pin digest=\"SHA-256\">([^<]+)</pin>").matcher(config());
        while (m.find()) fromXml.add(m.group(1).trim());
        assertEquals(fromAnchors, fromXml);
        // well-known values: ISRG Root X1 and DigiCert Global Root CA
        assertTrue(fromXml.contains("C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M="));
        assertTrue(fromXml.contains("r/mIkG3eEpVdm+u/ko/cwxzOMo1bk4TyHIlByibiA5E="));
    }

    @Test
    public void pinsNeverEnd() throws Exception {
        // no end date: the check stays on for years without an update
        assertTrue(config().contains("<pin-set>"));
        assertFalse(config().contains("expiration="));
    }

    @Test
    public void githubsAuthoritiesAndThePublicListAreIn() {
        Set<String> names = new HashSet<>();
        for (TlsAnchors.Anchor anchor : TlsAnchors.ALL) names.add(anchor.name);
        for (String name : new String[]{"ISRG Root X1", "ISRG Root X2", "DigiCert Global Root G2",
                "DigiCert Global Root CA", "USERTrust ECC Certification Authority",
                "Sectigo Public Server Authentication Root E46", "GlobalSign Root R46",
                "GTS Root R1", "Amazon Root CA 1", "Microsoft RSA Root Certificate Authority 2017"}) {
            assertTrue(name, names.contains(name));
        }
        assertTrue(TlsAnchors.ALL.size() >= 100);
    }
}
