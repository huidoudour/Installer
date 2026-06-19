# CMakeLists.txt for TermuxBridge native library
#
# 编译 libtermux_bridge.so - 基于 Termux 的 libtermux.so 实现的 PTY 子进程管理库
# 参考: https://github.com/termux/termux-app/wiki/Termux-Libraries
#
cmake_minimum_required(VERSION 3.22.1)
project("termux_bridge")

# 设置 C 标准
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# 16KB 页面大小支持 (Android 15+, API 35+)
# 确保 .so 在 16KB 页面大小设备上可正常加载
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# 64位架构额外对齐到 16KB
set(CMAKE_SHARED_LINKER_FLAGS_ARM64 "${CMAKE_SHARED_LINKER_FLAGS_ARM64} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS_X86_64 "${CMAKE_SHARED_LINKER_FLAGS_X86_64} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# 创建共享库
add_library(
    termux_bridge
    SHARED
    termux_bridge.c
)

# 链接必要的系统库
target_link_libraries(
    termux_bridge
    log
    c
)

# 设置目标属性
target_compile_options(
    termux_bridge
    PRIVATE
    -Wall
    -Wextra
    -Werror=implicit-function-declaration
)
