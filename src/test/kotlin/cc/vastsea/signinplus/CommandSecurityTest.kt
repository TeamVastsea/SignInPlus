package cc.vastsea.signinplus

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandSecurityTest {
    @Test
    fun `force make-up requires administrator permission`() {
        assertFalse(isMakeUpForceAllowed(forceRequested = true, isAdmin = false))
        assertTrue(isMakeUpForceAllowed(forceRequested = true, isAdmin = true))
        assertTrue(isMakeUpForceAllowed(forceRequested = false, isAdmin = false))
    }
}
