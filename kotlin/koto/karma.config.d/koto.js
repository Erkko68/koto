// koto.js (defines createKotoModule) and the bootstrap that waits for it go before the tests; koto.wasm is only
// served, for koto.js to fetch next to itself. All three are test resources, under the test package's `kotlin/`.
config.files = [
    { pattern: 'kotlin/koto.js', watched: false, included: true, served: true, nocache: true },
    { pattern: 'kotlin/koto.wasm', watched: false, included: false, served: true, nocache: true },
    { pattern: 'kotlin/koto-karma-bootstrap.js', watched: false, included: true, served: true, nocache: true },
].concat(config.files || []);
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 30000 });
