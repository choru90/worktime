# Schedule memo compatibility and rollout

`POST /api/workers/{workerId}/schedules` accepts optional `memo: string | null`
(at most 500 UTF-16 code units). Omitted and null values store SQL NULL;
an explicitly empty string stays empty. Text is not trimmed or interpreted as HTML.
Create, detail, owner/employee list and state-transition responses include the nullable
memo. Proposal ownership, conflict checks, notifications and acceptance are unchanged.
Old clients may omit memo and ignore the added response property.

Apply in this order (this PR does not deploy anything):

1. Apply additive Flyway V7 to the target DB through the existing release procedure.
   Existing rows get NULL. Never change checksums of V1–V6.
2. Roll out this backend and verify Hibernate validation plus create/detail/list on
   synthetic test data. During a rolling update, finish replacing all old instances
   before enabling the new web: the old DTO silently ignores unknown memo input.
3. Roll out the web change only after every serving backend supports memo. Verify
   input → create response → a fresh GET/list → employee acceptance retains text.

New backend + old web/mobile is supported (omitted field). New web + old backend
is **not a persistence-compatible deployment**: schedule creation succeeds but memo
is ignored. Do not reverse the order. Backend without V7 fails schema validation;
apply migration before starting it. V7 + old application remains schema-compatible
because it adds only a nullable column, verified with legacy SQL inserts and old ORM.

Rollback web first; keep V7 and its data. Old backend can run against the expanded
schema, but must not receive memo-enabled web writes. Never drop the memo column
as an automatic rollback. A later return to the new backend restores visibility
of stored memos. Existing schedules without memo continue to display a dash.

Automated coverage: ScheduleMemoIntegrationTest exercises HTTP/service/JPA reload,
both role lists, acceptance and 500/501 limits; MysqlMigrationIntegrationTest covers
fresh schema and upgrade from V6 with legacy rows/inserts and Hibernate validation.
