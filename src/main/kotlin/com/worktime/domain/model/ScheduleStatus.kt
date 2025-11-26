package com.worktime.domain.model

/**
 * Status of a work schedule.
 */
enum class ScheduleStatus {
    /**
     * Worker requested this schedule
     */
    REQUESTED_BY_WORKER,
    
    /**
     * Owner proposed this schedule
     */
    PROPOSED_BY_OWNER,
    
    /**
     * Both parties confirmed the schedule
     */
    CONFIRMED,
    
    /**
     * Schedule was rejected
     */
    REJECTED,
    
    /**
     * Schedule was canceled
     */
    CANCELED
}
