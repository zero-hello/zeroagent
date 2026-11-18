package me.rerere.zeroagent.data.root

import org.junit.Assert.*
import org.junit.Test

class RootCommandProtocolTest {
    @Test fun `a nonzero command can still have a valid root grant`() {
        val result = RootResult(1, "__ZEROAGENT_ROOT_VERIFIED__\nfile not found\n")
        assertTrue(RootCommandProtocol.isVerified(result.output))
        assertEquals("file not found\n", RootCommandProtocol.cleanOutput(result.output))
    }
    @Test fun `denial output is not accepted as a verified grant`() {
        assertFalse(RootCommandProtocol.isVerified("su: Permission denied"))
        assertFalse(RootCommandProtocol.isVerified("error: __ZEROAGENT_ROOT_VERIFIED__"))
        assertTrue(RootCommandProtocol.isVerified("su notice\r\n__ZEROAGENT_ROOT_VERIFIED__\r\n"))
    }
}
