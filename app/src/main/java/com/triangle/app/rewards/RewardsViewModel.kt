package com.triangle.app.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triangle.app.data.RewardRepository
import com.triangle.app.data.SessionStore
import com.triangle.app.data.models.Redemption
import com.triangle.app.data.models.Reward
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class RewardsUiState(
    val isLoading: Boolean = true,
    val rewards: List<Reward> = emptyList(),
    val redemptions: List<Redemption> = emptyList(),
    val balance: Int = 0,
    val totalEarned: Int = 0,
    val totalSpent: Int = 0,
    val transactions: List<Map<String, Any?>> = emptyList(),
    val xpPerCoin: Int = 10,
    val roundingPolicy: String = "floor"
)

sealed class RedeemUiEvent {
    data class Success(val rewardName: String) : RedeemUiEvent()
    object NotEnoughCoins : RedeemUiEvent()
    object OutOfStock : RedeemUiEvent()
    object Failed : RedeemUiEvent()
}

/**
 * Shared ViewModel for the whole Rewards nested nav graph (mirrors
 * TasksHabitsViewModel — scoped to the graph's NavBackStackEntry so
 * navigating between Store/Detail/Wallet/History/Manage/Settings keeps
 * seeing the same live data instead of each screen re-subscribing).
 */
class RewardsViewModel(private val session: SessionStore.Session) : ViewModel() {

    private val _uiState = MutableStateFlow(RewardsUiState())
    val uiState: StateFlow<RewardsUiState> = _uiState.asStateFlow()

    private val _redeemEvent = MutableStateFlow<RedeemUiEvent?>(null)
    val redeemEvent: StateFlow<RedeemUiEvent?> = _redeemEvent.asStateFlow()

    init {
        combine(
            RewardRepository.rewardsFlow(session.orgId),
            RewardRepository.redemptionsFlow(session.orgId, session.uid),
            RewardRepository.rewardSettingsFlow(session.orgId),
            RewardRepository.walletFlow(session.orgId, session.uid)
        ) { rewards, redemptions, settings, wallet ->
            RewardsUiState(
                isLoading = false,
                rewards = rewards,
                redemptions = redemptions,
                balance = wallet.balance,
                totalEarned = wallet.totalEarned,
                totalSpent = wallet.totalSpent,
                transactions = wallet.transactions,
                xpPerCoin = settings.xpPerCoin,
                roundingPolicy = settings.roundingPolicy
            )
        }.onEach { _uiState.value = it }.launchIn(viewModelScope)
    }

    fun rewardById(id: String): Reward? = _uiState.value.rewards.firstOrNull { it.id == id }

    fun redeem(reward: Reward) {
        viewModelScope.launch {
            val result = RewardRepository.redeem(session.orgId, session.uid, reward)
            _redeemEvent.value = when (result) {
                is RewardRepository.RedeemResult.Success -> RedeemUiEvent.Success(reward.name)
                RewardRepository.RedeemResult.NotEnoughCoins -> RedeemUiEvent.NotEnoughCoins
                RewardRepository.RedeemResult.OutOfStock -> RedeemUiEvent.OutOfStock
                RewardRepository.RedeemResult.Failed -> RedeemUiEvent.Failed
            }
        }
    }

    fun consumeRedeemEvent() {
        _redeemEvent.value = null
    }

    fun markDelivered(redemptionId: String) {
        viewModelScope.launch {
            runCatching { RewardRepository.updateRedemptionStatus(session.orgId, redemptionId, "delivered") }
        }
    }

    fun saveReward(reward: Reward, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { RewardRepository.saveReward(session.orgId, reward) }
            onDone()
        }
    }

    fun deleteReward(rewardId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { RewardRepository.deleteReward(session.orgId, rewardId) }
            onDone()
        }
    }

    fun saveSettings(xpPerCoin: Int, roundingPolicy: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { RewardRepository.saveRewardSettings(session.orgId, xpPerCoin, roundingPolicy) }
            onDone()
        }
    }
}
