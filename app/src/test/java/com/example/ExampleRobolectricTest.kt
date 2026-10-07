package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.CartItem
import com.example.data.CashierUserEntity
import com.example.data.CategoryEntity
import com.example.data.KasirDatabase
import com.example.data.KasirRepository
import com.example.data.ProductEntity
import com.example.data.StoreSettingsEntity
import com.example.service.BluetoothPrinterService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `verify restaurant order to kitchen and separate billing payment flow`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("KasirKu", appName)

        val db = Room.inMemoryDatabaseBuilder(context, KasirDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.kasirDao()
        val repo = KasirRepository(dao)

        dao.saveStoreSettings(StoreSettingsEntity(id = 1, storeName = "Resto KasirKu"))
        val foodCatId = repo.saveCategory(CategoryEntity(name = "Makanan")).getOrThrow()
        val drinkCatId = repo.saveCategory(CategoryEntity(name = "Minuman")).getOrThrow()

        val mieGorengId = repo.saveProduct(
            ProductEntity(
                name = "Mie Goreng",
                buyPrice = 8000.0,
                sellPrice = 15000.0,
                unit = "Porsi",
                categoryId = foodCatId,
                categoryName = "Makanan"
            )
        ).getOrThrow()

        val esTehId = repo.saveProduct(
            ProductEntity(
                name = "Es Teh Manis",
                buyPrice = 2000.0,
                sellPrice = 5000.0,
                unit = "Gelas",
                categoryId = drinkCatId,
                categoryName = "Minuman"
            )
        ).getOrThrow()

        val mieGoreng = dao.getProductById(mieGorengId)!!
        val esTeh = dao.getProductById(esTehId)!!

        // 1. PROSES PESAN: Send Order to Kitchen (Status = UNPAID / BELUM BAYAR)
        val cashier = CashierUserEntity(id = 1, name = "Kasir Resto")
        val sentOrder = repo.sendOrderToKitchen(
            existingTransactionId = null,
            billingNumber = 1,
            cartItems = listOf(
                CartItem(
                    product = mieGoreng,
                    quantity = 2,
                    portionNotes = listOf("tidak pedas, tanpa sayur", "pedas sedang")
                ),
                CartItem(
                    product = esTeh,
                    quantity = 2,
                    portionNotes = listOf("es sedikit", "normal")
                )
            ),
            cashier = cashier,
            customer = null,
            notes = "Meja 4"
        ).getOrThrow()

        assertEquals("UNPAID", sentOrder.transaction.status)
        assertEquals("Billing 1", sentOrder.transaction.billingDisplay)
        assertEquals(40000.0, sentOrder.transaction.totalAmount, 0.01)

        // Verify kitchen ticket contains per-portion notes
        val kitchenTicket = BluetoothPrinterService.formatKitchenTicketText(sentOrder, 58)
        assertTrue(kitchenTicket.contains("BILLING 1"))
        assertTrue(kitchenTicket.contains("Porsi 1: tidak pedas, tanpa sayur"))
        assertTrue(kitchenTicket.contains("Porsi 2: pedas sedang"))

        // 2. PROSES BAYAR: Complete payment for Billing 1 with Rp50.000 cash
        val paidOrder = repo.processBillingPayment(
            transactionId = sentOrder.transaction.id,
            cashier = cashier,
            discountAmount = 0.0,
            serviceFee = 0.0,
            paymentMethod = "Tunai",
            amountPaid = 50000.0
        ).getOrThrow()

        assertEquals("PAID", paidOrder.transaction.status)
        assertEquals(40000.0, paidOrder.transaction.totalAmount, 0.01)
        assertEquals(10000.0, paidOrder.transaction.changeAmount, 0.01)

        // Verify receipt formatting contains key information
        val receipt = BluetoothPrinterService.formatReceiptText(
            txWithItems = paidOrder,
            settings = StoreSettingsEntity(storeName = "Resto KasirKu", paperSizeMm = 58)
        )
        assertTrue(receipt.contains("RESTO KASIRKU"))
        assertTrue(receipt.contains("Mie Goreng"))
        assertTrue(receipt.contains("KEMBALI"))

        db.close()
    }
}
