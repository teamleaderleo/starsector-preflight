# Starsector Preflight — contributor entry point

There is no living handoff document. Do not create one, and do not add per-session
"handoff", "postmerge", or "next operator" notes — those drifted and contradicted each
other, which is why they were removed.

Durable project state lives in exactly these places:

- [docs/roadmap.md](docs/roadmap.md) — the measurement-first program and milestones.
- [docs/optimization-north-star.md](docs/optimization-north-star.md) — real-install evidence, reviewed targets, benchmark protocol, and release gates.
- [docs/evidence/](docs/evidence/) — dated, point-in-time reports. Every probe, pilot, and failure is archived here; the newest dated file is the current status.
- The open GitHub issues.
- The per-feature technical docs under [docs/](docs/) (for example [prepared-textures.md](docs/prepared-textures.md), [vanilla-adapter.md](docs/vanilla-adapter.md)).

Read those before changing code. When you finish a working session, add a dated report
under `docs/evidence/` rather than a new top-level handoff.
