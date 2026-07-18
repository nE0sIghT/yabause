# Private shader compiler dependencies

The Linux libretro core compiles the pinned shaderc, glslang, SPIRV-Tools,
and SPIRV-Headers submodules directly into the core. This prevents runtime
symbol interposition with a glslang copy embedded in the libretro frontend.

Only the source files required by the shaderc API and its performance
optimizer are selected by `Makefile.common`. Generated tables are checked in
under `shaderc-generated`, so the Makefile build does not need CMake or Python.

The submodule revisions match shaderc v2026.1 and its `DEPS` file.
