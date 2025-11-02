# CMake配置文件 - 用于编译C++共享库
cmake_minimum_required(VERSION 3.22.1)

project("installer-native")

# === 启用 16KB 页面对齐支持 ===
# 这确保原生库在 16KB 页面大小的设备上正常运行 (Android 15+)
if(ANDROID)
    # 设置最大页面大小为 16KB
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
endif()

# 添加源文件
add_library(
    # 库名称
    installer-native
    
    # 库类型（共享库 .so）
    SHARED
    
    # 源文件
    native-lib.cpp
)

# 查找log库（用于日志输出）
find_library(
    log-lib
    log
)

# 链接库
target_link_libraries(
    installer-native
    
    # 链接log库
    ${log-lib}
)
