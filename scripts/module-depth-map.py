#!/usr/bin/env python3
"""The single command. Run it from the repository root:

    python3 scripts/module-depth-map.py

It reads the backend source and writes docs/module-depth-map.json and
docs/module-depth-map.html. Nothing outside the Python standard library is needed, and
two runs over unchanged source write identical bytes.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from module_depth_map.cli import main  # noqa: E402  (after the path is set up)

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
