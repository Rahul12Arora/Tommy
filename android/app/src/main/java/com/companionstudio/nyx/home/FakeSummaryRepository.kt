package com.companionstudio.nyx.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Fixed numbers for Home until real tracking lands in M4. */
class FakeSummaryRepository : SummaryRepository {
    override fun observeToday(): Flow<TodaySummary> = flowOf(FAKE_TODAY)

    companion object {
        val FAKE_TODAY = TodaySummary(
            totalSeconds = 3 * 3600 + 12 * 60,
            productiveSeconds = 3600 + 5 * 60,
            doomscrollSeconds = 47 * 60,
            goals = listOf(
                GoalProgress(label = "Social ≤ 60 min", usedSeconds = 47 * 60, targetSeconds = 60 * 60),
                GoalProgress(label = "Games ≤ 30 min", usedSeconds = 35 * 60, targetSeconds = 30 * 60),
            ),
            petFace = "😸",
        )
    }
}
