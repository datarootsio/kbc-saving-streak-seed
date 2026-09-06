#!/usr/bin/env python3
"""The single command. Run it from the repository root, saying which day it is a picture of:

    python3 scripts/module-depth-map.py --snapshot-date <the day you are dating it>

Written with the placeholder rather than a day, because this is one of the two lines a
maintainer copies from and a day written here ages into a trap: regenerating from new
source with a stale date passes every check, since the test that compares the committed
outputs against a fresh run hands that run the date it reads out of the committed file.
The page then says it is an observation of a day the source it describes had not reached.

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
