#include <jni.h>
#include <android/log.h>
#include <string>
#include <mutex>
#include <thread>
#include <atomic>
#include <vector>
#include <cstring>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "offline_llm", __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static const llama_vocab * g_vocab = nullptr;
static std::atomic<bool> g_stop(false);
static std::thread g_thread;

static void release_model() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    g_vocab = nullptr;
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_initBackend(JNIEnv *, jobject) {
    llama_backend_init();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_loadModel(
    JNIEnv * env,
    jobject,
    jstring path,
    jint contextSize,
    jint threads
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_thread.joinable()) {
        g_thread.join();
    }

    release_model();

    const char * raw = env->GetStringUTFChars(path, nullptr);

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(raw, model_params);

    env->ReleaseStringUTFChars(path, raw);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        LOGE("Failed to get model vocab");
        release_model();
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(contextSize);
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        release_model();
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_unloadModel(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);

    g_stop.store(true);
    if (g_thread.joinable()) {
        g_thread.join();
    }

    release_model();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_isModelLoaded(JNIEnv *, jobject) {
    return (g_model && g_ctx && g_vocab) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_stopGeneration(JNIEnv *, jobject) {
    g_stop.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_getModelMetadata(
    JNIEnv * env,
    jobject,
    jstring path
) {
    const char * raw = env->GetStringUTFChars(path, nullptr);

    llama_model_params params = llama_model_default_params();
    llama_model * tmp = llama_model_load_from_file(raw, params);

    env->ReleaseStringUTFChars(path, raw);

    if (!tmp) {
        return env->NewStringUTF("unknown;load_failed");
    }

    char arch_buf[128] = {0};
    int meta_res = llama_model_meta_val_str(tmp, "general.architecture", arch_buf, sizeof(arch_buf));

    if (meta_res < 0 || arch_buf[0] == '\0') {
        std::memset(arch_buf, 0, sizeof(arch_buf));
        if (llama_model_desc(tmp, arch_buf, sizeof(arch_buf)) < 0 || arch_buf[0] == '\0') {
            std::strncpy(arch_buf, "unknown", sizeof(arch_buf) - 1);
        }
    }

    std::string out = std::string(arch_buf) + ";metadata_available";
    llama_model_free(tmp);

    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_offlinellm_nativebridge_LlamaNativeBridge_countTokens(
    JNIEnv * env,
    jobject,
    jstring text
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_vocab || !text) {
        return -1;
    }

    const char * raw = env->GetStringUTFChars(text, nullptr);
    std::string input(raw ? raw : "");
    env->ReleaseStringUTFChars(text, raw);
    if (input.empty()) {
        return 0;
    }

    std::vector<llama_token> tokens(input.size() + 8);
    const int32_t n = llama_tokenize(
        g_vocab,
        input.c_str(),
        static_cast<int32_t>(input.size()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,
        false
    );
    return n >= 0 ? n : -1;
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

    if (!g_ctx || !g_model || !g_vocab) {
        return JNI_FALSE;
    }

    if (g_thread.joinable()) {
        g_thread.join();
    }

    JavaVM * jvm = nullptr;
    env->GetJavaVM(&jvm);

    jobject globalCb = env->NewGlobalRef(callback);
    jclass cbClass = env->GetObjectClass(callback);

    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    env->DeleteLocalRef(cbClass);

    if (!onToken || !onComplete || !onError) {
        env->DeleteGlobalRef(globalCb);
        return JNI_FALSE;
    }

    const char * promptRaw = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptRaw ? promptRaw : "");
    env->ReleaseStringUTFChars(prompt, promptRaw);

    g_stop.store(false);

    g_thread = std::thread([=]() {
        JNIEnv * threadEnv = nullptr;
        if (jvm->AttachCurrentThread(&threadEnv, nullptr) != JNI_OK) {
            return;
        }

        auto finishWithError = [&](const char * msg) {
            jstring jMsg = threadEnv->NewStringUTF(msg);
            threadEnv->CallVoidMethod(globalCb, onError, jMsg);
            threadEnv->DeleteLocalRef(jMsg);
            threadEnv->DeleteGlobalRef(globalCb);
            jvm->DetachCurrentThread();
        };

        std::vector<llama_token> tokens(promptStr.size() + 8);
        int32_t n_prompt = llama_tokenize(
            g_vocab,
            promptStr.c_str(),
            static_cast<int32_t>(promptStr.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            false
        );

        if (n_prompt < 0) {
            threadEnv->DeleteGlobalRef(globalCb);
            jvm->DetachCurrentThread();
            return;
        }

        tokens.resize(static_cast<size_t>(n_prompt));

        llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
        if (llama_decode(g_ctx, batch) != 0) {
            finishWithError("initial decode failed");
            return;
        }

        llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        if (!sampler) {
            finishWithError("sampler init failed");
            return;
        }

        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        bool hadError = false;

        for (int i = 0; i < maxTokens; ++i) {
            if (g_stop.load()) {
                break;
            }

            llama_token newToken = llama_sampler_sample(sampler, g_ctx, -1);

            if (llama_vocab_is_eog(g_vocab, newToken)) {
                break;
            }

            char piece[16 * 1024];
            int32_t n = llama_token_to_piece(
                g_vocab,
                newToken,
                piece,
                static_cast<int32_t>(sizeof(piece)),
                0,
                true
            );

            if (n < 0) {
                hadError = true;
                finishWithError("token to piece failed");
                break;
            }

            if (n > 0) {
                std::string tok(piece, static_cast<size_t>(n));
                jstring jTok = threadEnv->NewStringUTF(tok.c_str());
                threadEnv->CallVoidMethod(globalCb, onToken, jTok);
                threadEnv->DeleteLocalRef(jTok);
            }

            llama_batch next = llama_batch_get_one(&newToken, 1);
            if (llama_decode(g_ctx, next) != 0) {
                hadError = true;
                finishWithError("decode failed during generation");
                break;
            }
        }

        llama_sampler_free(sampler);

        if (!hadError) {
            threadEnv->CallVoidMethod(globalCb, onComplete);
            threadEnv->DeleteGlobalRef(globalCb);
            jvm->DetachCurrentThread();
        }
    });

    return JNI_TRUE;
}
