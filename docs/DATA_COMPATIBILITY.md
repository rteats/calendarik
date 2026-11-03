# Backwards-compatible storage contract

## Invariants

1. Keep application ID \`com.rteats.calendarik\` and the release signing certificate.
2. Keep record IDs stable. Do not repurpose existing database columns.
3. Room schema version is independent from app version and wire payload version.
4. Never call \`fallbackToDestructiveMigration\` or delete the database on exceptions.
5. Never erase tombstones before every known peer has acknowledged deletion.
6. Migrate only in a transaction. Test against real previous schema snapshots.
7. If an unknown schema is found, show a recoverable error. Never overwrite it.
8. Keep old payload decoders until legacy encrypted sync messages are migrated.
9. New wire formats must contain explicit \`payloadVersion\` and support replay.
10. Back up data before any risky migration. Exports will be added before sync rollout.

## Migration pull-request checklist

- [ ] Increment \`@Database(version)\`.
- [ ] Add an explicit \`Migration(from, to)\` class.
- [ ] Register migrations in \`Room.databaseBuilder(...).addMigrations(...)\`.
- [ ] Include the generated JSON schemas in \`app/schemas\`.
- [ ] Test preservation of events, coauthors, revisions and tombstones.
- [ ] Test failed migration does not silently recreate the DB.
- [ ] Test install of release N over N-1 with same signing key.

Room schema 1 was the first shipped prototype. Schema 2 adds `daily_reactions`.
`MIGRATION_1_2` is explicitly registered, and tests verify retention of
existing events and tombstones. The v1 exported JSON schema was not committed
in the first prototype; retain historical APKs and add schema snapshots to
version control before introducing further migrations.

