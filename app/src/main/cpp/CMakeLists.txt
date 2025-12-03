# CMake 最低版本要求
cmake_minimum_required(VERSION 3.22.1)

# 项目名称
project("installer-native")

# === 16KB 页面对齐配置 ===
# 确保原生库支持 Android 15+ 的 16KB 页面大小设备

# 设置链接器标志: 强制 16KB 对齐
# -Wl,-z,max-page-size=16384: ELF 段对齐到 16KB
# -Wl,-z,common-page-size=16384: 通用页面大小为 16KB
set(CMAKE_SHARED_LINKER_FLAGS 
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS 
    "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,common-page-size=16384")

# 输出对齐配置信息
message(STATUS "=== 16KB 页面对齐配置 ===")
message(STATUS "已设置链接器标志: -Wl,-z,max-page-size=16384")
message(STATUS "已设置链接器标志: -Wl,-z,common-page-size=16384")
message(STATUS "目标架构: ${ANDROID_ABI}")

# 创建共享库
add_library(
    # 库名称
    installer-native
    
    # 共享库类型
    SHARED
    
    # 源文件
    native-lib.cpp
)

# 查找 Android 日志库
find_library(
    # 输出变量名
    log-lib
    
    # NDK 库名
    log
)

# 链接库
target_link_libraries(
    # 目标库
    installer-native
    
    # Android 日志库
    ${log-lib}
)

# 编译选项
target_compile_options(installer-native PRIVATE
    -Wall                    # 启用所有警告
    -Wextra                  # 额外警告
    -O2                      # 优化级别2
    -fvisibility=hidden      # 隐藏符号,减小库体积
)

# C++ 标准
set_target_properties(installer-native PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# 验证配置
message(STATUS "=== 构建配置 ===")
message(STATUS "库名: installer-native")
message(STATUS "C++ 标准: C++17")
message(STATUS "优化级别: O2")
message(STATUS "链接器标志: ${CMAKE_SHARED_LINKER_FLAGS}")
message(STATUS "==================")
