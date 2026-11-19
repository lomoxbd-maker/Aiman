package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lifecycle state of MAX's future assistant background service host.
 *
 * In accordance with battery and low-RAM optimization requirements, no background service
 * is spawned automatically during the foundation phase.
 */
enum class ServiceHostState {
  STOPPED,
  BOUND_ON_DEMAND,
  STANDBY_READY,
}

interface AssistantServiceContract {
  val hostState: StateFlow<ServiceHostState>

  fun markOnDemandReady()

  fun markStopped()
}

class FoundationServiceCoordinator : AssistantServiceContract {
  private val _hostState = MutableStateFlow(ServiceHostState.STOPPED)
  override val hostState: StateFlow<ServiceHostState> = _hostState.asStateFlow()

  override fun markOnDemandReady() {
    _hostState.value = ServiceHostState.BOUND_ON_DEMAND
  }

  override fun markStopped() {
    _hostState.value = ServiceHostState.STOPPED
  }
}
