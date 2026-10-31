import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {existsSync, mkdtempSync, readFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {setTimeout as delay} from 'node:timers/promises';

// Real Chromium worker/CSP tests. These do not replace Android WebView device coverage.
const browser = process.env.CHROME_BIN || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'
].find(existsSync);
assert(browser, 'Set CHROME_BIN to a Chromium browser');
const profile = mkdtempSync(join(tmpdir(), 'patchrelay-browser-'));
const child = spawn(browser, ['--headless=new', '--remote-debugging-port=0', `--user-data-dir=${profile}`,
    '--no-first-run', '--no-default-browser-check', '--disable-extensions', '--disable-background-networking'],
    {windowsHide:true, stdio:['ignore', 'ignore', 'pipe']});
let socket;
try {
    const address = await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Browser startup timed out')), 20000);
        let output = '';
        child.stderr.on('data', data => {
            output += data;
            const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);
            if (match) { clearTimeout(timer); resolve(match[1]); }
        });
        child.once('error', error => { clearTimeout(timer); reject(error); });
        child.once('exit', code => { clearTimeout(timer); reject(new Error(`Browser exited: ${code}`)); });
    });
    socket = new WebSocket(address);
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
    let serial = 0;
    const pending = new Map();
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (!message.id) return;
        const call = pending.get(message.id);
        if (!call) return;
        pending.delete(message.id); clearTimeout(call.timer);
        if (message.error) call.reject(new Error(JSON.stringify(message.error)));
        else call.resolve(message.result);
    };
    function cdp(method, params = {}, sessionId) {
        return new Promise((resolve, reject) => {
            const id = ++serial;
            const timer = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
            pending.set(id, {resolve, reject, timer});
            socket.send(JSON.stringify({id, method, params, ...(sessionId ? {sessionId} : {})}));
        });
    }
    const {targetId} = await cdp('Target.createTarget', {url:'about:blank'});
    const {sessionId} = await cdp('Target.attachToTarget', {targetId, flatten:true});
    async function evaluate(expression) {
        const response = await cdp('Runtime.evaluate', {expression, returnByValue:true, awaitPromise:true}, sessionId);
        assert(!response.exceptionDetails, JSON.stringify(response.exceptionDetails));
        return response.result.value;
    }
    const html = readFileSync(new URL('../sdk/src/main/assets/patchrelay-interpreter.html', import.meta.url));
    async function reset() {
        await cdp('Page.navigate', {url:`data:text/html;base64,${html.toString('base64')}`}, sessionId);
        for (let tries = 0; tries < 100; tries++) {
            if (await evaluate('typeof window.__relay === "object"')) return;
            await delay(20);
        }
        throw new Error('Interpreter page did not initialize');
    }
    let ticket = 0;
    async function run(source) {
        const id = ++ticket;
        await evaluate(`window.__relay.send(${id},${JSON.stringify(source)})`);
        for (let tries = 0; tries < 250; tries++) {
            const reply = await evaluate(`window.__relay.take(${id})`);
            if (reply !== null) return JSON.parse(reply);
            await delay(10);
        }
        throw new Error('Worker response timed out');
    }
    const wrap = expression => `JSON.stringify({ok:true,value:${expression}})`;
    await reset();
    const program = readFileSync(new URL('../demo/src/main/assets/baseline-screen.js', import.meta.url), 'utf8');
    assert.deepEqual(await run(program + '\n;' + wrap("'ready'")), {ok:true,value:'ready'});
    const tree = await run(wrap("self['PatchRelay:screens'].renderScreen('checkout')"));
    assert.equal(JSON.parse(tree.value).screenId, 'checkout');
    console.log('PASS: compiled Kotlin program executes in worker');

    const capabilities = await run(wrap("JSON.stringify(['window','document','Android','java','Java','__relay','RTCPeerConnection','localStorage'].map(name => typeof self[name]))"));
    assert.deepEqual(JSON.parse(capabilities.value), Array(8).fill('undefined'));
    const storage = await run(`(function(){try { indexedDB.open('escape'); return ${wrap("'allowed'")}; } catch(e) {return ${wrap("'denied'")};}})()`);
    assert.equal(storage.value, 'denied');
    console.log('PASS: no DOM/native bridge/WebRTC/local storage; IndexedDB denied by opaque origin');

    await run(`self.blocked = []; Promise.all([
        fetch('https://example.invalid/escape'), fetch('file:///etc/passwd'),
        fetch('content://settings/system'),
        new Promise((resolve,reject) => { try { const s = new WebSocket('wss://example.invalid/'); s.onopen = resolve; s.onerror = reject; } catch(e) { reject(e); } })
    ].map(p => p.then(() => false, () => true))).then(result => self.blocked = result);
    ${wrap("'started'")}`);
    let blocked;
    for (let tries = 0; tries < 100; tries++) {
        blocked = JSON.parse((await run(wrap('JSON.stringify(self.blocked)'))).value);
        if (blocked.length === 4) break;
        await delay(20);
    }
    assert.deepEqual(blocked, [true,true,true,true]);
    assert.equal((await run(`try { importScripts('https://example.invalid/escape.js'); ${wrap("'allowed'")}; } catch(e) { ${wrap("'denied'")}; }`)).value, 'denied');
    console.log('PASS: HTTPS/file/content fetch, WebSocket and remote script loading denied');

    assert.equal((await run("throw new Error('malicious')")).ok, false);
    assert.equal((await run("'x'.repeat(128 * 1024 + 1)")).ok, false);
    assert.equal((await run("({not:'a string'})")).ok, false);
    console.log('PASS: exceptions, oversized and malformed replies rejected');

    const loop = ++ticket;
    await evaluate(`window.__relay.send(${loop},'while(true) {}')`);
    await delay(100);
    assert.equal(await evaluate(`window.__relay.take(${loop})`), null);
    assert.equal(await evaluate('window.__relay.stop(); 7'), 7);
    await reset();
    assert.deepEqual(await run(wrap("'recovered'")), {ok:true,value:'recovered'});
    console.log('PASS: infinite-loop worker terminates; replacement interpreter runs');
    await cdp('Browser.close');
} finally {
    socket?.close();
    if (child.exitCode === null) {
        await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(5000)]);
        if (child.exitCode === null) child.kill();
    }
    // Only remove the unique temporary profile created by this test.
    for (let tries = 0; tries < 30; tries++) {
        try { rmSync(profile, {recursive:true, force:true}); break; }
        catch (error) {
            if (tries === 29) {
                if (!['EPERM', 'EBUSY', 'ENOTEMPTY'].includes(error.code)) throw error;
                console.warn('Browser retained a lock on its temporary test profile:', profile);
            } else await delay(200);
        }
    }
}
