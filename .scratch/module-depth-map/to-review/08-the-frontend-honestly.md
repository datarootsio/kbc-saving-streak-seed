# 08: The frontend, honestly

**What to build:** The frontend appears on the page alongside the backend, analysed by the same rules
at file grain, so the picture is of the application rather than of half of it.

Its result is shown rather than smoothed over. The largest file in this repository presents almost
nothing to a caller while containing a great deal, and under the measure this page uses it scores as
low-leverage for its size. That outcome is the clearest demonstration available of what the page
exists to teach: depth is complexity hidden behind a small interface, not a lot of code. Drawing the
frontend as one tidy box would be a picture that lies by omission.

**Blocked by:** 03 (Reach, and the fan).

**Status:** needs-review

- [x] Frontend source is analysed at file grain and appears on the page beside the backend modules
- [x] Interface cost, reach and depth are computed for frontend modules by the same rules as for backend modules
- [x] What a frontend module exports, and what it reaches, are both derived from the source
- [x] A large frontend module presenting a small interface is not reported as deep on account of its size
- [x] The page makes the frontend's shape visible rather than collapsing it into a single unscored box
- [x] Frontend source the tool cannot parse is reported loudly and named, as backend source is
- [x] Test code, build output and dependencies are excluded from the graph
