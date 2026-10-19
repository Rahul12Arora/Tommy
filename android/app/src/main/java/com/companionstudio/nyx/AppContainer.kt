package com.companionstudio.nyx

import com.companionstudio.nyx.data.DayClock
import com.companionstudio.nyx.home.FakeSummaryRepository
import com.companionstudio.nyx.home.SummaryRepository

/** Manual dependency injection (TECH_STACK §5). One instance, created by [PetApp]. */
class AppContainer {
    val dayClock = DayClock()
    val summaryRepository: SummaryRepository = FakeSummaryRepository() // real data in M4
}
