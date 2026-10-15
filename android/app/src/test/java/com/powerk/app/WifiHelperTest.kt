package com.powerk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the credential checks the wizard runs before it spends the strip's brief setup window:
 * a passphrase the WPA2 supplicant cannot accept must be caught here, not by a strip that accepts
 * the setup and then never joins the network.
 */
class WifiHelperTest {

    @Test
    fun acceptsWpa2Passphrases() {
        assertNull(WifiHelper.homeWifiValidationError("Home", "supersecret"))
        assertNull(WifiHelper.homeWifiValidationError("Home", "12345678"))
        assertNull(WifiHelper.homeWifiValidationError("Home", "x".repeat(63)))
    }

    @Test
    fun acceptsA64DigitHexPskEvenThoughItIsLongerThan63Characters() {
        assertNull(WifiHelper.homeWifiValidationError("Home", "0123456789abcdef".repeat(4)))
    }

    @Test
    fun rejectsPassphrasesOutsideThe8To63CharacterRange() {
        assertNotNull(WifiHelper.homeWifiValidationError("Home", "short"))
        assertNotNull(WifiHelper.homeWifiValidationError("Home", "x".repeat(64)))
    }

    @Test
    fun rejectsMissingNameAndPassword() {
        assertNotNull(WifiHelper.homeWifiValidationError("", "supersecret"))
        assertNotNull(WifiHelper.homeWifiValidationError("   ", "supersecret"))
        assertNotNull(WifiHelper.homeWifiValidationError("Home", ""))
    }

    @Test
    fun rejectsPassphrasesThatAreNotPrintableAscii() {
        assertNotNull(WifiHelper.homeWifiValidationError("Home", "pass\tword"))
        assertNotNull(WifiHelper.homeWifiValidationError("Home", "passwörd"))
    }

    @Test
    fun derivesTheStripApPasswordTheSameWayTheFirmwareDoes() {
        assertEquals("LGU_ABC1234", stripApPassword("TONLY_TAP_ABC1234"))
        assertEquals("", stripApPassword("MTTL-W01-SETUP"))
    }
}
