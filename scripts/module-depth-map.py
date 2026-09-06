#!/usr/bin/env python3
"""The single command. Run it from the repository root, saying which day it is a picture of:

    python3 scripts/module-depth-map.py --snapshot-date 2026-09-06

It reads the backend and frontend source and writes docs/module-depth-map.json and
docs/module-depth-map.html. Nothing outside the Python standard library is needed, and
two runs over unchanged source write identical bytes.

The date has no default: the only one available is this machine's clock, and a page that
dated itself would say when it was generated rather than what it is an observation of, and
would write different bytes every day over source that had not changed.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from module_depth_map.cli import main  # noqa: E402  (after the path is set up)

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
