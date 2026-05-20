# Module Teams

This directory defines the module-level autonomous teams that can run in separate Codex threads.

Use cases:
- Run one overall 24/7 coordinator thread.
- Run one module-level 24/7 thread for a single service or surface.
- Run multiple module-level threads in parallel while one overall coordinator monitors them.

Each module team declares:
- owner role,
- allowed paths,
- forbidden paths,
- validation profile,
- memory targets,
- handoff expectations.

The registry is declarative. Register actual running threads in `.codex/threads/thread-registry.json`.
