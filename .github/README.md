# Inspections for FFM+

[![Build](https://github.com/club-doki7/ffm-plus-inspections/actions/workflows/build.yml/badge.svg)](https://github.com/club-doki7/ffm-plus-inspections/actions/workflows/build.yml)
[![JB Version](https://img.shields.io/jetbrains/plugin/v/27683)][jb]
[![JB Downloads](https://img.shields.io/jetbrains/plugin/d/27683)][jb]

jb: https://plugins.jetbrains.com/plugin/27683

This is created for the FFM+ module of [vulkan4j](https://github.com/club-doki7/vulkan4j).

# Recommended setup

+ Disable the built-in [`Magic constant`](https://www.jetbrains.com/help/inspectopedia/MagicConstant.html) inspection (unless you rely on the very old java bean feature).

# Implemented features

+ Support for `@Bitmask` and `@EnumType` annotations from FFM+, so far only works on primitive types (so, not including `IntPtr` and stuff), with interoperability with the intellij `@MagicConstant` annotation.

# Showcase

https://github.com/user-attachments/assets/ddb450bf-ff8c-4e9e-bbf3-bd26394e3556
