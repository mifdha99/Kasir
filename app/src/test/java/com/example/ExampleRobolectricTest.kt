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
    fun `read app_name from context and verify atomic checkout and refund`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("KasirKu", appName)

        val db = Room.inMemoryDatabaseBuilder(context, KasirDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.kasirDao()
        val repo = KasirRepository(dao)

        dao.saveStoreSettings(StoreSettingsEntity(id = 1, storeName = "Toko KasirKu"))
        val catId = repo.saveCategory(CategoryEntity(name = "Minuman")).getOrThrow()
        val prodId = repo.saveProduct(
            ProductEntity(
                name = "Es Kopi Susu",
                sku = "KP-01",
                barcode = "899000111222",
                buyPrice = 8000.0,
                sellPrice = 15000.0,
                stock = 20,
                minStock = 5,
                unit = "Cup",
                categoryId = catId,
                categoryName = "Minuman"
            ),
            userName = "Admin"
        ).getOrThrow()

        val savedProduct = dao.getProductById(prodId)!!
        assertEquals(20, savedProduct.stock)

        // Execute checkout of 3 cups paid with 50,000 cash
        val cashier = CashierUserEntity(id = 1, name = "Kasir Test")
        val checkoutResult = repo.processCheckout(
            cartItems = listOf(CartItem(product = savedProduct, quantity = 3, note = "Less sugar")),
            cashier = cashier,
            customer = null,
            discountAmount = 5000.0,
            taxPercentage = 0.0,
            serviceFee = 0.0,
            paymentMethod = "Tunai",
            amountPaid = 50000.0,
            notes = "Meja 1"
        ).getOrThrow()

        assertEquals(40000.0, checkoutResult.transaction.totalAmount, 0.01)
        assertEquals(10000.0, checkoutResult.transaction.changeAmount, 0.01)

        // Verify stock decreased from 20 to 17
        val afterSaleProduct = dao.getProductById(prodId)!!
        assertEquals(17, afterSaleProduct.stock)

        // Verify receipt formatting contains key information
        val receipt = BluetoothPrinterService.formatReceiptText(
            txWithItems = checkoutResult,
            settings = StoreSettingsEntity(storeName = "Toko KasirKu", paperSizeMm = 58)
        )
        assertTrue(receipt.contains("TOKO KASIRKU"))
        assertTrue(receipt.contains("Es Kopi Susu"))

        // Cancel/Refund transaction and verify stock returns to 20
        repo.cancelTransaction(
            transactionId = checkoutResult.transaction.id,
            cancelReason = "Salah pesanan",
            cancelledBy = "Admin"
        ).getOrThrow()

        val afterRefundProduct = dao.getProductById(prodId)!!
        assertEquals(20, afterRefundProduct.stock)

        db.close()
    }
}
