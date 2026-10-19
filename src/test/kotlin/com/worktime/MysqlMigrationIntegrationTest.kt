package com.worktime

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.builder.SpringApplicationBuilder
import java.sql.DriverManager

/** Opt-in, destructive tests restricted to a disposable local schema. Never use production data. */
@EnabledIfEnvironmentVariable(named = "WORKTIME_MYSQL_MIGRATION_TEST_URL", matches = ".+")
class MysqlMigrationIntegrationTest {
    private val url: String get() = System.getenv("WORKTIME_MYSQL_MIGRATION_TEST_URL").also {
        require(it.startsWith("jdbc:mysql://127.0.0.1:13307/worktime_migration_test?"))
    }
    private fun flyway(target: String? = null): Flyway {
        val config = Flyway.configure().dataSource(url, "root", "").cleanDisabled(false)
        if (target != null) config.target(MigrationVersion.fromVersion(target))
        return config.load()
    }
    @BeforeEach fun cleanBefore() { flyway().clean() }
    @AfterEach fun cleanAfter() { flyway().clean() }
    private fun sql(statement: String) {
        DriverManager.getConnection(url, "root", "").use { c ->
            c.createStatement().use { s -> s.execute("SET time_zone='+00:00'"); s.execute(statement) }
        }
    }
    private fun scalar(query: String): String = DriverManager.getConnection(url, "root", "").use { c ->
        c.createStatement().use { s -> s.execute("SET time_zone='+00:00'"); s.executeQuery(query).use { r -> r.next(); r.getString(1) } }
    }
    private fun startValidated() = SpringApplicationBuilder(Application::class.java).run(
        "--spring.datasource.url=$url", "--spring.datasource.username=root", "--spring.datasource.password=",
        "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true", "--server.port=0",
        "--jwt.secret=synthetic-migration-test-secret-with-at-least-32-bytes",
        "--logging.level.root=WARN", "--logging.level.com.worktime=WARN", "--logging.level.org.hibernate.SQL=WARN",
        "--spring.jpa.show-sql=false"
    )
    private fun legacy() {
        flyway("3").migrate()
        sql("INSERT INTO users(id,email,password,name,role) VALUES (UNHEX(LPAD('1',32,'0')),'legacy@example.test','synthetic','Owner','OWNER')")
        sql("INSERT INTO owners(id,user_id) VALUES (UNHEX(LPAD('2',32,'0')),UNHEX(LPAD('1',32,'0')))")
        sql("INSERT INTO stores(id,owner_id,name,timezone) VALUES (UNHEX(LPAD('3',32,'0')),UNHEX(LPAD('2',32,'0')),'Legacy store','Asia/Seoul')")
        sql("INSERT INTO workers(id,name,phone_number,owner_id,store_id) VALUES (UNHEX(LPAD('4',32,'0')),'Legacy worker','00000000000',UNHEX(LPAD('2',32,'0')),UNHEX(LPAD('3',32,'0')))")
        sql("INSERT INTO wage_contracts(id,worker_id,hourly_wage,effective_date) VALUES (UNHEX(LPAD('5',32,'0')),UNHEX(LPAD('4',32,'0')),10000,'2025-01-01'),(UNHEX(LPAD('6',32,'0')),UNHEX(LPAD('4',32,'0')),12000,'2025-07-01')")
        sql("INSERT INTO schedules(id,worker_id,store_id,scheduled_date,start_time,end_time,status) VALUES (UNHEX(LPAD('7',32,'0')),UNHEX(LPAD('4',32,'0')),UNHEX(LPAD('3',32,'0')),'2025-06-10','09:00:00.123456','13:00:00.654321','REQUESTED_BY_OWNER')")
        sql("INSERT INTO work_logs(id,worker_id,clock_in_time,clock_out_time,status) VALUES (UNHEX(LPAD('8',32,'0')),UNHEX(LPAD('4',32,'0')),'2025-06-10 00:00:00','2025-06-10 04:00:00','CONFIRMED')")
    }
    @Test fun `fresh schema migrates then passes Hibernate validation`() {
        startValidated().use {
            assertEquals("4", scalar("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1"))
            assertEquals("1", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workers' AND column_name='invite_code'"))
        }
    }
    @Test fun `legacy rows preserve IDs wages timestamps and local schedules`() {
        legacy()
        startValidated().use {
            assertEquals("2", scalar("SELECT COUNT(*) FROM wage_contracts"))
            assertEquals("2025-06-30", scalar("SELECT end_date FROM wage_contracts WHERE id=UNHEX(LPAD('5',32,'0'))"))
            assertEquals("2025-06-10 00:00:00", scalar("SELECT DATE_FORMAT(start_at_utc,'%Y-%m-%d %H:%i:%s') FROM schedules"))
            assertEquals("09:00:00.123456", scalar("SELECT start_time FROM schedules"))
            assertEquals("123456", scalar("SELECT MICROSECOND(start_at_utc) FROM schedules"))
            assertEquals("PROPOSED_BY_OWNER", scalar("SELECT status FROM schedules"))
            assertEquals("2025-06-10 00:00:00", scalar("SELECT DATE_FORMAT(clock_in_at,'%Y-%m-%d %H:%i:%s') FROM work_logs"))
            assertEquals("1", scalar("SELECT COUNT(*) FROM work_logs WHERE clock_in_at=clock_in_time AND clock_out_at=clock_out_time"))
        }
    }
    @Test fun `missing legacy timezone stops before schema changes`() {
        legacy(); sql("UPDATE schedules SET store_id=NULL"); sql("UPDATE workers SET store_id=NULL")
        assertThrows(Exception::class.java) { flyway().migrate() }
        assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workers' AND column_name='invite_code'"))
        assertEquals("2", scalar("SELECT COUNT(*) FROM wage_contracts"))
    }
    @Test fun `ambiguous daylight saving time stops before schema changes`() {
        legacy(); sql("UPDATE stores SET timezone='America/New_York'")
        sql("UPDATE schedules SET scheduled_date='2025-11-02',start_time='01:30',end_time='03:00'")
        assertThrows(Exception::class.java) { flyway().migrate() }
        assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workers' AND column_name='invite_code'"))
    }
    @Test fun `explicit fallback timezone upgrades an unattached legacy schedule`() {
        legacy(); sql("UPDATE schedules SET store_id=NULL"); sql("UPDATE workers SET store_id=NULL")
        Flyway.configure().dataSource(url, "root", "").placeholders(mapOf("legacyScheduleTimezone" to "Asia/Seoul")).load().migrate()
        startValidated().use { assertEquals("Asia/Seoul", scalar("SELECT timezone FROM schedules")) }
    }
    @Test fun `duplicate wage starts are rejected before altering data`() {
        legacy()
        sql("INSERT INTO wage_contracts(id,worker_id,hourly_wage,effective_date) VALUES (UNHEX(LPAD('9',32,'0')),UNHEX(LPAD('4',32,'0')),11000,'2025-01-01')")
        assertThrows(Exception::class.java) { flyway().migrate() }
        assertEquals("3", scalar("SELECT COUNT(*) FROM wage_contracts"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='workers' AND column_name='invite_code'"))
    }

}
