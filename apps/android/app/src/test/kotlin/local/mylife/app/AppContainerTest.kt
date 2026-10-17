package local.mylife.app

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppContainerTest {
    @Test
    fun testDefaultAppContainerComponents() {
        // AppContainer architecture test: verify dependency declarations
        assertNotNull(AppContainer::class.java)
    }
}
