cmake_minimum_required(VERSION 3.22.1)

project("lib-rtmp")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DNO_CRYPTO")

include_directories(librtmp)

aux_source_directory(./librtmp SOURCE)

add_library(rtmp SHARED
        ${SOURCE}
        librtmp.c)

target_link_libraries(rtmp
        android
        log)