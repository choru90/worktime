package com.worktime.deploy;

import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
import java.util.Map;

/** Test classpath only; never included in the deployment JAR. */
public final class ReleaseFixture {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("WORKTIME_MIGRATION_URL");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/worktime_release_test_[a-z0-9_]+\\?serverTimezone=UTC&characterEncoding=UTF-8"))
            throw new IllegalArgumentException("Fixed disposable loopback schema required");
        String user=System.getenv("WORKTIME_MIGRATION_USER"), password=System.getenv("WORKTIME_MIGRATION_PASSWORD");
        Flyway.configure().dataSource(url,user,password).target("5").cleanDisabled(true)
                .placeholders(Map.of("legacyScheduleTimezone","UTC")).load().migrate();
        try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) {
            s.execute("SET time_zone='+00:00'");
            s.execute("INSERT INTO users(id,email,password,name,role) VALUES (UNHEX(LPAD('1',32,'0')),'release-synthetic@example.test','not-a-login-password','복원 검증','OWNER')");
            s.execute("INSERT INTO owners(id,user_id) VALUES (UNHEX(LPAD('2',32,'0')),UNHEX(LPAD('1',32,'0')))");
            s.execute("INSERT INTO stores(id,owner_id,name,timezone) VALUES (UNHEX(LPAD('3',32,'0')),UNHEX(LPAD('2',32,'0')),'합성 사업장','Asia/Seoul')");
            s.execute("INSERT INTO workers(id,name,phone_number,owner_id,store_id) VALUES (UNHEX(LPAD('4',32,'0')),'합성 직원','00000000000',UNHEX(LPAD('2',32,'0')),UNHEX(LPAD('3',32,'0')))");
            s.execute("INSERT INTO schedules(id,worker_id,store_id,start_at_utc,end_at_utc,timezone,status) VALUES (UNHEX(LPAD('5',32,'0')),UNHEX(LPAD('4',32,'0')),UNHEX(LPAD('3',32,'0')),'2026-11-27 11:00:00.123456','2026-11-27 12:00:00.654321','Asia/Seoul','PROPOSED_BY_OWNER')");
            s.execute("INSERT INTO wage_contracts(id,worker_id,hourly_wage,start_date) VALUES (UNHEX(LPAD('6',32,'0')),UNHEX(LPAD('4',32,'0')),12345.67,'2026-01-01')");
            s.execute("INSERT INTO work_logs(id,worker_id,clock_in_at,clock_out_at,status,memo,correction_count,correction_reason) VALUES (UNHEX(LPAD('7',32,'0')),UNHEX(LPAD('4',32,'0')),'2026-11-26 23:30:00.123456','2026-11-27 01:00:00.654321','COMPLETED','합성 & <text>',1,'정정 보존')");
        }
    }
}
