package com.triangle.app.data

import com.google.firebase.database.FirebaseDatabase
import com.triangle.app.data.models.Redemption
import com.triangle.app.data.models.Reward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Rewards data layer for the native Rewards screen (Milestone 4).
 *
 * `rewardSettings` and `submissions/{uid}/coinWallet` follow this project's
 * usual narrow-sub-path-write rule (see TaskRepository's own header comment)
 * — but `rewards` and `redemptions` deliberately do NOT: both are read
 * throughout the still-live WebView as plain JS arrays (`db.rewards.filter(
 * ...)`, `.map(...)`, `.find(...)`). script.js's own realtime-sync
 * normalizer, `_thNormalizeArrayLike()` (script.js:2358), only reconstructs
 * an array from a Firebase snapshot when EVERY key is a numeric string (an
 * artifact of how Firebase itself stores sparse/dense arrays) — it does NOT
 * convert an arbitrary keyed map (e.g. Firebase push-IDs) into an array.
 * Writing either path as a keyed map would silently break the WebView the
 * next time it reads that path (`TypeError: db.rewards.filter is not a
 * function`). So `saveReward`/`deleteReward`/`redeem`/`updateRedemptionStatus`
 * read the full array, mutate it, and write it back — exactly what the
 * WebView's own `fbPut('rewards', db.rewards)`/`fbPut('redemptions', ...)`
 * already do — scoped only to those two sub-paths, never a full
 * `organizations/{orgId}/data` blob write. This is a narrow, disclosed
 * exception to the project's write-pattern rule, not a violation of its
 * spirit.
 */
object RewardRepository {
    private fun orgData(orgId: String) =
        FirebaseDatabase.getInstance(TriangleConfig.FIREBASE_URL).getReference("organizations/$orgId/data")

    data class RewardSettings(val xpPerCoin: Int = 10, val roundingPolicy: String = "floor", val setupDone: Boolean = false)

    sealed class RedeemResult {
        data class Success(val redemption: Redemption) : RedeemResult()
        object NotEnoughCoins : RedeemResult()
        object OutOfStock : RedeemResult()
        object Failed : RedeemResult()
    }

    fun rewardsFlow(orgId: String): Flow<List<Reward>> =
        orgData(orgId).child("rewards").valueFlow().map { snap ->
            anyToMapList(snap.value).mapNotNull { Reward.fromMap(it) }.filter { it.active }
        }

    fun redemptionsFlow(orgId: String, uid: String): Flow<List<Redemption>> =
        orgData(orgId).child("redemptions").valueFlow().map { snap ->
            anyToMapList(snap.value).mapNotNull { Redemption.fromMap(it) }
                .filter { it.internId == uid }
                .sortedByDescending { it.createdAt }
        }

    fun rewardSettingsFlow(orgId: String): Flow<RewardSettings> =
        orgData(orgId).child("rewardSettings").valueFlow().map { snap ->
            RewardSettings(
                xpPerCoin = (snap.child("xpPerCoin").value as? Number)?.toInt()?.takeIf { it > 0 } ?: 10,
                roundingPolicy = snap.child("roundingPolicy").value as? String ?: "floor",
                setupDone = snap.child("setupDone").value == true
            )
        }

    data class WalletSnapshot(val balance: Int, val totalEarned: Int, val totalSpent: Int, val transactions: List<Map<String, Any?>>)

    fun walletFlow(orgId: String, uid: String): Flow<WalletSnapshot> =
        orgData(orgId).child("submissions/$uid/coinWallet").valueFlow().map { snap ->
            WalletSnapshot(
                balance = (snap.child("balance").value as? Number)?.toInt() ?: 0,
                totalEarned = (snap.child("totalEarned").value as? Number)?.toInt() ?: 0,
                totalSpent = (snap.child("totalSpent").value as? Number)?.toInt() ?: 0,
                transactions = anyToMapList(snap.child("transactions").value)
            )
        }

    private suspend fun readRewards(orgId: String): List<Reward> =
        anyToMapList(orgData(orgId).child("rewards").get().await().value).mapNotNull { Reward.fromMap(it) }

    private suspend fun readRedemptions(orgId: String): List<Redemption> =
        anyToMapList(orgData(orgId).child("redemptions").get().await().value).mapNotNull { Redemption.fromMap(it) }

    suspend fun saveReward(orgId: String, reward: Reward) {
        val current = readRewards(orgId)
        val idx = current.indexOfFirst { it.id == reward.id }
        val updated = if (idx >= 0) current.toMutableList().also { it[idx] = reward } else listOf(reward) + current
        orgData(orgId).child("rewards").setValue(updated.map { it.toMap() }).await()
    }

    suspend fun deleteReward(orgId: String, rewardId: String) {
        val current = readRewards(orgId)
        orgData(orgId).child("rewards").setValue(current.filterNot { it.id == rewardId }.map { it.toMap() }).await()
    }

    suspend fun saveRewardSettings(orgId: String, xpPerCoin: Int, roundingPolicy: String) {
        orgData(orgId).child("rewardSettings").setValue(
            mapOf("xpPerCoin" to xpPerCoin, "roundingPolicy" to roundingPolicy, "setupDone" to true)
        ).await()
    }

    /** Faithful port of rwRedeemNow() (script.js:16997-17033), minus the pending→self-approve step — see [Redemption]'s class doc. */
    suspend fun redeem(orgId: String, uid: String, reward: Reward): RedeemResult {
        val rewards = readRewards(orgId)
        val liveReward = rewards.firstOrNull { it.id == reward.id } ?: reward
        if (liveReward.stock != null && liveReward.stock <= 0) return RedeemResult.OutOfStock

        val newBalance = CoinWallet.spend(orgId, uid, liveReward.coinCost, "redemption", liveReward.name, liveReward.id)
            ?: return RedeemResult.NotEnoughCoins

        return try {
            val now = System.currentTimeMillis()
            val redemption = Redemption(
                id = "rdm-$now",
                rewardId = liveReward.id,
                internId = uid,
                coinsCost = liveReward.coinCost,
                status = "approved",
                createdAt = now,
                updatedAt = now
            )
            val redemptions = readRedemptions(orgId)
            orgData(orgId).child("redemptions").setValue((listOf(redemption) + redemptions).map { it.toMap() }).await()

            if (liveReward.stock != null) {
                val updatedRewards = rewards.map { if (it.id == liveReward.id) it.copy(stock = liveReward.stock - 1) else it }
                orgData(orgId).child("rewards").setValue(updatedRewards.map { it.toMap() }).await()
            }
            RedeemResult.Success(redemption)
        } catch (e: Exception) {
            // The coin debit already landed — unlike script.js's in-memory rollback
            // (which only ever undoes local state before its Promise.all fires),
            // there's nothing to roll back here since CoinWallet.spend() already
            // committed. Surface the failure so the UI can retry the redemption
            // record write; the wallet debit standing alone is the same partial-
            // failure risk the source's own non-atomic Promise.all already has.
            RedeemResult.Failed
        }
    }

    suspend fun updateRedemptionStatus(orgId: String, redemptionId: String, status: String) {
        val current = readRedemptions(orgId)
        val updated = current.map { if (it.id == redemptionId) it.copy(status = status, updatedAt = System.currentTimeMillis()) else it }
        orgData(orgId).child("redemptions").setValue(updated.map { it.toMap() }).await()
    }
}
