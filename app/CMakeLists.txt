cmake_minimum_required(VERSION 3.22.1)
project("shruti_native_engine" CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/include
)

# Native Engine Shared Library
add_library(
    shruti_native_engine
    SHARED
    src/main/cpp/src/shruti_ring_buffer.cpp
    src/main/cpp/src/shruti_jni_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    shruti_native_engine
    ${log-lib}
    ${android-lib}
)
