package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CartItem
import com.example.data.CategoryEntity
import com.example.data.KasirDatabase
import com.example.data.KasirRepository
import com.example.data.ProductEntity
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionEntity
import com.example.data.TransactionItemEntity
import com.example.service.BluetoothPrinterService
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    private lateinit var database: KasirDatabase
    private lateinit var repository: KasirRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KasirDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KasirRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun verifySequentialBillingNumbersAndPaymentMethodsAndKitchenReceipt() = runBlocking {
        repository.ensureInitialData()

        // 1. Verify default payment methods: TUNAI, TRANSFER, QRIS, GOJEK, SHOPEE, GRAB
        val initialMethods = repository.getAllPaymentMethodsOnce()
        assertEquals(
            listOf("TUNAI", "TRANSFER", "QRIS", "GOJEK", "SHOPEE", "GRAB"),
            initialMethods.map { it.name }
        )

        val categories = repository.allCategories.first()
        val makananCat = categories.first()

        val menuId = repository.saveProduct(
            ProductEntity(
                name = "Mie Goreng Spesial",
                sellPrice = 15000.0,
                unit = "Porsi",
                categoryId = makananCat.id,
                categoryName = makananCat.name
            )
        )
        val menu = repository.allProducts.first().first { it.id == menuId }

        // 2. Verify initial next billing number for today starts at Billing 1
        assertEquals(1, repository.getNextBillingNumberForToday())

        // Create Billing 1 with 2 portions and per-portion notes
        val cartItem = CartItem(
            product = menu,
            quantity = 2,
            portionNotes = listOf("tidak pedas, tanpa sayur", "pedas sedang")
        )
        val tx1 = TransactionEntity(
            invoiceNumber = "BIL-TEST-001",
            billingNumber = 1,
            subtotal = cartItem.subtotal,
            totalAmount = cartItem.subtotal,
            paymentMethod = "BELUM BAYAR",
            amountPaid = 0.0,
            changeAmount = 0.0,
            status = "UNPAID"
        )
        val item1 = TransactionItemEntity(
            transactionId = 0,
            productId = menu.id,
            productName = menu.name,
            unit = menu.unit,
            sellPrice = menu.sellPrice,
            quantity = cartItem.quantity,
            itemNote = cartItem.encodedItemNote,
            subtotal = cartItem.subtotal
        )

        val sentOrder1 = repository.createKitchenOrderBilling(tx1, listOf(item1))
        assertEquals("UNPAID", sentOrder1.transaction.status)
        assertEquals(1, sentOrder1.transaction.billingNumber)
        assertEquals("Billing 1", sentOrder1.transaction.billingDisplay)

        // Verify kitchen ticket contains Billing 1, menu, and per-portion notes, without payment/change
        val kitchenTicket = BluetoothPrinterService.formatKitchenTicketText(sentOrder1, 58)
        assertTrue(kitchenTicket.contains("BILLING 1"))
        assertTrue(kitchenTicket.contains("Mie Goreng Spesial x2"))
        assertTrue(kitchenTicket.contains("Porsi 1: tidak pedas, tanpa sayur"))
        assertTrue(kitchenTicket.contains("Porsi 2: pedas sedang"))
        assertFalse(kitchenTicket.contains("KEMBALI"))

        // 3. Pay Billing 1 using QRIS
        val qrisMethod = initialMethods.first { it.name == "QRIS" }
        val paidOrder1 = repository.completeBillingPayment(
            sentOrder1.transaction.copy(
                paymentMethodId = qrisMethod.id,
                paymentMethod = qrisMethod.name,
                amountPaid = 30000.0,
                changeAmount = 0.0,
                status = "PAID"
            )
        )
        assertEquals("PAID", paidOrder1.transaction.status)

        // 4. CRITICAL: Even though Billing 1 is now PAID (and unpaidBillings is empty),
        // the next billing number on the same day MUST be Billing 2 (NEVER reset to Billing 1!)
        assertEquals(0, repository.unpaidTransactionsWithItems.first().size)
        assertEquals(2, repository.getNextBillingNumberForToday())

        // Even if caller passes billingNumber = 1 by mistake, createKitchenOrderBilling enforces Billing 2!
        val sentOrder2 = repository.createKitchenOrderBilling(
            tx1.copy(billingNumber = 1),
            listOf(item1)
        )
        assertEquals(2, sentOrder2.transaction.billingNumber)
        assertEquals("Billing 2", sentOrder2.transaction.billingDisplay)
        assertEquals(3, repository.getNextBillingNumberForToday())

        // 5. Rename QRIS -> QRIS BCA, add DANA, delete GRAB, and verify old transaction (Billing 1) still has "QRIS" intact
        repository.updatePaymentMethod(qrisMethod, "QRIS BCA", false)
        repository.addPaymentMethod("DANA", false)
        val grabMethod = initialMethods.first { it.name == "GRAB" }
        repository.deletePaymentMethod(grabMethod)

        val updatedMethods = repository.getAllPaymentMethodsOnce().map { it.name }
        assertTrue(updatedMethods.contains("QRIS BCA"))
        assertTrue(updatedMethods.contains("DANA"))
        assertFalse(updatedMethods.contains("GRAB"))

        val allHistory = repository.allTransactionsWithItems.first()
        val historicalTx1 = allHistory.first { it.transaction.id == paidOrder1.transaction.id }
        assertEquals("QRIS", historicalTx1.transaction.paymentMethod)

        // 6. Pay Billing 2 with TUNAI and verify payment receipt
        val paidOrder2 = repository.completeBillingPayment(
            sentOrder2.transaction.copy(
                paymentMethod = "TUNAI",
                amountPaid = 50000.0,
                changeAmount = 20000.0,
                status = "PAID"
            )
        )
        val receipt = BluetoothPrinterService.formatReceiptText(paidOrder2, StoreSettingsEntity())
        assertTrue(receipt.contains("BILLING 2"))
        assertTrue(receipt.contains("TUNAI"))
        assertTrue(receipt.contains("KEMBALI"))
    }
}
