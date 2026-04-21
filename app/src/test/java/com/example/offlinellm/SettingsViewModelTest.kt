package com.example.offlinellm

import com.example.offlinellm.domain.InferenceSettings
import com.example.offlinellm.domain.SettingsRepository
import com.example.offlinellm.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun updateWritesSettings() = runTest {
        val fake = FakeSettingsRepository()
        val vm = SettingsViewModel(fake)
        val target = InferenceSettings(systemPrompt = "x", temperature = 0.2f)
        vm.update(target)
        assertEquals("x", fake.state.value.systemPrompt)
        assertEquals(0.2f, fake.state.value.temperature)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    val state = MutableStateFlow(InferenceSettings())
    override val settings: Flow<InferenceSettings> = state

    override suspend fun update(settings: InferenceSettings) {
        state.value = settings
    }
}
