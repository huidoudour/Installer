# CMake 最低版本要求
cmake_minimum_required(VERSION 3.22.1)

# 项目名称
project("installer-native")

# === 16KB 页面对齐配置 ===
# 确保原生库支持 Android 15+ 的 16KB 页面大小设备
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,common-page-size=16384")

message(STATUS "=== 16KB 页面对齐配置 ===")
message(STATUS "已设置链接器标志: -Wl,-z,max-page-size=16384")
message(STATUS "已设置链接器标志: -Wl,-z,common-page-size=16384")
message(STATUS "目标架构: ${ANDROID_ABI}")

# ============================================================
# crash-handler（独立信号处理器）
# ============================================================
add_library(
    crash-handler
    SHARED
    crash_handler.cpp
)

find_library(log-lib log)
target_link_libraries(crash-handler ${log-lib})

target_compile_options(crash-handler PRIVATE
    -Wall -Wextra -O2 -fvisibility=hidden
)

set_target_properties(crash-handler PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

message(STATUS "crash-handler (独立信号处理器) ")
#
# message(STATUS "installer-native")
message(STATUS "=== 构建配置 ===")
message(STATUS "C++ 标准: C++17")
message(STATUS "优化级别: O2")
message(STATUS "链接器标志: ${CMAKE_SHARED_LINKER_FLAGS}")
message(STATUS "==================")
