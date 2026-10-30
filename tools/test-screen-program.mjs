import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const [file, mode] = process.argv.slice(2);
assert.ok(['baseline', 'fixed'].includes(mode));
const context = vm.createContext({}, {codeGeneration: {strings: false, wasm: false}});
new vm.Script(fs.readFileSync(file, 'utf8')).runInContext(context, {timeout: 1000});
const module = "this['PatchRelay:screens']";
function call(method, ...args) {
  return new vm.Script(module + '.' + method + '(' + args.map(x => JSON.stringify(x)).join(',') + ')')
    .runInContext(context, {timeout: 1000});
}
function render(method, ...args) {
  const tree = JSON.parse(call(method, ...args));
  assert.equal(tree.screenId, 'checkout');
  assert.equal(tree.type, 'column');
  const all = [];
  const ids = new Set();
  function walk(nodes, depth = 1) {
    assert.ok(depth <= 8 && nodes.length > 0 && nodes.length <= 32);
    for (const node of nodes) {
      assert.match(node.id, /^[a-z][a-z0-9_-]{0,63}$/);
      assert.ok(!ids.has(node.id)); ids.add(node.id);
      assert.ok(['text','button','input','toggle','column','row','card'].includes(node.type));
      all.push(node);
      if (node.children) walk(node.children, depth + 1);
    }
  }
  walk(tree.children); assert.ok(all.length <= 128);
  return Object.fromEntries(all.map(n => [n.id, n]));
}
let tree = render('renderScreen', 'checkout');
assert.equal(tree.items.text, 'Items: 1');
assert.equal(tree.total.text, 'Total: 100');
tree = render('handleEvent', 'checkout', 'add', null);
assert.equal(tree.total.text, 'Total: 200');
if (mode === 'fixed') {
  assert.equal(tree.pay.enabled, false);
  tree = render('handleEvent', 'checkout', 'pay', null);
  assert.equal(tree.pay.enabled, false);
  tree = render('handleEvent', 'checkout', 'email', 'invalid');
  assert.equal(tree.validation.style, 'error');
  tree = render('handleEvent', 'checkout', 'email', 'guest@example.com');
  assert.equal(tree.email.value, 'guest@example.com');
  assert.equal(tree.pay.enabled, false);
  tree = render('handleEvent', 'checkout', 'terms', 'true');
  assert.equal(tree.pay.enabled, true);
  tree = render('handleEvent', 'checkout', 'pay', null);
  assert.equal(tree.status.text, 'Payment confirmed: 200');
  assert.equal(tree.add.enabled, false);
  assert.throws(() => call('handleEvent','checkout','terms','not-boolean'));
  assert.throws(() => call('handleEvent','checkout','email','a'.repeat(1025)));
} else {
  tree = render('handleEvent','checkout','pay',null);
  assert.equal(tree.status.text, 'Payment confirmed: 200');
}
assert.throws(() => call('renderScreen','unknown'));
assert.throws(() => call('handleEvent','checkout','unsupported',null));
console.log('Compiled Kotlin screen verified: ' + mode);
