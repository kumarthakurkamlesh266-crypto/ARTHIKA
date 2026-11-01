package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.FinanceAiEngine
import com.example.data.entity.BudgetEntity
import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ARTHIKA", appName)
    }

    @Test
    fun `verify ai query engine calculates biggest expense category correctly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = FinanceAiEngine(context)

        val transactions = listOf(
            TransactionEntity(
                amount = 250.0,
                type = TransactionType.EXPENSE,
                category = "Food",
                dateMillis = System.currentTimeMillis(),
                timeString = "12:00",
                paymentMethod = "Credit Card",
                merchant = "Whole Foods"
            ),
            TransactionEntity(
                amount = 80.0,
                type = TransactionType.EXPENSE,
                category = "Transport",
                dateMillis = System.currentTimeMillis(),
                timeString = "15:00",
                paymentMethod = "Cash",
                merchant = "Metro"
            ),
            TransactionEntity(
                amount = 3000.0,
                type = TransactionType.INCOME,
                category = "Salary",
                dateMillis = System.currentTimeMillis(),
                timeString = "09:00",
                paymentMethod = "Bank Transfer",
                merchant = "Tech Corp"
            )
        )

        val budget = BudgetEntity(monthlyLimit = 2000.0)
        val response = engine.processQuery("What is my biggest expense category?", transactions, budget)

        assertNotNull(response)
        assertTrue(response.replyText.contains("Food", ignoreCase = true))
    }
}
