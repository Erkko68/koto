// Linked into koto.js with --post-js. Common Kotlin binds `external fun KotoX` by global name, so once the runtime is up
// every `_KotoX` export is installed as globalThis.KotoX, with the fixups the js target needs: C bool comes back from
// wasm as 0/1 and becomes a real boolean; a float result becomes the shortest decimal with the same f32 value; a 64-bit
// argument that arrives as a Kotlin Long object becomes a BigInt. wasmJs already passes a BigInt and a real f32, so
// those are no-ops there. The module itself becomes globalThis.kotoModule, where Kotlin finds its heap.
// ponytail: wasmJs pays for the float fixup too; a flag from Kotlin/JS to turn it on if float getters show up in profiles.
(function () {
    function install() {
        const bools = new Set(Module['kotoBoolExports'] || []), f32s = new Set(Module['kotoF32Exports'] || []);
        const i64s = new Set(Module['kotoI64Exports'] || []);
        const big = (x) => typeof x === 'object' && x !== null ? BigInt(x.toString()) : x;
        const f32 = (v) => { for (let p = 1; p < 10; p++) { const d = Number(v.toPrecision(p)); if (Math.fround(d) === v) return d; } return v; };
        for (const k in Module) {
            if (!k.startsWith('_Koto')) continue;
            const name = k.substring(1), f = Module[k];
            let g = i64s.has(name) ? (...a) => f(...a.map(big)) : f;
            if (bools.has(name)) { const h = g; g = (...a) => h(...a) !== 0; }
            if (f32s.has(name)) { const h = g; g = (...a) => f32(h(...a)); }
            globalThis[name] = g;
        }
        globalThis.kotoModule = Module;
    }
    // The runtime may already be up here (synchronous instantiation); otherwise wait for it.
    if (runtimeInitialized) {
        install();
    } else {
        var previous = Module['onRuntimeInitialized'];
        Module['onRuntimeInitialized'] = function () {
            if (previous) previous();
            install();
        };
    }
}());
