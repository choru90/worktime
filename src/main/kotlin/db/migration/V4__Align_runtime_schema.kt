package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Calendar
import java.util.TimeZone

/** Immutable upgrade from V3. Legacy values remain available for inspection/backup recovery. */
class V4__Align_runtime_schema : BaseJavaMigration() {
    override fun getChecksum(): Int = 2026101904

    private data class Shift(val id: ByteArray, val start: LocalDateTime, val end: LocalDateTime, val zone: String)
    private data class Wage(val id: ByteArray, val worker: String, val start: LocalDate)
    private val enumColumns = listOf(
        Triple("business_hours", "day_of_week", listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")),
        Triple("regular_shifts", "day_of_week", listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")),
        Triple("users", "role", listOf("OWNER", "WORKER")),
        Triple("notifications", "type", listOf("SCHEDULE_PROPOSED", "SCHEDULE_CONFIRMED", "SCHEDULE_REJECTED", "SCHEDULE_CANCELED")),
        Triple("schedules", "status", listOf("REQUESTED_BY_WORKER", "PROPOSED_BY_OWNER", "CONFIRMED", "REJECTED", "CANCELED")),
        Triple("work_logs", "status", listOf("ONGOING", "COMPLETED", "CONFIRMED", "REJECTED", "NO_SHOW", "MANUAL_ADJUSTED"))
    )
    private fun execute(c: Connection, sql: String) { c.createStatement().use { it.execute(sql) } }
    private fun assertZero(c: Connection, sql: String, message: String) {
        c.createStatement().use { s -> s.executeQuery(sql).use { r -> r.next(); check(r.getLong(1) == 0L) { message } } }
    }
    private fun utc(date: LocalDate, time: java.time.LocalTime, zone: ZoneId): LocalDateTime {
        val local = date.atTime(time)
        val offsets = zone.rules.getValidOffsets(local)
        check(offsets.size == 1) { "Legacy schedule has an ambiguous/nonexistent local time; resolve it before migration" }
        return local.atOffset(offsets.single()).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
    }
    override fun migrate(context: Context) {
        val c = context.connection
        val priorZone = c.createStatement().use { s -> s.executeQuery("SELECT @@session.time_zone").use { r -> r.next(); r.getString(1) } }
        execute(c, "SET time_zone = '+00:00'")
        try {
            // Every semantic check precedes MySQL DDL, which cannot be rolled back reliably.
            for ((table, column, values) in enumColumns) {
                val accepted = if (table == "schedules") values + listOf("REQUESTED_BY_OWNER", "CANCELLED") else values
                val choices = accepted.joinToString(",") { "'$it'" }
                assertZero(c, "SELECT COUNT(*) FROM $table WHERE $column NOT IN ($choices)", "Unknown legacy $table.$column value; review before migration")
            }
            assertZero(c, "SELECT COUNT(*) FROM (SELECT worker_id,effective_date FROM wage_contracts GROUP BY worker_id,effective_date HAVING COUNT(*)>1) d", "Duplicate legacy wage start dates; resolve before migration")
            assertZero(c, "SELECT COUNT(*) FROM wage_contracts WHERE ABS(hourly_wage)>99999999", "Legacy wage exceeds DECIMAL(10,2) capacity")
            assertZero(c, "SELECT COUNT(*) FROM (SELECT user_account_id FROM workers WHERE user_account_id IS NOT NULL GROUP BY user_account_id HAVING COUNT(*)>1) d", "An account is linked to multiple legacy workers")
            val shifts = mutableListOf<Shift>()
            val fallback = context.configuration.placeholders["legacyScheduleTimezone"]?.takeIf { it.isNotBlank() }
            c.createStatement().use { s -> s.executeQuery("""
                SELECT sc.id,sc.scheduled_date,sc.start_time,sc.end_time,COALESCE(ss.timezone,ws.timezone) AS zone
                FROM schedules sc JOIN workers w ON w.id=sc.worker_id
                LEFT JOIN stores ss ON ss.id=sc.store_id LEFT JOIN stores ws ON ws.id=w.store_id
            """.trimIndent()).use { r -> while (r.next()) {
                val zoneName = r.getString("zone")?.takeIf { it.isNotBlank() } ?: fallback
                    ?: error("Legacy schedules without a store need an explicit legacyScheduleTimezone placeholder")
                val zone = ZoneId.of(zoneName)
                val date = r.getDate("scheduled_date").toLocalDate()
                val start = utc(date, r.getObject("start_time", java.time.LocalTime::class.java), zone)
                val end = utc(date, r.getObject("end_time", java.time.LocalTime::class.java), zone)
                check(end.isAfter(start)) { "Legacy schedule is reversed/overnight; resolve its date before migration" }
                shifts.add(Shift(r.getBytes("id"), start, end, zoneName))
            } } }
            val wages = mutableListOf<Wage>()
            c.createStatement().use { s -> s.executeQuery("SELECT id,HEX(worker_id) AS worker,effective_date FROM wage_contracts ORDER BY worker_id,effective_date").use { r ->
                while (r.next()) wages.add(Wage(r.getBytes("id"), r.getString("worker"), r.getDate("effective_date").toLocalDate()))
            } }
            execute(c, "ALTER TABLE workers ADD COLUMN invite_code VARCHAR(10) NULL, ADD COLUMN invite_code_expires_at DATETIME(6) NULL, ADD UNIQUE KEY uk_worker_invite_code(invite_code), ADD UNIQUE KEY uk_worker_user_account(user_account_id)")
            execute(c, "ALTER TABLE wage_contracts ADD COLUMN start_date DATE NULL, ADD COLUMN end_date DATE NULL, MODIFY COLUMN hourly_wage DECIMAL(10,2) NOT NULL, MODIFY COLUMN effective_date DATE NULL")
            c.prepareStatement("UPDATE wage_contracts SET start_date=?,end_date=? WHERE id=?").use { update ->
                wages.forEachIndexed { i, wage ->
                    val next = wages.getOrNull(i + 1)?.takeIf { it.worker == wage.worker }
                    update.setObject(1, wage.start); update.setObject(2, next?.start?.minusDays(1)); update.setBytes(3, wage.id); update.addBatch()
                }
                update.executeBatch()
            }
            execute(c, "ALTER TABLE wage_contracts MODIFY COLUMN start_date DATE NOT NULL, ADD INDEX idx_wage_contract_dates(start_date,end_date)")
            execute(c, "ALTER TABLE schedules ADD COLUMN start_at_utc DATETIME(6) NULL, ADD COLUMN end_at_utc DATETIME(6) NULL, ADD COLUMN timezone VARCHAR(50) NULL, MODIFY COLUMN scheduled_date DATE NULL, MODIFY COLUMN start_time TIME(6) NULL, MODIFY COLUMN end_time TIME(6) NULL")
            val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            c.prepareStatement("UPDATE schedules SET start_at_utc=?,end_at_utc=?,timezone=? WHERE id=?").use { update ->
                for (shift in shifts) {
                    update.setTimestamp(1, Timestamp.from(shift.start.toInstant(ZoneOffset.UTC)), utcCalendar)
                    update.setTimestamp(2, Timestamp.from(shift.end.toInstant(ZoneOffset.UTC)), utcCalendar)
                    update.setString(3, shift.zone); update.setBytes(4, shift.id); update.addBatch()
                }
                update.executeBatch()
            }
            execute(c, "ALTER TABLE schedules MODIFY COLUMN start_at_utc DATETIME(6) NOT NULL, MODIFY COLUMN end_at_utc DATETIME(6) NOT NULL, MODIFY COLUMN timezone VARCHAR(50) NOT NULL, ADD INDEX idx_schedule_time(start_at_utc,end_at_utc)")
            execute(c, "ALTER TABLE work_logs ADD COLUMN clock_in_at DATETIME(6) NULL, ADD COLUMN clock_out_at DATETIME(6) NULL, MODIFY COLUMN clock_in_time TIMESTAMP(6) NULL")
            execute(c, "UPDATE work_logs SET clock_in_at=clock_in_time,clock_out_at=clock_out_time")
            execute(c, "ALTER TABLE work_logs MODIFY COLUMN clock_in_at DATETIME(6) NOT NULL, ADD INDEX idx_work_log_clock_in(clock_in_at)")
            execute(c, "UPDATE schedules SET status='PROPOSED_BY_OWNER' WHERE status='REQUESTED_BY_OWNER'")
            execute(c, "UPDATE schedules SET status='CANCELED' WHERE status='CANCELLED'")
            for ((table, column, values) in enumColumns) {
                val choices = values.joinToString(",") { "'$it'" }
                execute(c, "ALTER TABLE $table MODIFY COLUMN $column ENUM($choices) NOT NULL")
            }
        } finally {
            c.prepareStatement("SET time_zone = ?").use { it.setString(1, priorZone); it.execute() }
        }
    }
}
