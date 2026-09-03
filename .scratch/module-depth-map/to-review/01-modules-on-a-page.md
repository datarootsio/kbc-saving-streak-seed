# 01: Modules on a page

**What to build:** One command turns this repository's source into a single page that shows every
module in the application, grouped by the package it lives in. Nothing is scored or judged yet — the
deliverable is that a reader who has never opened the codebase can see what it is made of, and that
running the command twice produces exactly the same answer both times.

This ticket also lays down the properties every later ticket inherits: a graph document that the page
is a pure rendering of, output that never varies between runs or machines, an honest report of how
much of the source was actually understood, and a page that is readable in either theme at a normal
screen width.

**Blocked by:** None (can start immediately).

**Status:** needs-review

- [x] A single command reads the backend source and writes two outputs: a machine-readable graph document and a self-contained HTML page
- [x] The page opens in a browser with no server and no network access, with nothing loaded from outside the file
- [x] Every module in the application appears on the page, identified at class grain and grouped visually by its package
- [x] Nothing appears on the page that is not present in the graph document
- [x] Running the command twice over unchanged source produces byte-identical output, and a test asserts this
- [x] No timestamps, absolute paths, or machine-specific values appear anywhere in either output
- [x] Every collection in the graph document is emitted in a stable sorted order
- [x] A source file the tool cannot parse is reported loudly and named, never silently treated as empty
- [x] The run reports how many source files were parsed and how many were not, so a reader can judge how much weight the page deserves
- [x] The page is legible in both light and dark themes, and at laptop width nothing forces the body to scroll sideways
- [x] The tool depends on nothing outside the Python standard library, and its tests run with the standard library's own test runner
