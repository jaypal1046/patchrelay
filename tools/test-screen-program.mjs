import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const [file, mode] = process.argv.slice(2);
assert.ok(['baseline', 'fixed'].includes(mode), 'Specify baseline or fixed');
const context = vm.createContext({}, {codeGeneration: {strings: false, wasm: false}});
new vm.Script(fs.readFileSync(file, 'utf8')).runInContext(context, {timeout: 1000});
assert.ok(context['PatchRelay:screens'], 'Missing compiler module export');
function call(expression) {
  return new vm.Script(expression).runInContext(context, {timeout: 1000});
}
function render(expression) {
  const tree = JSON.parse(call(expression));
  assert.equal(tree.screenId, 'checkout');
  assert.equal(tree.type, 'column');
  assert.ok(tree.children.length > 0 && tree.children.length <= 32);
  for (const node of tree.children) {
    assert.ok(['text', 'button'].includes(node.type), 'Unsupported widget');
    assert.ok(typeof node.text === 'string' && node.text.length > 0 && node.text.length <= 1024);
    if (node.type === 'button') assert.match(node.action, /^[a-z][a-z0-9_-]{0,31}$/);
  }
  return tree;
}
const module = "this['PatchRelay:screens']";
let tree = render(`${module}.renderScreen('checkout')`);
assert.ok(tree.children.some(n => n.text === 'Items: 1'));
assert.ok(tree.children.some(n => n.text === `Total: ${mode === 'fixed' ? 100 : 0}`));
assert.equal(tree.children.some(n => n.action === 'pay'), mode === 'fixed');
tree = render(`${module}.handleEvent('checkout','add')`);
assert.ok(tree.children.some(n => n.text === 'Items: 2'));
assert.ok(tree.children.some(n => n.text === `Total: ${mode === 'fixed' ? 200 : 0}`));
if (mode === 'fixed') {
  tree = render(`${module}.handleEvent('checkout','pay')`);
  assert.ok(tree.children.some(n => n.text === 'Payment confirmed: 200'));
}
assert.throws(() => call(`${module}.renderScreen('unknown')`));
assert.throws(() => call(`${module}.handleEvent('checkout','unsupported')`));
console.log(`Compiled Kotlin screen verified: ${mode}, rendering, events and unsupported-call rejection.`);
