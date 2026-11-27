package com.worktime.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.*;

/** Migration-only entry point: no Spring context, HTTP listener, JPA or push worker. */
public final class MigrationCli {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing migration configuration");
        return value;
    }
    private static List<Map<String,Object>> rows(Connection c, String sql) throws SQLException {
        var out = new ArrayList<Map<String,Object>>();
        try (var s = c.createStatement(); var rs = s.executeQuery(sql)) {
            var md = rs.getMetaData();
            while (rs.next()) {
                var row = new LinkedHashMap<String,Object>();
                for (int i=1; i<=md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), v == null ? null :
                            v instanceof byte[] ? HexFormat.of().formatHex((byte[])v) : rs.getString(i));
                }
                out.add(row);
                if (out.size() > 1000000) throw new IllegalStateException("Snapshot capacity exceeded; review capacity before release");
            }
        }
        return out;
    }
    private static Map<String,Object> snapshot(Connection c, Flyway flyway) throws Exception {
        var out = new LinkedHashMap<String,Object>();
        out.put("identity", rows(c,"SELECT @@server_uuid AS server_uuid, DATABASE() AS `database`").get(0));
        var history = new ArrayList<Map<String,Object>>();
        for (var row : rows(c,"SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank")) {
            row.put("checksum", row.get("checksum") == null ? null : Integer.valueOf((String)row.get("checksum")));
            row.put("success", "1".equals(row.get("success")));
            history.add(row);
        }
        out.put("history", history);
        int version=Integer.parseInt((String)history.get(history.size()-1).get("version"));
        if (version >= 6) {
            var indexes=rows(c,"SELECT table_name,index_name,seq_in_index,column_name,non_unique FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('push_devices','push_deliveries') ORDER BY table_name,index_name,seq_in_index");
            Set<String> actual=new HashSet<>();
            for (var r:indexes) actual.add(r.get("table_name")+":"+r.get("index_name")+":"+r.get("seq_in_index")+":"+r.get("column_name")+":"+r.get("non_unique"));
            if (!actual.equals(Set.of("push_devices:PRIMARY:1:push_token:0","push_devices:idx_push_device_user:1:user_id:1",
                    "push_deliveries:PRIMARY:1:id:0","push_deliveries:idx_push_due:1:due_at:1",
                    "push_deliveries:idx_push_due:2:id:1","push_deliveries:idx_push_expiration:1:expires_at:1")))
                throw new IllegalStateException("Push index contract changed");
            if (rows(c,"SELECT constraint_name FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='push_devices' AND referenced_table_name='users' AND delete_rule='CASCADE'").size()!=1)
                throw new IllegalStateException("Push foreign key contract changed");
        }
        if (version >= 7) {
            var memo=rows(c,"SELECT column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='schedules' AND column_name='memo'");
            if (memo.size()!=1 || !"varchar(500)".equals(memo.get(0).get("column_type")) || !"YES".equals(memo.get(0).get("is_nullable")))
                throw new IllegalStateException("Memo schema contract changed");
        }
        var resolved = new ArrayList<Map<String,Object>>();
        for (var migration : flyway.info().all()) {
            if (migration.getVersion() == null) throw new IllegalStateException("Repeatable migration requires separate review");
            var row = new LinkedHashMap<String,Object>();
            row.put("version", migration.getVersion().toString()); row.put("checksum", migration.getChecksum());
            resolved.add(row);
        }
        out.put("resolved", resolved);
        var tables = new TreeMap<String,Object>();
        for (var row : rows(c,"SELECT table_name,engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")) {
            String name = (String)row.get("table_name");
            if (!"InnoDB".equals(row.get("engine"))) throw new IllegalStateException("Only InnoDB backup supported");
            if (name.equals("flyway_schema_history")) continue;
            if (!name.matches("[a-z_]+")) throw new IllegalStateException("Unexpected table name");
            tables.put(name, rows(c,"SELECT * FROM `"+name+"` ORDER BY 1"));
        }
        out.put("tables", tables);
        out.put("columns", rows(c,"SELECT table_name,column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position"));
        out.put("indexes", rows(c,"SELECT table_name,index_name,seq_in_index,column_name,non_unique FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index"));
        return out;
    }
    public static void main(String[] args) {
        try {
            if (args.length != 1 || !Set.of("snapshot","migrate6","migrate7").contains(args[0]))
                throw new IllegalArgumentException("Use snapshot, migrate6 or migrate7");
            String url=required("WORKTIME_MIGRATION_URL"), user=required("WORKTIME_MIGRATION_USER");
            String password=System.getenv("WORKTIME_MIGRATION_PASSWORD");
            if (password == null) throw new IllegalArgumentException("Missing migration credential");
            // No JDBC properties from the normal application or arbitrary CLI SQL.
            if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("MySQL required");
            try (var c=DriverManager.getConnection(url,user,password)) {
                var identity=rows(c,"SELECT @@server_uuid AS server_uuid, DATABASE() AS `database`").get(0);
                if (!required("WORKTIME_MIGRATION_SERVER_UUID").equals(identity.get("server_uuid"))
                        || !required("WORKTIME_MIGRATION_DATABASE").equals(identity.get("database")))
                    throw new IllegalStateException("Database identity mismatch before migration");
            }
            Flyway f=Flyway.configure().dataSource(url,user,password).cleanDisabled(true)
                    .baselineOnMigrate(false).validateOnMigrate(true)
                    .placeholders(Map.of("legacyScheduleTimezone","UTC")).load();
            // Pending V6/V7 are expected before expansion. Every other validation
            // error (including applied checksum drift/failure/missing migration) refuses.
            var validation=f.validateWithResult();
            if (!validation.validationSuccessful) {
                if (validation.invalidMigrations == null || validation.invalidMigrations.isEmpty())
                    throw new IllegalStateException("Unclassified migration validation failure");
                for (var invalid : validation.invalidMigrations) {
                    if (!"RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED".equals(invalid.errorDetails.errorCode.toString())
                            || !Set.of("6","7").contains(invalid.version))
                        throw new IllegalStateException("Applied migration history drift");
                }
            }
            if (!args[0].equals("snapshot")) {
                int target=Integer.parseInt(args[0].substring(7));
                // Resolve/validate applied checksums against this exact artifact before any DDL.
                var before=f.info().current();
                if (before == null || !before.getVersion().toString().equals(Integer.toString(target-1)))
                    throw new IllegalStateException("Migration requires exact preceding version");
                f=Flyway.configure().dataSource(url,user,password).cleanDisabled(true).baselineOnMigrate(false)
                        .validateOnMigrate(true).target(Integer.toString(target))
                        .placeholders(Map.of("legacyScheduleTimezone","UTC")).load();
                f.migrate();
                if (!f.info().current().getVersion().toString().equals(Integer.toString(target)))
                    throw new IllegalStateException("Migration target mismatch");
            }
            try (var c=DriverManager.getConnection(url,user,password)) {
                c.createStatement().execute("SET time_zone='+00:00'");
                String result=JSON.writeValueAsString(snapshot(c,f));
                System.out.println("WORKTIME_MIGRATION_JSON="+result);
            }
        } catch (Exception error) {
            // JDBC/Flyway exceptions may contain credentials or customer row values.
            System.err.println("Migration operation refused or failed; inspect private server evidence and exact journal checkpoint");
            System.exit(1);
        }
    }
}
