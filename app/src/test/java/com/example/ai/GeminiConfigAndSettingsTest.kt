package com.example.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ApiKeyManager
import com.example.data.ApiKeyStatus
import com.example.data.AppDatabase
import com.example.data.entity.BudgetEntity
import com.example.ui.components.IndianCurrencyFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiConfigAndSettingsTest {

    private lateinit var context: Context
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        apiKeyManager = ApiKeyManager(context)
        GeminiConfig.clearCache()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
        GeminiConfig.clearCache()
    }

    @Test
    fun `test default model is gemini-3-8-flash`() {
        assertEquals("gemini-3.8-flash", GeminiConfig.DEFAULT_MODEL)
        assertEquals("gemini-3.8-flash", GeminiConfig.getModel(context))
        assertEquals("gemini-3.8-flash", apiKeyManager.getSelectedModel())
    }

    @Test
    fun `test central model configuration and switching`() {
        GeminiConfig.setModel(context, "gemini-1.5-pro")
        assertEquals("gemini-1.5-pro", GeminiConfig.getModel(context))
        assertEquals("gemini-1.5-pro", apiKeyManager.getSelectedModel())

        // Switch back to gemini-3.8-flash
        GeminiConfig.setModel(context, "gemini-3.8-flash")
        assertEquals("gemini-3.8-flash", GeminiConfig.getModel(context))
    }

    @Test
    fun `test generate content url format`() {
        val url = GeminiConfig.getGenerateContentUrl("test_api_key_123", "gemini-3.8-flash")
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=test_api_key_123",
            url
        )
    }

    @Test
    fun `test blank api key returns bad key status without fake success`() = runBlocking {
        val status = apiKeyManager.testApiKey("   ")
        assertTrue("Blank key must return Invalid.BadKey", status is ApiKeyStatus.Invalid.BadKey)
        assertFalse("Must never display fake success", status is ApiKeyStatus.Valid)
    }

    @Test
    fun `test invalid api key format error message categorization`() = runBlocking {
        val status = apiKeyManager.testApiKey("invalid_fake_key_12345")
        // Will fail to authenticate with Google Gemini
        assertTrue("Must return Invalid status", status is ApiKeyStatus.Invalid)
        assertFalse("Must not return Valid for fake key", status is ApiKeyStatus.Valid)
    }

    @Test
    fun `test Indian currency formatter with lakhs and crores`() {
        // Zero
        assertEquals("₹0.00", IndianCurrencyFormatter.format(0.0))

        // Hundreds and thousands
        assertEquals("₹450.50", IndianCurrencyFormatter.format(450.5))
        assertEquals("₹1,234.56", IndianCurrencyFormatter.format(1234.56))

        // Lakhs (Indian grouping 1,23,456.78)
        assertEquals("₹1,23,456.78", IndianCurrencyFormatter.format(123456.78))
        assertEquals("₹50,000.00", IndianCurrencyFormatter.format(50000.0))
        assertEquals("₹10,00,000.00", IndianCurrencyFormatter.format(1000000.0))

        // Crores (Indian grouping 1,23,45,678.90)
        assertEquals("₹1,23,45,678.90", IndianCurrencyFormatter.format(12345678.90))

        // Negative numbers
        assertEquals("-₹500.00", IndianCurrencyFormatter.format(-500.0))
        assertEquals("-₹1,50,000.00", IndianCurrencyFormatter.format(-150000.0))
    }

    @Test
    fun `test user profile name and monthly spending budget persistence`() = runBlocking {
        // Save initial budget
        val initialBudget = BudgetEntity(
            id = 1,
            userId = 1L,
            monthlyLimit = 50000.0,
            currencySymbol = "₹",
            userName = "Kamlesh"
        )
        db.budgetDao().saveBudget(initialBudget)

        val retrieved = db.budgetDao().getBudget()
        assertEquals("Kamlesh", retrieved?.userName)
        assertEquals(50000.0, retrieved?.monthlyLimit ?: 0.0, 0.001)
        assertEquals("₹", retrieved?.currencySymbol)

        // User edits profile name and monthly budget
        val updated = retrieved!!.copy(
            userName = "Kamlesh Thakur",
            monthlyLimit = 75000.0
        )
        db.budgetDao().saveBudget(updated)

        val afterUpdate = db.budgetDao().getBudget()
        assertEquals("Kamlesh Thakur", afterUpdate?.userName)
        assertEquals(75000.0, afterUpdate?.monthlyLimit ?: 0.0, 0.001)
    }
}
