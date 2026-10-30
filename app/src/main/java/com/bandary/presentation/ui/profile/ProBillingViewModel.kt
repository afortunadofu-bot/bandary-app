package com.bandary.presentation.ui.profile

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.bandary.data.billing.BillingManager
import com.bandary.data.billing.ProPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Pro 頁的付款狀態。BillingManager 是 @Singleton（跨畫面共用同一條 Play 連線），
 * 這裡只做轉發，不另外持有狀態。
 */
@HiltViewModel
class ProBillingViewModel @Inject constructor(
    private val billing: BillingManager
) : ViewModel() {

    val availability = billing.availability
    val offers = billing.offers
    val owned = billing.owned
    val message = billing.message
    val busy = billing.busy

    init {
        // 進頁即連線並查價／查已購買（已連線時只重查一次）。
        billing.start()
    }

    fun buy(activity: Activity, plan: ProPlan) = billing.purchase(activity, plan)
    fun restore() = billing.restorePurchases()
    fun clearMessage() = billing.clearMessage()
    fun manageSubscriptionUrl(plan: ProPlan = ProPlan.YEARLY): String = billing.manageSubscriptionUrl(plan)
}
