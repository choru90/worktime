-- Additive and nullable: existing schedules and clients may omit the memo.
ALTER TABLE schedules ADD COLUMN memo VARCHAR(500) NULL;
