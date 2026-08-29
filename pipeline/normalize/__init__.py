"""Source adapters. Each adapter turns one upstream dataset into the unified compendium
schema (pipeline/schema/compendium.schema.json). Adapters are pure functions over the
cached JSON — no network, no side effects — so they can be unit-tested with fixtures."""
