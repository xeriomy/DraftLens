package com.draftlens.permissions

import org.junit.Assert.*
import org.junit.Test

class ProjectionPermissionTest {

    @Test fun `granted result is accepted`() {
        assertTrue(ProjectionPermission.isResultOk(-1)) // Activity.RESULT_OK
    }

    @Test fun `denied result is rejected`() {
        assertFalse(ProjectionPermission.isResultOk(0)) // RESULT_CANCELED
        assertFalse(ProjectionPermission.isResultOk(1))
    }
}
