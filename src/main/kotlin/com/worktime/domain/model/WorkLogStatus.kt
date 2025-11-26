package com.worktime.domain.model

/**
 * Status of a work log entry.
 */
enum class WorkLogStatus {
    /**
     * Worker has clocked in, still working
     */
    ONGOING,
    
    /**
     * Worker has clocked out, work completed
     */
    COMPLETED,
    
    /**
     * Worker did not show up for scheduled work
     */
    NO_SHOW,
    
    /**
     * Work log was manually adjusted by owner/admin
     */
    MANUAL_ADJUSTED
}
