cmake_minimum_required(VERSION 3.18)
project(auxcapture)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_library(auxcapture SHARED auxcapture.cpp)
find_library(log-lib log)
target_link_libraries(auxcapture ${log-lib} GLESv2)
