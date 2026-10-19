import assert from 'node:assert/strict';
import { RcdPlayer } from './web/main';
import { CoreDocument } from './core/CoreDocument';

async function test(): Promise<void> {
    const player = Object.create(RcdPlayer.prototype) as RcdPlayer;
    const document = new CoreDocument();
    const payload = new ArrayBuffer(8);
    let decodes = 0;
    player.loadFromArrayBuffer = async (data) => {
        assert.equal(data, payload);
        decodes++;
        return document;
    };
    for (const status of [404, 500]) {
        let bodyRead = false;
        globalThis.fetch = async () => ({
            ok: false, status,
            arrayBuffer: async () => { bodyRead = true; return payload; },
        } as Response);
        await assert.rejects(player.loadFromUrl('/document.rc'), new RegExp(`HTTP ${status}`));
        assert.equal(bodyRead, false, 'HTTP error bodies must not reach the wire decoder');
        assert.equal(decodes, 0, 'failed requests must preserve the current document');
    }
    globalThis.fetch = async () => ({ ok: true, arrayBuffer: async () => payload } as Response);
    assert.equal(await player.loadFromUrl('/document.rc'), document);
    assert.equal(decodes, 1, 'successful requests still load the document');
    const failure = new Error('network unavailable');
    globalThis.fetch = async () => { throw failure; };
    await assert.rejects(player.loadFromUrl('/document.rc'), (error) => error === failure);
    console.log('document URL loads reject HTTP failures before decoding and preserve network errors');
}

test().catch((error) => { console.error(error); process.exitCode = 1; });
