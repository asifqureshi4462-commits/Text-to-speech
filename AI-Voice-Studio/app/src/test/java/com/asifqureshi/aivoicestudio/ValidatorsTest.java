package com.asifqureshi.aivoicestudio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.asifqureshi.aivoicestudio.utils.Validators;

import org.junit.Test;

public class ValidatorsTest {
    @Test public void emailValidation() {
        assertTrue(Validators.isValidEmail("a@b.co"));
        assertTrue(Validators.isValidEmail("  user.name+tag@example.com "));
        assertFalse(Validators.isValidEmail(""));
        assertFalse(Validators.isValidEmail("no-at-sign.com"));
        assertFalse(Validators.isValidEmail("a@b"));
        assertFalse(Validators.isValidEmail("a b@c.com"));
        assertFalse(Validators.isValidEmail(null));
    }

    @Test public void passwordStrengthMatchesBackendRule() {
        assertTrue(Validators.isStrongPassword("Passw0rd1"));
        assertFalse(Validators.isStrongPassword("short1"));
        assertFalse(Validators.isStrongPassword("onlyletters"));
        assertFalse(Validators.isStrongPassword("12345678"));
        assertFalse(Validators.isStrongPassword(null));
        StringBuilder tooLong = new StringBuilder("a1");
        for (int i = 0; i < 130; i++) tooLong.append('x');
        assertFalse(Validators.isStrongPassword(tooLong.toString()));
    }

    @Test public void nameValidation() {
        assertTrue(Validators.isValidName("Asif Qureshi"));
        assertFalse(Validators.isValidName("   "));
        assertFalse(Validators.isValidName(null));
        StringBuilder long81 = new StringBuilder();
        for (int i = 0; i < 81; i++) long81.append('n');
        assertFalse(Validators.isValidName(long81.toString()));
    }

    @Test public void serverUrlRequiresHttpsExceptLocalhost() {
        assertEquals("https://api.example.com/", Validators.normalizeServerUrl("https://api.example.com"));
        assertEquals("https://api.example.com/v1/", Validators.normalizeServerUrl(" https://api.example.com/v1/ "));
        assertEquals("http://10.0.2.2:8080/", Validators.normalizeServerUrl("http://10.0.2.2:8080"));
        assertEquals("http://localhost:8080/", Validators.normalizeServerUrl("http://localhost:8080"));
        assertNull(Validators.normalizeServerUrl("http://api.example.com"));
        assertNull(Validators.normalizeServerUrl("ftp://api.example.com"));
        assertNull(Validators.normalizeServerUrl("api.example.com"));
        assertNull(Validators.normalizeServerUrl(""));
        assertNull(Validators.normalizeServerUrl(null));
        assertNull(Validators.normalizeServerUrl("https://user:pw@api.example.com"));
        assertNull(Validators.normalizeServerUrl("https://api.example.com/?x=1"));
    }
}
