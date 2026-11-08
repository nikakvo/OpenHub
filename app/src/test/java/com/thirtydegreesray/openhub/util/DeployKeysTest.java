package com.thirtydegreesray.openhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Keys made with Python cryptography; fingerprints as ssh-keygen -l computes them. */
public class DeployKeysTest {

    private static final String ED25519 =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMUNEIubdQoPjHPbVO0ULXFZcH1e3R1GVX8+6G4hpCh4";
    private static final String RSA = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDzSLby/8Co1EjZC9D2UI43dwY1Gdxm"
            + "oGbmqlcUrEdwLqKZxN6dgupIXhp0K6HB1RTb1uTziyNvIZ2CywX0FV4PmYAww2V5/iDXMz9VD/JNaGVNcI4ry1ihxMATmaaZWm8Z"
            + "gC+bqwK6KwSwVoDCDaSWGPHRfOqnhXKPiWA7KXus+IbVkf3+tzHlS5qsZAkvBwc2+Js9RAFrP7qb52b/YNnRq/pEn0LU+/3ejhcD"
            + "P4nCaQSHSRMzFr346V0NiQBqTYT9L11TWnNhgNxdCPFtx5P83PXykG7TEgQ9Q12njsCcZXBHiB/HHSoLHwN8aAvwLW6qY4WZJhfV"
            + "+1zU95tlYGkQ9KXA+i9R+foAZ4T2pkDnoVayjz4WyKmQHWZeK6UdIvbpvNJPM1r4H7rTeEC9UoRpjBd9rNvD0souN34Z2YkV10H7"
            + "Td5dBRVyyQqk2/xJN1EuoDicZq0qylFWNx1gobTsI8pZuSsGAi47wU9dHlKfM0MJCw/XfTN3v2XqR6zQs/k=";
    private static final String ECDSA = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAAB"
            + "BBDci3NcD3Glozts8wS8ktSpPqXUJce6OJjYRyCcDJuFXCeY4FE77OkdOk7EKNqsJJEZwobnaLA88WmJ6d+gqBYM=";

    @Test
    public void fingerprintsMatchSshKeygen() {
        assertEquals("SHA256:zrD5a8fM1/yaOa7qhH0r14cGTkrkP5SeEmMyb2TIk3c", DeployKeys.fingerprintOf(ED25519));
        assertEquals("SHA256:5UjXaY+STD/mOp50e4t1ya0rMC3SMOeHArLLpEKog1Y", DeployKeys.fingerprintOf(RSA));
        assertEquals("SHA256:Zau6yNg465+CV/zb2B7zW5vr3UoikJm562hmATM7SpI", DeployKeys.fingerprintOf(ECDSA));
        assertEquals("", DeployKeys.fingerprintOf(null));
    }

    @Test
    public void commentAndKeyLine() {
        DeployKeys.Parsed parsed = DeployKeys.parse("  " + ED25519 + "   phone@termux  \n");
        assertTrue(parsed.ok());
        assertEquals("ssh-ed25519", parsed.type);
        assertEquals("phone@termux", parsed.comment);
        assertEquals(ED25519, parsed.keyLine());
        assertEquals("phone@termux", DeployKeys.defaultTitle(parsed, "2026-11-08"));
        DeployKeys.Parsed bare = DeployKeys.parse(ECDSA);
        assertEquals("", bare.comment);
        assertEquals("ecdsa-sha2-nistp256 2026-11-08", DeployKeys.defaultTitle(bare, "2026-11-08"));
    }

    @Test
    public void commentLinesAndCrLfAreSkipped() {
        assertTrue(DeployKeys.parse("# my key\r\n\r\n" + RSA + " ci\r\n").ok());
    }

    @Test
    public void privateKeysAreRefused() {
        assertEquals(DeployKeys.Problem.PRIVATE_KEY,
                DeployKeys.parse("-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNzaC1rZXk=\n-----END OPENSSH PRIVATE KEY-----").problem);
        assertEquals(DeployKeys.Problem.PRIVATE_KEY,
                DeployKeys.parse("-----BEGIN RSA PRIVATE KEY-----\nMIIE").problem);
    }

    @Test
    public void problems() {
        assertEquals(DeployKeys.Problem.EMPTY, DeployKeys.parse("  \n ").problem);
        assertEquals(DeployKeys.Problem.EMPTY, DeployKeys.parse(null).problem);
        assertEquals(DeployKeys.Problem.NOT_A_KEY, DeployKeys.parse("hello world").problem);
        assertEquals(DeployKeys.Problem.NOT_A_KEY, DeployKeys.parse("ssh-ed25519").problem);
        assertEquals(DeployKeys.Problem.UNKNOWN_TYPE, DeployKeys.parse("ssh-dss AAAAB3NzaC1kc3M=").problem);
        assertEquals(DeployKeys.Problem.BROKEN, DeployKeys.parse("ssh-ed25519 not*base64").problem);
        // RSA data under an ed25519 name: a damaged or edited copy
        assertEquals(DeployKeys.Problem.BROKEN,
                DeployKeys.parse("ssh-ed25519 " + RSA.substring("ssh-rsa ".length())).problem);
        assertFalse(DeployKeys.parse("ssh-rsa AAAA").ok());
    }

    @Test
    public void innerType() {
        assertNull(DeployKeys.innerType(new byte[]{0, 0}));
        assertNull(DeployKeys.innerType(new byte[]{0, 0, 0, 9, 'a'}));
    }
}
