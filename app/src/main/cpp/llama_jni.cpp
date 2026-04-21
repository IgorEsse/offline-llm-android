#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>
#include <thread>
#include <atomic>
#include <vector>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "offline_llm", __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static std::atomic<bool> g_stop(false);
static std::thread g_thread;

static void release_model() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_initBackend(JNIEnv *, jobject) {
    llama_backend_init();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_loadModel(JNIEnv * env, jobject, jstring path, jint contextSize, jint threads) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_thread.joinable()) g_thread.join();
    release_model();

    const char* raw = env->GetStringUTFChars(path, nullptr);
    llama_model_params model_params = llama_model_default_params();
    g_model = llama_load_model_from_file(raw, model_params);
    env->ReleaseStringUTFChars(path, raw);
    if (!g_model) return JNI_FALSE;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    return g_ctx ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_unloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_thread.joinable()) g_thread.join();
    release_model();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_isModelLoaded(JNIEnv *, jobject) {
    return (g_model && g_ctx) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_stopGeneration(JNIEnv *, jobject) {
    g_stop.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_getModelMetadata(JNIEnv * env, jobject, jstring path) {
    const char* raw = env->GetStringUTFChars(path, nullptr);
    llama_model_params params = llama_model_default_params();
    llama_model * tmp = llama_load_model_from_file(raw, params);
    env->ReleaseStringUTFChars(path, raw);
    if (!tmp) return env->NewStringUTF("unknown;load_failed");
    const char * arch = llama_model_arch(tmp);
    std::string out = std::string(arch ? arch : "unknown") + ";metadata_available";
    llama_free_model(tmp);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_nativeGenerate(
    JNIEnv * env,
    jobject,
    jstring prompt,
    jfloat temperature,
    jint topK,
    jfloat topP,
    jint maxTokens,
    jobject callback
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_ctx || !g_model) return JNI_FALSE;
    if (g_thread.joinable()) g_thread.join();

    JavaVM* jvm;
    env->GetJavaVM(&jvm);
    jobject globalCb = env->NewGlobalRef(callback);
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    const char* promptRaw = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptRaw);
    env->ReleaseStringUTFChars(prompt, promptRaw);

    g_stop.store(false);
    g_thread = std::thread([=]() {
        JNIEnv* threadEnv = nullptr;
        jvm->AttachCurrentThread(&threadEnv, nullptr);

        auto emitError = [&](const char* msg) {
            jstring jMsg = threadEnv->NewStringUTF(msg);
            threadEnv->CallVoidMethod(globalCb, onError, jMsg);
            threadEnv->DeleteLocalRef(jMsg);
        };

        std::vector<llama_token> tokens(promptStr.size() + 8);
        int n_prompt = llama_tokenize(g_model, promptStr.c_str(), promptStr.size(), tokens.data(), tokens.size(), true, false);
        if (n_prompt < 0) {
            emitError("prompt tokenization failed");
            threadEnv->DeleteGlobalRef(globalCb);
            jvm->DetachCurrentThread();
            return;
        }
        tokens.resize(n_prompt);

        llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
        if (llama_decode(g_ctx, batch) != 0) {
            emitError("initial decode failed");
            threadEnv->DeleteGlobalRef(globalCb);
            jvm->DetachCurrentThread();
            return;
        }

        llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        for (int i = 0; i < maxTokens; ++i) {
            if (g_stop.load()) {
                emitError("generation canceled");
                break;
            }

            llama_token newToken = llama_sampler_sample(sampler, g_ctx, -1);
            if (llama_token_is_eog(g_model, newToken)) break;

            char piece[16 * 1024];
            int n = llama_token_to_piece(g_model, newToken, piece, sizeof(piece), 0, true);
            if (n > 0) {
                std::string tok(piece, n);
                jstring jTok = threadEnv->NewStringUTF(tok.c_str());
                threadEnv->CallVoidMethod(globalCb, onToken, jTok);
                threadEnv->DeleteLocalRef(jTok);
            }

            llama_batch next = llama_batch_get_one(&newToken, 1);
            if (llama_decode(g_ctx, next) != 0) {
                emitError("decode failed during generation");
                break;
            }
        }

        llama_sampler_free(sampler);
        threadEnv->CallVoidMethod(globalCb, onComplete);
        threadEnv->DeleteGlobalRef(globalCb);
        jvm->DetachCurrentThread();
    });

    return JNI_TRUE;
}
