package local.mylife.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelsTest {

    @Test
    fun testProductValidation_ValidProduct() {
        val product = Product(
            id = "prod-1",
            categoryId = "cat-1",
            title = "Test Product",
            rating = 8,
            priceMinor = 150000L,
            currency = "RUB",
            purchaseUrl = "https://example.com/product",
        )
        assertEquals("Test Product", product.title)
        assertEquals(8, product.rating)
        assertEquals(150000L, product.priceMinor)
    }

    @Test
    fun testProductValidation_InvalidRatingRejected() {
        try {
            Product(id = "p1", categoryId = "c1", title = "P", rating = 0)
            fail("Rating 0 should be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }

        try {
            Product(id = "p2", categoryId = "c1", title = "P", rating = 11)
            fail("Rating 11 should be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }
    }

    @Test
    fun testProductValidation_NegativePriceOrMissingCurrencyRejected() {
        try {
            Product(id = "p1", categoryId = "c1", title = "P", priceMinor = -50L, currency = "RUB")
            fail("Negative price must be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }

        try {
            Product(id = "p2", categoryId = "c1", title = "P", priceMinor = 100L, currency = null)
            fail("Price without currency must be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }
    }

    @Test
    fun testProductValidation_InvalidUrlRejected() {
        try {
            Product(id = "p1", categoryId = "c1", title = "P", purchaseUrl = "javascript:alert(1)")
            fail("Javascript scheme must be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }

        try {
            Product(id = "p2", categoryId = "c1", title = "P", purchaseUrl = "file:///etc/passwd")
            fail("File scheme must be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }
    }

    @Test
    fun testPlaceValidation_ValidAndInvalidRatings() {
        val place = Place(id = "pl-1", categoryId = "c1", name = "Coffee Shop", rating = 10)
        assertEquals(10, place.rating)

        try {
            Place(id = "pl-2", categoryId = "c1", name = "Bar", rating = 15)
            fail("Rating 15 must be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }
    }

    @Test
    fun testTitleLengthLimits() {
        val longTitle = "A".repeat(161)
        try {
            TaskItem(id = "t1", title = longTitle)
            fail("Title > 160 chars must be rejected")
        } catch (_: IllegalArgumentException) {
            // PASS
        }
    }
}
