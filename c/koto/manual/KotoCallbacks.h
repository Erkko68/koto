// miniaudio's callbacks, which Kotlin passes as an Upcall and its user data (Bridge.hpp's Callback): the trampolines
// calling them, and the functions keeping that user data where miniaudio hands it back for as long as it may call.
// Each keeps the signature generateCApi would give it.
#ifndef KOTO_MANUAL_KOTOCALLBACKS_H
#define KOTO_MANUAL_KOTOCALLBACKS_H

#include "../generated/Types.h"

#ifdef __cplusplus
extern "C" {
#endif

// A device's callbacks live in its pUserData: the config's until the device copies them, the device's until uninit.
void KotoMa_device_config_SetDataCallback(KotoMa_device_config* self, KotoUpcall value, void* valueUser);
void KotoMa_device_config_SetNotificationCallback(KotoMa_device_config* self, KotoUpcall value, void* valueUser);
void KotoMa_device_config_SetStopCallback(KotoMa_device_config* self, KotoUpcall value, void* valueUser);
void KotoMa_device_config_destroy(KotoMa_device_config* self);
KotoMa_result Koto_ma_device_init(KotoMa_context* pContext, const KotoMa_device_config* pConfig, KotoMa_device* pDevice);
KotoMa_result Koto_ma_device_init_ex(const KotoMa_backend* backends, uint32_t backendCount, const KotoMa_context_config* pContextConfig, const KotoMa_device_config* pConfig, KotoMa_device* pDevice);
void Koto_ma_device_uninit(KotoMa_device* pDevice);

// A sound's end callback lives in its pEndCallbackUserData, as the config's does.
void KotoMa_sound_config_SetEndCallback(KotoMa_sound_config* self, KotoUpcall value, void* valueUser);
void KotoMa_sound_config_destroy(KotoMa_sound_config* self);
KotoMa_result Koto_ma_sound_init_ex(KotoMa_engine* pEngine, const KotoMa_sound_config* pConfig, KotoMa_sound* pSound);
KotoMa_result Koto_ma_sound_set_end_callback(KotoMa_sound* pSound, KotoUpcall callback, void* callbackUser);
void Koto_ma_sound_uninit(KotoMa_sound* pSound);

// Called before it returns.
KotoMa_result Koto_ma_context_enumerate_devices(KotoMa_context* pContext, KotoUpcall callback, void* callbackUser);

// An engine's callbacks live in its pProcessUserData, as a device's do in its pUserData. The device it makes has the
// engine for its pUserData, so that device's callbacks find them through the engine.
void KotoMa_engine_config_SetOnProcess(KotoMa_engine_config* self, KotoUpcall value, void* valueUser);
void KotoMa_engine_config_SetDataCallback(KotoMa_engine_config* self, KotoUpcall value, void* valueUser);
void KotoMa_engine_config_SetNotificationCallback(KotoMa_engine_config* self, KotoUpcall value, void* valueUser);
void KotoMa_engine_config_destroy(KotoMa_engine_config* self);
KotoMa_result Koto_ma_engine_init(const KotoMa_engine_config* pConfig, KotoMa_engine* pEngine);
void Koto_ma_engine_uninit(KotoMa_engine* pEngine);

// A decoder's or encoder's I/O lives in its pUserData until uninit, as a decoder's copy of the bytes it decodes does.
KotoMa_result Koto_ma_decoder_init(KotoUpcall onRead, void* onReadUser, KotoUpcall onSeek, void* onSeekUser, const KotoMa_decoder_config* pConfig, KotoMa_decoder* pDecoder);
KotoMa_result Koto_ma_decoder_init_memory(const void* pData, uint32_t pDataCount, const KotoMa_decoder_config* pConfig, KotoMa_decoder* pDecoder);
KotoMa_result Koto_ma_decoder_uninit(KotoMa_decoder* pDecoder);
KotoMa_result Koto_ma_encoder_init(KotoUpcall onWrite, void* onWriteUser, KotoUpcall onSeek, void* onSeekUser, const KotoMa_encoder_config* pConfig, KotoMa_encoder* pEncoder);
void Koto_ma_encoder_uninit(KotoMa_encoder* pEncoder);

// miniaudio reads registered data where it's given: the resource manager keeps a copy, by name, until uninit. A
// sound may still be reading it after unregister.
KotoMa_result Koto_ma_resource_manager_register_encoded_data(KotoMa_resource_manager* pResourceManager, const char* pName, const void* pData, uint32_t pDataCount);
KotoMa_result Koto_ma_resource_manager_register_decoded_data(KotoMa_resource_manager* pResourceManager, const char* pName, const void* pData, uint64_t frameCount, KotoMa_format format, uint32_t channels, uint32_t sampleRate);
void Koto_ma_resource_manager_uninit(KotoMa_resource_manager* pResourceManager);

// miniaudio passes this one no user data: it's kept by the data source's address until it's set again, or the data
// source goes (these uninits, the decoder's, and a sound's of the data source it made).
KotoMa_result Koto_ma_data_source_set_next_callback(void* pDataSource, KotoUpcall onGetNext, void* onGetNextUser);
void Koto_ma_data_source_uninit(void* pDataSource);
void Koto_ma_audio_buffer_uninit(KotoMa_audio_buffer* pAudioBuffer);
void Koto_ma_audio_buffer_uninit_and_free(KotoMa_audio_buffer* pAudioBuffer);
void Koto_ma_waveform_uninit(KotoMa_waveform* pWaveform);
void Koto_ma_pulsewave_uninit(KotoMa_pulsewave* pWaveform);
void Koto_ma_noise_uninit(KotoMa_noise* pNoise, const KotoMa_allocation_callbacks* pAllocationCallbacks);

#ifdef __cplusplus
}
#endif

#endif
