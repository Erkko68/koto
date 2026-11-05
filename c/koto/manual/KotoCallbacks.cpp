// See KotoCallbacks.h. Each trampoline passes the callback's arguments in slots, less its user data, as
// CBridges.trampoline tells Kotlin to read them.
#include "../generated/Includes.hpp"
#include "KotoCallbacks.h"

namespace {

using koto::Callback;

// No user data is no function.
std::optional<Callback> function(KotoUpcall call, void* user) {
    return user ? std::optional<Callback>(Callback(call, user)) : std::nullopt;
}

template <class... Args>
uint64_t call(const Callback& f, Args... args) {
    const uint64_t slots[] = {koto::slot(args)...};
    uint64_t result = 0;
    f(slots, &result);
    return result;
}

ma_result resultOf(uint64_t slot) { return static_cast<ma_result>(static_cast<int32_t>(slot)); }

// A config's callbacks (Callbacks, at its user data), copied for what init makes of it: that one's until uninit, or
// gone with a failed init.
template <class Callbacks, class Config, class Init>
ma_result initWith(const Config* pConfig, void* Config::*user, Init init) {
    if (!pConfig) return init(nullptr);
    Config config = *pConfig;
    Callbacks* own = config.*user ? new Callbacks(*static_cast<Callbacks*>(config.*user)) : nullptr;
    config.*user = own;
    ma_result result = init(&config);
    if (result != MA_SUCCESS) delete own;
    return result;
}

// ma_device

struct DeviceCallbacks {
    std::optional<Callback> data, notification, stop;
};

DeviceCallbacks* callbacks(void* userData) { return static_cast<DeviceCallbacks*>(userData); }

void onData(ma_device* device, void* output, const void* input, ma_uint32 frameCount) {
    if (auto& f = callbacks(device->pUserData)->data) call(*f, device, output, input, frameCount);
}

void onNotification(const ma_device_notification* notification) {
    if (auto& f = callbacks(notification->pDevice->pUserData)->notification) call(*f, notification);
}

void onStop(ma_device* device) {
    if (auto& f = callbacks(device->pUserData)->stop) call(*f, device);
}

DeviceCallbacks& callbacks(ma_device_config* config) {
    if (!config->pUserData) config->pUserData = new DeviceCallbacks();
    return *callbacks(config->pUserData);
}

template <class Init>
ma_result initDevice(const KotoMa_device_config* pConfig, Init init) {
    return initWith<DeviceCallbacks>(koto::cpp(pConfig), &ma_device_config::pUserData, init);
}

// ma_sound: the audio thread may be in a callback set_end_callback replaces, so each keeps the ones it replaced until
// uninit.

struct EndCallback {
    std::optional<Callback> function;
    EndCallback* replaced;
};

void onEnd(void* user, ma_sound* sound) {
    if (auto& f = static_cast<EndCallback*>(user)->function) call(*f, sound);
}

EndCallback* ours(ma_sound_end_proc proc, void* user) { return proc == onEnd ? static_cast<EndCallback*>(user) : nullptr; }

void release(EndCallback* e) {
    while (e) delete std::exchange(e, e->replaced);
}

ma_bool32 onDevice(ma_context* context, ma_device_type type, const ma_device_info* info, void* user) {
    return call(*static_cast<Callback*>(user), context, type, info) != 0 ? MA_TRUE : MA_FALSE;
}

// ma_engine

struct EngineCallbacks {
    std::optional<Callback> process, data, notification;
};

EngineCallbacks* engineCallbacks(void* user) { return static_cast<EngineCallbacks*>(user); }
EngineCallbacks* engineCallbacks(ma_device* device) { return engineCallbacks(static_cast<ma_engine*>(device->pUserData)->pProcessUserData); }

EngineCallbacks& engineCallbacks(ma_engine_config* config) {
    if (!config->pProcessUserData) config->pProcessUserData = new EngineCallbacks();
    return *engineCallbacks(config->pProcessUserData);
}

void onEngineProcess(void* user, float* frames, ma_uint64 frameCount) {
    if (auto& f = engineCallbacks(user)->process) call(*f, frames, frameCount);
}

void onEngineData(ma_device* device, void* output, const void* input, ma_uint32 frameCount) {
    if (auto& f = engineCallbacks(device)->data) call(*f, device, output, input, frameCount);
}

void onEngineNotification(const ma_device_notification* notification) {
    if (auto& f = engineCallbacks(notification->pDevice)->notification) call(*f, notification);
}

// ma_decoder, ma_encoder: a read or write, which says how many bytes it moved through a slot of its own, and a seek;
// or a decoder's bytes. Only koto's decoders have pUserData.

struct Io {
    std::optional<Callback> transfer, seek;
    std::vector<uint8_t> bytes;
};

template <class Codec>
Io* io(Codec* codec) { return static_cast<Io*>(codec->pUserData); }

template <class Codec, class Buffer>
ma_result onTransfer(Codec* codec, Buffer buffer, size_t bytes, size_t* pMoved) {
    uint64_t moved = 0;
    ma_result result = resultOf(call(*io(codec)->transfer, codec, buffer, bytes, &moved));
    if (pMoved) *pMoved = static_cast<size_t>(moved);
    return result;
}

template <class Codec>
ma_result onSeek(Codec* codec, ma_int64 offset, ma_seek_origin origin) {
    return resultOf(call(*io(codec)->seek, codec, offset, origin));
}

template <class Init>
ma_result initIo(KotoUpcall transfer, void* transferUser, KotoUpcall seek, void* seekUser, Init init) {
    Io* own = new Io{function(transfer, transferUser), function(seek, seekUser), {}};
    ma_result result = own->transfer ? init(own, own->seek.has_value()) : MA_INVALID_ARGS;
    if (result != MA_SUCCESS) delete own;
    return result;
}

// ma_resource_manager's registered bytes

std::mutex registeredLock;
std::map<ma_resource_manager*, std::map<std::string, std::vector<uint8_t>>> registered;

// The copy of [data, data + size) registered under name, made the first time; what register does with it, or the copy's
// gone with a failed one.
template <class Register>
ma_result registerCopy(ma_resource_manager* manager, const char* name, const void* data, size_t size, Register reg) {
    if (!manager || !name || !data) return MA_INVALID_ARGS;
    std::lock_guard<std::mutex> lock(registeredLock);
    // Registered again, miniaudio counts it and keeps the first bytes.
    auto [it, added] = registered[manager].try_emplace(name);
    if (added) it->second.assign(static_cast<const uint8_t*>(data), static_cast<const uint8_t*>(data) + size);
    ma_result result = reg(it->second.data());
    if (result != MA_SUCCESS && added) registered[manager].erase(it);
    return result;
}

void unregisterAll(ma_resource_manager* manager) {
    std::map<std::string, std::vector<uint8_t>> gone;
    std::lock_guard<std::mutex> lock(registeredLock);
    if (auto it = registered.find(manager); it != registered.end()) {
        gone = std::move(it->second);
        registered.erase(it);
    }
}

// ma_data_source's next

std::mutex nextLock;
std::map<ma_data_source*, Callback> nexts;

ma_data_source* nextOf(ma_data_source* source) {
    std::optional<Callback> f;
    {
        std::lock_guard<std::mutex> lock(nextLock);
        if (auto it = nexts.find(source); it != nexts.end()) f = it->second;
    }
    return f ? reinterpret_cast<ma_data_source*>(static_cast<uintptr_t>(call(*f, source))) : nullptr;
}

void forgetNext(void* source) {
    std::optional<Callback> gone;  // dropped, into Kotlin, once the lock is
    std::lock_guard<std::mutex> lock(nextLock);
    if (auto it = nexts.find(static_cast<ma_data_source*>(source)); it != nexts.end()) {
        gone = std::move(it->second);
        nexts.erase(it);
    }
}

// Uninit, then forget the data source's next.
template <class Uninit>
void uninitSource(void* source, Uninit uninit) {
    uninit();
    forgetNext(source);
}

} // namespace

extern "C" {

void KotoMa_device_config_SetDataCallback(KotoMa_device_config* self, KotoUpcall value, void* valueUser) {
    callbacks(koto::cpp(self)).data = function(value, valueUser);
    koto::cpp(self)->dataCallback = valueUser ? onData : nullptr;
}

void KotoMa_device_config_SetNotificationCallback(KotoMa_device_config* self, KotoUpcall value, void* valueUser) {
    callbacks(koto::cpp(self)).notification = function(value, valueUser);
    koto::cpp(self)->notificationCallback = valueUser ? onNotification : nullptr;
}

void KotoMa_device_config_SetStopCallback(KotoMa_device_config* self, KotoUpcall value, void* valueUser) {
    callbacks(koto::cpp(self)).stop = function(value, valueUser);
    koto::cpp(self)->stopCallback = valueUser ? onStop : nullptr;
}

void KotoMa_device_config_destroy(KotoMa_device_config* self) {
    koto::forget(koto::cpp(self), sizeof(ma_device_config));
    delete callbacks(koto::cpp(self)->pUserData);
    delete koto::cpp(self);
}

KotoMa_result Koto_ma_device_init(KotoMa_context* pContext, const KotoMa_device_config* pConfig, KotoMa_device* pDevice) {
    return static_cast<KotoMa_result>(initDevice(pConfig, [&](const ma_device_config* c) { return ::ma_device_init(koto::cpp(pContext), c, koto::cpp(pDevice)); }));
}

KotoMa_result Koto_ma_device_init_ex(const KotoMa_backend* backends, uint32_t backendCount, const KotoMa_context_config* pContextConfig, const KotoMa_device_config* pConfig, KotoMa_device* pDevice) {
    return static_cast<KotoMa_result>(initDevice(pConfig, [&](const ma_device_config* c) {
        return ::ma_device_init_ex(reinterpret_cast<const ma_backend*>(backends), backendCount, koto::cpp(pContextConfig), c, koto::cpp(pDevice));
    }));
}

void Koto_ma_device_uninit(KotoMa_device* pDevice) {
    DeviceCallbacks* own = callbacks(koto::cpp(pDevice)->pUserData);
    ::ma_device_uninit(koto::cpp(pDevice));
    delete own;
}

void KotoMa_sound_config_SetEndCallback(KotoMa_sound_config* self, KotoUpcall value, void* valueUser) {
    ma_sound_config* config = koto::cpp(self);
    release(ours(config->endCallback, config->pEndCallbackUserData));
    config->endCallback = valueUser ? onEnd : nullptr;
    config->pEndCallbackUserData = valueUser ? new EndCallback{function(value, valueUser), nullptr} : nullptr;
}

void KotoMa_sound_config_destroy(KotoMa_sound_config* self) {
    koto::forget(koto::cpp(self), sizeof(ma_sound_config));
    release(ours(koto::cpp(self)->endCallback, koto::cpp(self)->pEndCallbackUserData));
    delete koto::cpp(self);
}

KotoMa_result Koto_ma_sound_init_ex(KotoMa_engine* pEngine, const KotoMa_sound_config* pConfig, KotoMa_sound* pSound) {
    if (!pConfig) return static_cast<KotoMa_result>(::ma_sound_init_ex(koto::cpp(pEngine), nullptr, koto::cpp(pSound)));
    ma_sound_config config = *koto::cpp(pConfig);
    if (EndCallback* e = ours(config.endCallback, config.pEndCallbackUserData)) config.pEndCallbackUserData = new EndCallback{e->function, nullptr};
    ma_result result = ::ma_sound_init_ex(koto::cpp(pEngine), &config, koto::cpp(pSound));
    if (result != MA_SUCCESS) release(ours(config.endCallback, config.pEndCallbackUserData));
    return static_cast<KotoMa_result>(result);
}

KotoMa_result Koto_ma_sound_set_end_callback(KotoMa_sound* pSound, KotoUpcall callback, void* callbackUser) {
    ma_sound* sound = koto::cpp(pSound);
    EndCallback* replaced = ours(sound->endCallback, sound->pEndCallbackUserData);
    if (!callbackUser && !replaced) return static_cast<KotoMa_result>(::ma_sound_set_end_callback(sound, nullptr, nullptr));
    return static_cast<KotoMa_result>(::ma_sound_set_end_callback(sound, onEnd, new EndCallback{function(callback, callbackUser), replaced}));
}

void Koto_ma_sound_uninit(KotoMa_sound* pSound) {
    ma_sound* sound = koto::cpp(pSound);
    EndCallback* own = ours(sound->endCallback, sound->pEndCallbackUserData);
    ma_data_source* made = sound->ownsDataSource ? sound->pDataSource : nullptr;
    ::ma_sound_uninit(sound);
    release(own);
    if (made) forgetNext(made);
}

KotoMa_result Koto_ma_context_enumerate_devices(KotoMa_context* pContext, KotoUpcall callback, void* callbackUser) {
    if (!callbackUser) return static_cast<KotoMa_result>(MA_INVALID_ARGS);
    Callback f(callback, callbackUser);
    return static_cast<KotoMa_result>(::ma_context_enumerate_devices(koto::cpp(pContext), onDevice, &f));
}

void KotoMa_engine_config_SetOnProcess(KotoMa_engine_config* self, KotoUpcall value, void* valueUser) {
    engineCallbacks(koto::cpp(self)).process = function(value, valueUser);
    koto::cpp(self)->onProcess = valueUser ? onEngineProcess : nullptr;
}

void KotoMa_engine_config_SetDataCallback(KotoMa_engine_config* self, KotoUpcall value, void* valueUser) {
    engineCallbacks(koto::cpp(self)).data = function(value, valueUser);
    koto::cpp(self)->dataCallback = valueUser ? onEngineData : nullptr;
}

void KotoMa_engine_config_SetNotificationCallback(KotoMa_engine_config* self, KotoUpcall value, void* valueUser) {
    engineCallbacks(koto::cpp(self)).notification = function(value, valueUser);
    koto::cpp(self)->notificationCallback = valueUser ? onEngineNotification : nullptr;
}

void KotoMa_engine_config_destroy(KotoMa_engine_config* self) {
    delete engineCallbacks(koto::cpp(self)->pProcessUserData);
    delete koto::cpp(self);
}

KotoMa_result Koto_ma_engine_init(const KotoMa_engine_config* pConfig, KotoMa_engine* pEngine) {
    return static_cast<KotoMa_result>(initWith<EngineCallbacks>(koto::cpp(pConfig), &ma_engine_config::pProcessUserData,
        [&](const ma_engine_config* c) { return ::ma_engine_init(c, koto::cpp(pEngine)); }));
}

// With the resource manager it made, and that one's registered bytes.
void Koto_ma_engine_uninit(KotoMa_engine* pEngine) {
    ma_engine* engine = koto::cpp(pEngine);
    EngineCallbacks* own = engineCallbacks(engine->pProcessUserData);
    ma_resource_manager* manager = engine->ownsResourceManager ? engine->pResourceManager : nullptr;
    ::ma_engine_uninit(engine);
    delete own;
    if (manager) unregisterAll(manager);
}

KotoMa_result Koto_ma_resource_manager_register_encoded_data(KotoMa_resource_manager* pResourceManager, const char* pName, const void* pData, uint32_t pDataCount) {
    ma_resource_manager* manager = koto::cpp(pResourceManager);
    return static_cast<KotoMa_result>(registerCopy(manager, pName, pData, pDataCount, [&](const uint8_t* copy) {
        return ::ma_resource_manager_register_encoded_data(manager, pName, copy, pDataCount);
    }));
}

KotoMa_result Koto_ma_resource_manager_register_decoded_data(KotoMa_resource_manager* pResourceManager, const char* pName, const void* pData, uint64_t frameCount, KotoMa_format format, uint32_t channels, uint32_t sampleRate) {
    ma_resource_manager* manager = koto::cpp(pResourceManager);
    auto f = static_cast<ma_format>(format);
    size_t size = static_cast<size_t>(frameCount * ma_get_bytes_per_frame(f, channels));
    return static_cast<KotoMa_result>(registerCopy(manager, pName, pData, size, [&](const uint8_t* copy) {
        return ::ma_resource_manager_register_decoded_data(manager, pName, copy, frameCount, f, channels, sampleRate);
    }));
}

void Koto_ma_resource_manager_uninit(KotoMa_resource_manager* pResourceManager) {
    ::ma_resource_manager_uninit(koto::cpp(pResourceManager));
    unregisterAll(koto::cpp(pResourceManager));
}

KotoMa_result Koto_ma_decoder_init(KotoUpcall onRead, void* onReadUser, KotoUpcall onSeek, void* onSeekUser, const KotoMa_decoder_config* pConfig, KotoMa_decoder* pDecoder) {
    return static_cast<KotoMa_result>(initIo(onRead, onReadUser, onSeek, onSeekUser, [&](Io* own, bool seeks) {
        return ::ma_decoder_init(onTransfer<ma_decoder, void*>, seeks ? ::onSeek<ma_decoder> : nullptr, own, koto::cpp(pConfig), koto::cpp(pDecoder));
    }));
}

KotoMa_result Koto_ma_decoder_init_memory(const void* pData, uint32_t pDataCount, const KotoMa_decoder_config* pConfig, KotoMa_decoder* pDecoder) {
    auto bytes = static_cast<const uint8_t*>(pData);
    Io* own = new Io{std::nullopt, std::nullopt, std::vector<uint8_t>(bytes, bytes + (bytes ? pDataCount : 0))};
    ma_result result = ::ma_decoder_init_memory(own->bytes.data(), own->bytes.size(), koto::cpp(pConfig), koto::cpp(pDecoder));
    if (result == MA_SUCCESS) koto::cpp(pDecoder)->pUserData = own;
    else delete own;
    return static_cast<KotoMa_result>(result);
}

KotoMa_result Koto_ma_decoder_uninit(KotoMa_decoder* pDecoder) {
    ma_decoder* decoder = koto::cpp(pDecoder);
    Io* own = io(decoder);
    ma_result result = ::ma_decoder_uninit(decoder);
    delete own;
    forgetNext(decoder);
    return static_cast<KotoMa_result>(result);
}

KotoMa_result Koto_ma_encoder_init(KotoUpcall onWrite, void* onWriteUser, KotoUpcall onSeek, void* onSeekUser, const KotoMa_encoder_config* pConfig, KotoMa_encoder* pEncoder) {
    return static_cast<KotoMa_result>(initIo(onWrite, onWriteUser, onSeek, onSeekUser, [&](Io* own, bool seeks) {
        return ::ma_encoder_init(onTransfer<ma_encoder, const void*>, seeks ? ::onSeek<ma_encoder> : nullptr, own, koto::cpp(pConfig), koto::cpp(pEncoder));
    }));
}

// Its last write, the file's header, goes through the I/O.
void Koto_ma_encoder_uninit(KotoMa_encoder* pEncoder) {
    ma_encoder* encoder = koto::cpp(pEncoder);
    Io* own = encoder->onWrite == onTransfer<ma_encoder, const void*> ? io(encoder) : nullptr;
    ::ma_encoder_uninit(encoder);
    delete own;
}

KotoMa_result Koto_ma_data_source_set_next_callback(void* pDataSource, KotoUpcall onGetNext, void* onGetNextUser) {
    auto* source = static_cast<ma_data_source*>(pDataSource);
    std::optional<Callback> replaced;  // dropped, into Kotlin, once the lock is
    {
        std::lock_guard<std::mutex> lock(nextLock);
        if (auto it = nexts.find(source); it != nexts.end()) {
            replaced = std::move(it->second);
            nexts.erase(it);
        }
        if (onGetNextUser) nexts.emplace(source, Callback(onGetNext, onGetNextUser));
    }
    return static_cast<KotoMa_result>(::ma_data_source_set_next_callback(source, onGetNextUser ? nextOf : nullptr));
}

void Koto_ma_data_source_uninit(void* pDataSource) {
    uninitSource(pDataSource, [&] { ::ma_data_source_uninit(pDataSource); });
}

void Koto_ma_audio_buffer_uninit(KotoMa_audio_buffer* pAudioBuffer) {
    uninitSource(pAudioBuffer, [&] { ::ma_audio_buffer_uninit(koto::cpp(pAudioBuffer)); });
}

void Koto_ma_audio_buffer_uninit_and_free(KotoMa_audio_buffer* pAudioBuffer) {
    uninitSource(pAudioBuffer, [&] { ::ma_audio_buffer_uninit_and_free(koto::cpp(pAudioBuffer)); });
}

void Koto_ma_waveform_uninit(KotoMa_waveform* pWaveform) {
    uninitSource(pWaveform, [&] { ::ma_waveform_uninit(koto::cpp(pWaveform)); });
}

void Koto_ma_pulsewave_uninit(KotoMa_pulsewave* pWaveform) {
    uninitSource(pWaveform, [&] { ::ma_pulsewave_uninit(koto::cpp(pWaveform)); });
}

void Koto_ma_noise_uninit(KotoMa_noise* pNoise, const KotoMa_allocation_callbacks* pAllocationCallbacks) {
    uninitSource(pNoise, [&] { ::ma_noise_uninit(koto::cpp(pNoise), koto::cpp(pAllocationCallbacks)); });
}

} // extern "C"
