package com.akira.app.feature.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingPermissionPolicyTest {
    @Test fun `the combined permissions page continues only when both permissions are enabled`() {
        assertEquals(SetupPermission.AUDIO, requiredSetupPermission(false, false))
        assertEquals(SetupPermission.AUDIO, requiredSetupPermission(false, true))
        assertEquals(SetupPermission.NOTIFICATIONS, requiredSetupPermission(true, false))
        assertEquals(null, requiredSetupPermission(true, true))
    }

    @Test fun `notification rejection keeps the user in the request flow`() {
        assertEquals(SetupPrimaryAction.REQUEST_PERMISSION, notificationSetupAction(false, true, true, true))
    }
    @Test fun `permanent notification rejection opens settings instead of advancing`() {
        assertEquals(SetupPrimaryAction.OPEN_SETTINGS, notificationSetupAction(false, true, true, false))
    }
    @Test fun `the first notification request is not mistaken for permanent rejection`() {
        assertEquals(SetupPrimaryAction.REQUEST_PERMISSION, notificationSetupAction(false, true, false, false))
    }
    @Test fun `disabled notifications on old Android require settings`() {
        assertEquals(SetupPrimaryAction.OPEN_SETTINGS, notificationSetupAction(false, false, false, false))
    }
    @Test fun `enabled notifications allow continuation without requesting again`() {
        assertEquals(SetupPrimaryAction.CONTINUE, notificationSetupAction(true, true, true, false))
    }
    @Test fun `cached ready results cannot bypass missing audio permission`() {
        val ready = SetupState(status = SetupStatus.READY)
        assertEquals(SetupPrimaryAction.REQUEST_PERMISSION, librarySetupAction(false, false, ready))
        assertEquals(SetupPrimaryAction.OPEN_SETTINGS, librarySetupAction(false, true, ready))
    }
    @Test fun `audio grant starts a real scan and waits until it is ready`() {
        assertEquals(SetupPrimaryAction.START_SCAN, librarySetupAction(true, false, SetupState()))
        assertEquals(SetupPrimaryAction.WAIT, librarySetupAction(true, false, SetupState(status = SetupStatus.SCANNING)))
        assertEquals(SetupPrimaryAction.CONTINUE, librarySetupAction(true, false, SetupState(status = SetupStatus.READY)))
    }
    @Test fun `scan errors and restored skips never silently continue`() {
        assertEquals(SetupPrimaryAction.START_SCAN, librarySetupAction(true, false, SetupState(failure = SetupFailure.SCAN)))
        assertEquals(SetupPrimaryAction.START_SCAN, librarySetupAction(true, false, SetupState(status = SetupStatus.READY, librarySkipped = true)))
    }
}
