// Loads a browser script (plain global-style, no modules) into a vm sandbox
// and returns the sandbox, so it can be unit-tested without a DOM.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SCRIPTS = path.join(__dirname, '../../webapp/modules/core/scripts');

module.exports = function load(relPath, globals = {}) {
  const sandbox = vm.createContext({console, ...globals});
  vm.runInContext(fs.readFileSync(path.join(SCRIPTS, relPath), 'utf8'), sandbox, {filename: relPath});
  return sandbox;
};
