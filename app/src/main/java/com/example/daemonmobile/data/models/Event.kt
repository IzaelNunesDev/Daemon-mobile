package com.example.daemonmobile.data.models

/**
 * Event received from the Daemon. Based on the JSON payload.
 */
sealed class DaemonEvent {
    object DaemonStarted : DaemonEvent()
    data class PlanCreated(val plan: Plan) : DaemonEvent()
    data class PlanApproved(val id: String) : DaemonEvent()
    data class StepStarted(val stepIndex: Int, val description: String) : DaemonEvent()
    data class StepCompleted(val stepIndex: Int, val output: String?) : DaemonEvent()
    data class StepFailed(val stepIndex: Int, val error: String) : DaemonEvent()
    object ExecutionFinished : DaemonEvent()
    data class ExecutionAborted(val reason: String) : DaemonEvent()
    data class ErrorMessage(val message: String) : DaemonEvent()
    data class Thinking(val stage: String) : DaemonEvent()
}
