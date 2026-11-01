import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';

function start(bytes) {
    const context = vm.createContext({}, {codeGeneration: {strings: false, wasm: false}});
    new vm.Script(bytes.toString()).runInContext(context, {timeout: 5000});
    return context.__patchrelay;
}

export async function verifySimpleProgram(bytes, edition, previousState) {
    assert.ok(['baseline', 'patch'].includes(edition));
    const patched = edition === 'patch';
    const version = patched ? 'v2' : 'v1';
    const price = patched ? 9 : 12;
    const points = patched ? 20 : 10;
    const app = start(bytes);
    if (previousState) assert.equal(app.restoreState(previousState), 'restored');
    const state = api => JSON.parse(api.exportState()).values;
    const go = (page, api = app) => state(api)['simple.page'] === page ? api.renderScreen('checkout') : api.handleEvent('checkout', 'c_nav_' + page, null);
    assert.match(app.renderScreen('checkout'), new RegExp('Simple pages ' + version));
    assert.match(go('home'), new RegExp('Home ' + version));
    const quantity = state(app)['simple.quantity'];
    assert.match(go('catalog'), new RegExp('Catalog ' + version));
    assert.match(app.handleEvent('checkout', 'c_add_product', null), new RegExp('Items in cart: ' + (quantity + 1)));
    const cart = go('cart');
    assert.ok(cart.includes(`Total: $${(quantity + 1) * price}`));
    assert.ok(cart.includes(`Reward points: ${(quantity + 1) * points}`));
    go('profile');
    assert.match(app.handleEvent('checkout', 'c_profile_name', 'Jay'), /Hello Jay/);
    if (patched) assert.match(go('offers'), /Free delivery on every order/);
    go('cart');
    assert.equal(JSON.parse(await app.invokeFunction('simple.points.v1', '[2]')), 2 * points);
    const snapshot = app.exportState();
    const fresh = start(bytes);
    assert.equal(fresh.restoreState(snapshot), 'restored');
    assert.ok(fresh.renderScreen('checkout').includes(`Total: $${(quantity + 1) * price}`));
    assert.equal(state(fresh)['simple.name'], 'Jay');
    const futureState = JSON.parse(snapshot);
    futureState.values['simple.page'] = 'future_destination';
    const fallback = start(bytes);
    assert.equal(fallback.restoreState(JSON.stringify(futureState)), 'restored');
    assert.match(fallback.renderScreen('checkout'), new RegExp('Home ' + version));
    assert.equal(state(fallback)['simple.quantity'], quantity + 1);
    assert.equal(state(fallback)['simple.name'], 'Jay');
    assert.throws(() => fresh.handleEvent('checkout', 'c_unknown', null), /Unknown Compose action/);
    return snapshot;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const [file = fileURLToPath(new URL('../screens/build/compileSync/js/main/productionExecutable/kotlin/PatchRelayScreens.js', import.meta.url)), edition = 'baseline'] = process.argv.slice(2);
    await verifySimpleProgram(readFileSync(file), edition);
    console.log(`PASS: ${edition} pages, Compose callbacks, pricing/rewards, navigation and fresh-program state restoration.`);
}
