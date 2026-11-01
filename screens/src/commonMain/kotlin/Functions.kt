@PatchFunction("checkout.total.v1")
fun checkoutTotal(quantity: Int, price: Int): Int {
    require(quantity in 0..100 && price in 0..1000000)
    return quantity * price
}
@PatchFunction("checkout.shipping.v1")
fun checkoutShipping(base: Int, extra: Int): Int = base + extra
@PatchFunction("checkout.greeting.v1")
fun checkoutGreeting(name: String, vip: Boolean): String = (if (vip) "Welcome VIP " else "Welcome ") + name
@PatchFunction("checkout.status.v1")
suspend fun checkoutStatus(confirmed: Boolean): String = if (confirmed) "Confirmed" else "Pending"
@PatchFunction("checkout.note.v1")
fun checkoutNote(note: String?): String? = note?.trim()
@PatchFunction("simple.points.v1")
fun rewardPoints(quantity: Int): Int = quantity * 20
internal fun productPrice(): Int = 9
