#pragma once

#if defined(__LIBRETRO__) && defined(HAVE_VULKAN)

#include <libretro.h>
#include <libretro_vulkan.h>

#ifdef __cplusplus
extern "C" {
#endif

const struct retro_hw_render_context_negotiation_interface_vulkan *
vulkan_libretro_get_negotiation_interface(void);

int vulkan_libretro_context_reset(retro_environment_t environment);
void vulkan_libretro_context_destroy(void);
void vulkan_libretro_set_image(void);

uint32_t vulkan_libretro_get_sync_index(void);
uint32_t vulkan_libretro_get_sync_image_count(void);
void vulkan_libretro_wait_sync_index(void);

VkResult vulkan_libretro_queue_submit(VkQueue queue, uint32_t submit_count,
      const VkSubmitInfo *submits, VkFence fence);
VkResult vulkan_libretro_queue_wait_idle(VkQueue queue);
VkResult vulkan_libretro_device_wait_idle(VkDevice device);

#ifdef __cplusplus
}
#endif

#endif
