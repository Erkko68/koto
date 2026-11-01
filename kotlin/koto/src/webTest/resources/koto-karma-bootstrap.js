// Holds Karma's start until koto.wasm is up: the common tests run without waiting.
(function () {
    var loaded = __karma__.loaded.bind(__karma__);
    __karma__.loaded = function () {
        createKotoModule().then(function () { loaded(); });
    };
}());
