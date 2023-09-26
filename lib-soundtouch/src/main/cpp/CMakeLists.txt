cmake_minimum_required(VERSION 3.22.1)

project("lib-soundtouch")

# 基于 SoundTouch 2.3.2 源码 (https://www.surina.net/soundtouch/)
include_directories(include)
include_directories(SoundTouch)

aux_source_directory(./SoundTouch SOURCE)

add_library(soundtouch SHARED
        ${SOURCE}
        SoundTouchJNI.cpp)


target_link_libraries(soundtouch
    android
    log)
