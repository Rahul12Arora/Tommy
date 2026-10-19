package com.companionstudio.nyx.home

import kotlinx.coroutines.flow.Flow

interface SummaryRepository {
    fun observeToday(): Flow<TodaySummary>
}
