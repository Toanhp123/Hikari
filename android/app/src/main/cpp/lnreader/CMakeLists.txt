cmake_minimum_required(VERSION 3.22)
project(hikari_lnreader C)
include(FetchContent)
FetchContent_Declare(quickjs
  URL https://bellard.org/quickjs/quickjs-2025-09-13-2.tar.xz
  URL_HASH SHA256=996c6b5018fc955ad4d06426d0e9cb713685a00c825aa5c0418bd53f7df8b0b4)
FetchContent_GetProperties(quickjs)
if(NOT quickjs_POPULATED)
  FetchContent_Populate(quickjs)
endif()
add_library(hikari_quickjs STATIC
  ${quickjs_SOURCE_DIR}/quickjs.c ${quickjs_SOURCE_DIR}/dtoa.c
  ${quickjs_SOURCE_DIR}/libregexp.c ${quickjs_SOURCE_DIR}/libunicode.c
  ${quickjs_SOURCE_DIR}/cutils.c runtime.c)
target_include_directories(hikari_quickjs PUBLIC ${quickjs_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_definitions(hikari_quickjs PRIVATE _GNU_SOURCE CONFIG_VERSION="2025-09-13")
set_target_properties(hikari_quickjs PROPERTIES POSITION_INDEPENDENT_CODE ON C_VISIBILITY_PRESET hidden)
target_link_libraries(hikari_quickjs PUBLIC m)
add_library(hikari_lnreader SHARED jni.c)
target_link_libraries(hikari_lnreader hikari_quickjs)
add_executable(hikari_runtime_test runtime_test.c)
target_link_libraries(hikari_runtime_test hikari_quickjs)
