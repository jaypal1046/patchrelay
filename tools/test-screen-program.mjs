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
  if (tree.backAction !== undefined) assert.match(tree.backAction, /^[a-z][a-z0-9_-]{0,31}$/);
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
  return {...Object.fromEntries(all.map(n => [n.id, n])), backAction: tree.backAction};
}
const event = (action, value = null) => render('handleEvent','checkout',action,value);
let tree = render('renderScreen', 'checkout');
assert.equal(tree.items.text, 'Items: 1');
assert.equal(tree.total.text, 'Total: 100');
tree = event('add');
assert.equal(tree.total.text, 'Total: 200');
if (mode === 'fixed') {
  tree = event('load_repository');
  assert.equal(tree.api_load.enabled, false);
  const requests = JSON.parse(call('takeHostRequests'));
  assert.deepEqual(requests, [{id:1,function:'github.repository.v1',argument:'JetBrains/kotlin'}]);
  assert.equal(call('takeHostRequests'), '[]');
  tree = render('handleHostResult', 'checkout', 999, 'stale', null);
  assert.equal(tree.api_load.enabled, false);
  tree = render('handleHostResult', 'checkout', 1, 'JetBrains/kotlin · 10 stars', null);
  assert.match(tree.api_status.text, /10 stars/);
  assert.equal(tree.api_load.enabled, true);
  tree = event('load_repository');
  assert.equal(JSON.parse(call('takeHostRequests'))[0].id, 2);
  tree = render('handleHostResult', 'checkout', 2, null, 'failure');
  assert.equal(tree.api_status.text, 'API unavailable. Try again.');
  assert.equal(tree.api_load.enabled, true);
  assert.equal(tree.title.text, 'Cart');
  assert.equal(tree.backAction, undefined, 'Root must delegate Back to Android');
  tree = event('remove'); tree = event('remove');
  assert.equal(tree.items.text, 'Items: 1');
  assert.equal(tree.remove.enabled, false);
  for (let i = 0; i < 110; i++) tree = event('add');
  assert.equal(tree.items.text, 'Items: 100');
  assert.equal(tree.add.enabled, false);
  for (let i = 0; i < 98; i++) tree = event('remove');
  assert.equal(tree.items.text, 'Items: 2');

  tree = event('to_review'); assert.equal(tree.title.text, 'Cart');
  tree = event('place_order'); assert.equal(tree.title.text, 'Cart');
  tree = event('to_details'); assert.equal(tree.title.text, 'Details');
  assert.equal(tree.backAction, 'back_cart');
  tree = event(tree.backAction); assert.equal(tree.title.text, 'Cart');
  tree = event('to_details');
  tree = event('to_details'); assert.equal(tree.title.text, 'Details', 'Stale cart tap must not skip details');
  tree = event('to_review'); assert.equal(tree.title.text, 'Details');
  tree = event('email','invalid');
  assert.equal(tree.validation.style, 'error'); assert.equal(tree.next.enabled, false);
  tree = event('email','guest@example.com');
  assert.equal(tree.next.enabled, false, 'Terms required');
  tree = event('terms','true'); assert.equal(tree.next.enabled, true);
  assert.throws(() => call('handleEvent','checkout','terms','not-boolean'));
  assert.throws(() => call('handleEvent','checkout','email','a'.repeat(1025)));

  tree = event('to_review');
  assert.equal(tree.title.text, 'Review');
  assert.equal(tree.backAction, 'back_details');
  assert.equal(tree.total.text, 'Total: 200');
  tree = event('email','late@example.com');
  assert.equal(tree.receipt_email.text, 'Receipt: guest@example.com', 'Stale field event must not change review');
  tree = event(tree.backAction);
  assert.equal(tree.email.value, 'guest@example.com');
  assert.equal(tree.terms.checked, true);
  tree = event(tree.backAction); assert.equal(tree.items.text, 'Items: 2');
  assert.equal(tree.backAction, undefined);
  tree = event('add'); assert.equal(tree.total.text, 'Total: 300');
  tree = event('to_details'); assert.equal(tree.email.value, 'guest@example.com');
  tree = event('to_review');
  assert.equal(tree.total.text, 'Total: 300');
  tree = event('place_order');
  assert.equal(tree.title.text, 'Receipt');
  assert.equal(tree.backAction, 'back_cart');
  assert.equal(tree.status.text, 'Payment confirmed: 300');
  assert.equal(tree.orders.text, 'Orders created: 1');
  for (let i = 0; i < 10; i++) tree = event('place_order');
  assert.equal(tree.orders.text, 'Orders created: 1', 'Duplicate local submit must be ignored');
  tree = event('back_cart');
  assert.equal(tree.next.enabled, false);
  tree = event('add'); assert.equal(tree.items.text, 'Items: 3');
  tree = event('to_details'); assert.equal(tree.title.text, 'Cart');
  tree = event('new_order');
  assert.equal(tree.items.text, 'Items: 1');
  tree = event('to_details');
  assert.equal(tree.email.value, ''); assert.equal(tree.terms.checked, false);
  assert.equal(tree.next.enabled, false);
} else {
  tree = event('pay');
  assert.equal(tree.status.text, 'Payment confirmed: 200');
}
assert.throws(() => call('renderScreen','unknown'));
assert.throws(() => call('handleEvent','checkout','unsupported',null));
assert.deepEqual(JSON.parse(call('patchedFunctionIds')), mode === 'fixed' ? ['checkout.total.v1'] : []);
if (mode === 'fixed') {
  assert.equal(call('invokePatch','checkout.total.v1',3,100),'300');
  assert.equal(call('invokePatch','checkout.total.v1',0,100),'0');
  assert.throws(() => call('invokePatch','checkout.total.v1',101,100));
} else assert.throws(() => call('invokePatch','checkout.total.v1',3,100));
assert.throws(() => call('invokePatch','checkout.shipping.v1',10,5));
console.log('Compiled Kotlin verified: ' + mode + ', schema, navigation/state guards, quantity bounds, validation, local duplicate-submit protection and function exports.');
