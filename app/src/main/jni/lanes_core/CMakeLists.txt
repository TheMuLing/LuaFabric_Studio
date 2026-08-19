cmake_minimum_required(VERSION 3.31.6)

file(GLOB LANES_SRC *.cpp)

list(FILTER LANES_SRC EXCLUDE REGEX ".*/_pch\\.cpp$")

add_library(lanes_core SHARED ${LANES_SRC})

target_link_libraries(lanes_core
        log
        lua
)

target_compile_features(lanes_core PRIVATE cxx_std_20)

target_compile_options(lanes_core PRIVATE
        -DANDROID
        -Wall
)