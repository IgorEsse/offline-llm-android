cmake_minimum_required(VERSION 3.22.1)
project(offline_llm)

set(CMAKE_CXX_STANDARD 17)

add_library(offline_llm SHARED
    llama_jni.cpp
)

set(LLAMA_ROOT ${CMAKE_SOURCE_DIR}/../../../../third_party/llama.cpp)

add_subdirectory(${LLAMA_ROOT} ${CMAKE_BINARY_DIR}/llama)

target_include_directories(offline_llm PRIVATE ${LLAMA_ROOT}/include)

target_link_libraries(
    offline_llm
    llama
    android
    log
)
