package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/**
 * Faithful port of script.js's awardCoins()/_getWallet() — same
 * `rewardSettings.xpPerCoin`/`roundingPolicy` read, same
 * `coinWallet.{balance,totalEarned,totalSpent,transactions[]}` shape and
 * per-transaction fields, so a coin earned natively shows up identically
 * in the (not-yet-ported) WebView Rewards page, and vice versa. Shared by
 * both TaskRepository and HabitRepository, matching how awardCoins() is a
 * single shared function called from both completeTask() and
 * completeHabit() in script.js.
 */
object CoinWallet {
    private fun orgData(orgId: String) =
        FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL).getReference("organizations/$orgId/data")

    suspend fun award(orgId: String, uid: String, xpEarned: Int, sourceType: String, sourceTitle: String, sourceId: String) {
        if (xpEarned <= 0) return
        val settingsSnap = orgData(orgId).child("rewardSettings").get().await()
        val rate = (settingsSnap.child("xpPerCoin").value as? Number)?.toInt()?.takeIf { it > 0 } ?: 10
        val policy = settingsSnap.child("roundingPolicy").value as? String ?: "floor"
        val raw = xpEarned.toDouble() / rate
        val coins = when (policy) {
            "ceil" -> ceil(raw).toInt()
            "round" -> Math.round(raw).toInt()
            else -> floor(raw).toInt()
        }
        if (coins <= 0) return

        val walletRef = orgData(orgId).child("submissions/$uid/coinWallet")
        val snap = walletRef.get().await()
        val balance = (snap.child("balance").value as? Number)?.toInt() ?: 0
        val totalEarned = (snap.child("totalEarned").value as? Number)?.toInt() ?: 0
        val totalSpent = (snap.child("totalSpent").value as? Number)?.toInt() ?: 0
        val transactions = anyToMapList(snap.child("transactions").value)

        val newBalance = balance + coins
        val txn = mapOf(
            "id" to "txn-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}",
            "type" to "earned",
            "source" to sourceType,
            "sourceId" to sourceId,
            "sourceTitle" to sourceTitle,
            "xpEarned" to xpEarned,
            "conversionRate" to rate,
            "coinsChange" to coins,
            "balanceAfter" to newBalance,
            "timestamp" to System.currentTimeMillis()
        )
        // unshift semantics — newest transaction first, matching w.transactions.unshift(...) in script.js.
        walletRef.setValue(
            mapOf(
                "balance" to newBalance,
                "totalEarned" to totalEarned + coins,
                "totalSpent" to totalSpent,
                "transactions" to (listOf(txn) + transactions)
            )
        ).await()
    }

    /**
     * Mirror of [award] for the spend side — faithful port of rwRedeemNow()'s
     * wallet debit (script.js:17007-17011). Returns the new balance, or null
     * if the wallet doesn't have enough coins (checked fresh against the
     * current balance, not a caller-supplied one, since another completion
     * could have changed it since the caller last read it).
     */
    suspend fun spend(orgId: String, uid: String, coins: Int, sourceType: String, sourceTitle: String, sourceId: String): Int? {
        if (coins <= 0) return null
        val walletRef = orgData(orgId).child("submissions/$uid/coinWallet")
        val snap = walletRef.get().await()
        val balance = (snap.child("balance").value as? Number)?.toInt() ?: 0
        if (balance < coins) return null
        val totalEarned = (snap.child("totalEarned").value as? Number)?.toInt() ?: 0
        val totalSpent = (snap.child("totalSpent").value as? Number)?.toInt() ?: 0
        val transactions = anyToMapList(snap.child("transactions").value)

        val newBalance = balance - coins
        val txn = mapOf(
            "id" to "txn-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}",
            "type" to "spent",
            "source" to sourceType,
            "sourceId" to sourceId,
            "sourceTitle" to sourceTitle,
            "xpEarned" to 0,
            "conversionRate" to 0,
            "coinsChange" to -coins,
            "balanceAfter" to newBalance,
            "timestamp" to System.currentTimeMillis()
        )
        walletRef.setValue(
            mapOf(
                "balance" to newBalance,
                "totalEarned" to totalEarned,
                "totalSpent" to totalSpent + coins,
                "transactions" to (listOf(txn) + transactions)
            )
        ).await()
        return newBalance
    }
}
