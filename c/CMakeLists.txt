cmake_minimum_required(VERSION 3.22)

# The Koto* C API over miniaudio, built by the cmakeBuild tasks: for iOS, libkoto-c.a into KOTO_OUTPUT_DIR for
# Kotlin/Native to link; with KOTO_BINDINGS_DIR, libkoto, the same as a JNI library, or with Emscripten koto.{js,wasm}.
project(koto-c C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
# The static library, for Kotlin/Native to link. The other output dirs hold only what's packaged: libkoto, koto.{js,wasm}.
if (IOS)
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${KOTO_OUTPUT_DIR}")
endif()
# The static C runtime on Windows: a DLL in a JVM can't count on the JVM's msvcp140.dll being its own.
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

# The generated API (koto/generated) plus the hand-written rest (koto/manual), over miniaudio's one implementation file.
set(miniaudio "${CMAKE_CURRENT_SOURCE_DIR}/../miniaudio")
file(GLOB_RECURSE sources CONFIGURE_DEPENDS koto/generated/*.cpp koto/manual/*.cpp)
add_library(koto-c STATIC ${sources} "${miniaudio}/miniaudio.c")
target_include_directories(koto-c PUBLIC "${miniaudio}")
# Its iOS backend talks to AVFoundation, which only Objective-C can.
if (IOS)
    enable_language(OBJC)
    set_source_files_properties("${miniaudio}/miniaudio.c" PROPERTIES LANGUAGE OBJC)
endif()
if (ANDROID)
    target_link_libraries(koto-c PUBLIC log m dl)
elseif (CMAKE_SYSTEM_NAME STREQUAL "Linux")
    find_package(Threads REQUIRED)
    target_link_libraries(koto-c PUBLIC Threads::Threads m dl)
endif()

# The web's image: the C API exporting what generateBindings lists, which koto/web/koto-globals.js installs as the
# globals the Kotlin externals bind.
if (EMSCRIPTEN)
    set(wasm "${KOTO_BINDINGS_DIR}/wasm")
    set(globals "${CMAKE_CURRENT_SOURCE_DIR}/koto/web/koto-globals.js")
    add_executable(koto-web $<TARGET_OBJECTS:koto-c>)
    set_target_properties(koto-web PROPERTIES OUTPUT_NAME koto RUNTIME_OUTPUT_DIRECTORY "${KOTO_OUTPUT_DIR}" LINKER_LANGUAGE CXX
        LINK_DEPENDS "${wasm}/koto-exports.txt;${wasm}/koto-types.js;${globals}")
    target_link_options(koto-web PRIVATE
        "-sMODULARIZE=1" "-sEXPORT_NAME=createKotoModule" "-sEXPORTED_FUNCTIONS=@${wasm}/koto-exports.txt"
        "-sEXPORTED_RUNTIME_METHODS=HEAP8,HEAP32,HEAPF32,HEAPF64,addFunction,UTF8ToString"
        "-sALLOW_MEMORY_GROWTH=1" "-sALLOW_TABLE_GROWTH=1" "-sSTACK_SIZE=1048576"
        "--post-js=${wasm}/koto-types.js" "--post-js=${globals}")
# The JVM's image: the C API, the JNI runtime (koto/jni) and the forwarders generateBindings writes.
elseif (KOTO_BINDINGS_DIR)
    file(GLOB forwarders CONFIGURE_DEPENDS "${KOTO_BINDINGS_DIR}/jni/*.c")
    add_library(koto-jni SHARED koto/jni/KotoJni.cpp ${forwarders})
    # RUNTIME is where Windows puts a DLL, and _RELEASE keeps Visual Studio from adding a Release/ to it.
    set_target_properties(koto-jni PROPERTIES OUTPUT_NAME koto LIBRARY_OUTPUT_DIRECTORY "${KOTO_OUTPUT_DIR}"
        RUNTIME_OUTPUT_DIRECTORY "${KOTO_OUTPUT_DIR}" RUNTIME_OUTPUT_DIRECTORY_RELEASE "${KOTO_OUTPUT_DIR}")
    target_link_libraries(koto-jni PRIVATE koto-c)
    # The forwarders include the C headers as `koto/generated/…`.
    target_include_directories(koto-jni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
    # Only the JNI entry points are exported, and the linker drops what they never reach.
    set(seal "${CMAKE_CURRENT_SOURCE_DIR}/koto/jni/koto-jni.map")
    if (ANDROID)
        # jni.h is the NDK's. 16 KB pages are required from Android 15.
        target_link_options(koto-jni PRIVATE "LINKER:--version-script=${seal}" "LINKER:-z,max-page-size=16384" "LINKER:--gc-sections" "LINKER:--strip-all")
        set_target_properties(koto-jni PROPERTIES LINK_DEPENDS "${seal}")
    else()
        # Linux: sealed as Android's, and with the C++ runtime inside, so it loads whatever libstdc++ the machine has.
        if (CMAKE_SYSTEM_NAME STREQUAL "Linux")
            target_link_options(koto-jni PRIVATE "LINKER:--version-script=${seal}" "LINKER:--gc-sections" "LINKER:--strip-all" -static-libstdc++ -static-libgcc)
            set_target_properties(koto-jni PROPERTIES LINK_DEPENDS "${seal}")
        endif()
        # jni.h is the JDK's running the build.
        if (APPLE)
            set(jni_os darwin)
        elseif (WIN32)
            set(jni_os win32)
        else()
            set(jni_os linux)
        endif()
        target_include_directories(koto-jni PRIVATE "${JNI_HOME}/include" "${JNI_HOME}/include/${jni_os}")
        if (APPLE)
            target_link_options(koto-jni PRIVATE "LINKER:-exported_symbol,_Java_*" "LINKER:-exported_symbol,_JNI_OnLoad" "LINKER:-dead_strip")
        endif()
    endif()
endif()
