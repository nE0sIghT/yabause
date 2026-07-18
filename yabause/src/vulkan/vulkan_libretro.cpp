/* Vulkan <-> libretro glue for VIDVulkan.
 *
 * RetroArch owns the instance and the device returned by the negotiation
 * callback. The renderer owns only its offscreen images and other rendering
 * resources. Final images are synchronized with the frontend swapchain index
 * and handed to RetroArch in SHADER_READ_ONLY_OPTIMAL layout.
 */

#include "vulkan_libretro.h"

#include "Renderer.h"
#include "VIDVulkan.h"
#include "Window.h"

#include <algorithm>
#include <cstring>
#include <vector>

static const struct retro_hw_render_interface_vulkan *g_vulkan_if = nullptr;
static Renderer *g_renderer = nullptr;
static bool g_tessellation_enabled = false;
static struct retro_vulkan_image g_current_image = {};

static const VkApplicationInfo *vulkan_libretro_get_application_info(void)
{
   static const VkApplicationInfo info = {
      VK_STRUCTURE_TYPE_APPLICATION_INFO,
      nullptr,
      "YabaSanshiro",
      VK_MAKE_VERSION(3, 4, 2),
      "YabaSanshiro",
      VK_MAKE_VERSION(3, 4, 2),
      VK_API_VERSION_1_1
   };
   return &info;
}

static bool vulkan_libretro_features_supported(
      const VkPhysicalDeviceFeatures &available,
      const VkPhysicalDeviceFeatures &requested)
{
   const VkBool32 *have = reinterpret_cast<const VkBool32 *>(&available);
   const VkBool32 *need = reinterpret_cast<const VkBool32 *>(&requested);
   const size_t count = sizeof(VkPhysicalDeviceFeatures) / sizeof(VkBool32);

   for (size_t i = 0; i < count; ++i)
      if (need[i] && !have[i])
         return false;
   return true;
}

static bool vulkan_libretro_find_queue(
      VkInstance instance, VkPhysicalDevice gpu, VkSurfaceKHR surface,
      PFN_vkGetInstanceProcAddr get_instance_proc_addr, uint32_t *queue_family)
{
   uint32_t count = 0;
   vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, nullptr);
   if (count == 0)
      return false;

   std::vector<VkQueueFamilyProperties> families(count);
   vkGetPhysicalDeviceQueueFamilyProperties(gpu, &count, families.data());

   PFN_vkGetPhysicalDeviceSurfaceSupportKHR get_surface_support = nullptr;
   if (surface != VK_NULL_HANDLE)
   {
      get_surface_support = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceSupportKHR>(
            get_instance_proc_addr(instance, "vkGetPhysicalDeviceSurfaceSupportKHR"));
      if (!get_surface_support)
         return false;
   }

   for (uint32_t i = 0; i < count; ++i)
   {
      const VkQueueFlags required = VK_QUEUE_GRAPHICS_BIT | VK_QUEUE_COMPUTE_BIT;
      if ((families[i].queueFlags & required) != required || families[i].queueCount == 0)
         continue;

      if (surface != VK_NULL_HANDLE)
      {
         VkBool32 present_supported = VK_FALSE;
         if (get_surface_support(gpu, i, surface, &present_supported) != VK_SUCCESS ||
               present_supported != VK_TRUE)
            continue;
      }

      *queue_family = i;
      return true;
   }
   return false;
}

static bool vulkan_libretro_create_device(
      struct retro_vulkan_context *context,
      VkInstance instance,
      VkPhysicalDevice requested_gpu,
      VkSurfaceKHR surface,
      PFN_vkGetInstanceProcAddr get_instance_proc_addr,
      const char **required_device_extensions,
      unsigned num_required_device_extensions,
      const char **required_device_layers,
      unsigned num_required_device_layers,
      const VkPhysicalDeviceFeatures *required_features)
{
   g_tessellation_enabled = false;

   if (!context || !get_instance_proc_addr)
      return false;

   std::vector<VkPhysicalDevice> devices;
   if (requested_gpu != VK_NULL_HANDLE)
      devices.push_back(requested_gpu);
   else
   {
      uint32_t count = 0;
      if (vkEnumeratePhysicalDevices(instance, &count, nullptr) != VK_SUCCESS || count == 0)
         return false;
      devices.resize(count);
      if (vkEnumeratePhysicalDevices(instance, &count, devices.data()) != VK_SUCCESS)
         return false;
   }

   VkPhysicalDevice gpu = VK_NULL_HANDLE;
   uint32_t queue_family = 0;
   for (VkPhysicalDevice candidate : devices)
   {
      if (vulkan_libretro_find_queue(instance, candidate, surface,
               get_instance_proc_addr, &queue_family))
      {
         gpu = candidate;
         break;
      }
   }
   if (gpu == VK_NULL_HANDLE)
      return false;

   VkPhysicalDeviceFeatures available = {};
   VkPhysicalDeviceFeatures enabled = {};
   vkGetPhysicalDeviceFeatures(gpu, &available);
   if (required_features)
   {
      if (!vulkan_libretro_features_supported(available, *required_features))
         return false;
      enabled = *required_features;
   }

   /* GPU tessellation is optional in the core. Enable it only when the full
    * tessellation path (including its geometry shader) is supported. */
   const bool tessellation_enabled = available.tessellationShader == VK_TRUE &&
                                     available.geometryShader == VK_TRUE;
   if (tessellation_enabled)
   {
      enabled.tessellationShader = VK_TRUE;
      enabled.geometryShader = VK_TRUE;
   }

   const float priority = 1.0f;
   VkDeviceQueueCreateInfo queue_info = {};
   queue_info.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
   queue_info.queueFamilyIndex = queue_family;
   queue_info.queueCount = 1;
   queue_info.pQueuePriorities = &priority;

   VkDeviceCreateInfo device_info = {};
   device_info.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
   device_info.queueCreateInfoCount = 1;
   device_info.pQueueCreateInfos = &queue_info;
   device_info.enabledLayerCount = num_required_device_layers;
   device_info.ppEnabledLayerNames = required_device_layers;
   device_info.enabledExtensionCount = num_required_device_extensions;
   device_info.ppEnabledExtensionNames = required_device_extensions;
   device_info.pEnabledFeatures = &enabled;

   PFN_vkCreateDevice create_device = reinterpret_cast<PFN_vkCreateDevice>(
         get_instance_proc_addr(instance, "vkCreateDevice"));
   PFN_vkGetDeviceProcAddr get_device_proc_addr =
         reinterpret_cast<PFN_vkGetDeviceProcAddr>(
               get_instance_proc_addr(instance, "vkGetDeviceProcAddr"));
   if (!create_device || !get_device_proc_addr)
      return false;

   VkDevice device = VK_NULL_HANDLE;
   if (create_device(gpu, &device_info, nullptr, &device) != VK_SUCCESS)
      return false;

   PFN_vkGetDeviceQueue get_device_queue =
         reinterpret_cast<PFN_vkGetDeviceQueue>(
               get_device_proc_addr(device, "vkGetDeviceQueue"));
   if (!get_device_queue)
   {
      PFN_vkDestroyDevice destroy_device =
            reinterpret_cast<PFN_vkDestroyDevice>(
                  get_device_proc_addr(device, "vkDestroyDevice"));
      if (destroy_device)
         destroy_device(device, nullptr);
      return false;
   }

   VkQueue queue = VK_NULL_HANDLE;
   get_device_queue(device, queue_family, 0, &queue);
   if (queue == VK_NULL_HANDLE)
   {
      PFN_vkDestroyDevice destroy_device =
            reinterpret_cast<PFN_vkDestroyDevice>(
                  get_device_proc_addr(device, "vkDestroyDevice"));
      if (destroy_device)
         destroy_device(device, nullptr);
      return false;
   }

   std::memset(context, 0, sizeof(*context));
   context->gpu = gpu;
   context->device = device;
   context->queue = queue;
   context->queue_family_index = queue_family;
   context->presentation_queue = queue;
   context->presentation_queue_family_index = queue_family;
   g_tessellation_enabled = tessellation_enabled;
   return true;
}

static const struct retro_hw_render_context_negotiation_interface_vulkan
      g_negotiation_interface = {
   RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN,
   RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN_VERSION,
   vulkan_libretro_get_application_info,
   vulkan_libretro_create_device,
   nullptr
};

extern "C" {

const struct retro_hw_render_context_negotiation_interface_vulkan *
vulkan_libretro_get_negotiation_interface(void)
{
   return &g_negotiation_interface;
}

int vulkan_libretro_context_reset(retro_environment_t environment)
{
   if (!environment || !environment(RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE,
            reinterpret_cast<void *>(&g_vulkan_if)) || !g_vulkan_if)
      return 0;

   if (g_vulkan_if->interface_type != RETRO_HW_RENDER_INTERFACE_VULKAN ||
       g_vulkan_if->interface_version < 1 ||
       !g_vulkan_if->set_image)
   {
      g_vulkan_if = nullptr;
      return 0;
   }

   RetroVulkanAdopt adopt = {};
   adopt.instance = g_vulkan_if->instance;
   adopt.gpu = g_vulkan_if->gpu;
   adopt.device = g_vulkan_if->device;
   adopt.queue = g_vulkan_if->queue;
   adopt.queue_family = g_vulkan_if->queue_index;
   adopt.tessellation_enabled = g_tessellation_enabled;
   Renderer_SetRetroAdopt(adopt);

   delete g_renderer;
   g_renderer = new Renderer();
   g_renderer->OpenWindow(320, 240, "yabasanshiro", nullptr);
   VIDVulkan::getInstance()->setRenderer(g_renderer);
   return 1;
}

void vulkan_libretro_context_destroy(void)
{
   delete g_renderer;
   g_renderer = nullptr;
   g_vulkan_if = nullptr;
   std::memset(&g_current_image, 0, sizeof(g_current_image));

   RetroVulkanAdopt adopt = {};
   Renderer_SetRetroAdopt(adopt);
}

void vulkan_libretro_set_image(void)
{
   if (!g_vulkan_if || !g_renderer || !g_renderer->getWindow())
      return;

   Window *window = g_renderer->getWindow();
   std::memset(&g_current_image, 0, sizeof(g_current_image));
   g_current_image.image_view = window->getCurrentImageView();
   g_current_image.image_layout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
   g_current_image.create_info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
   g_current_image.create_info.image = window->getCurrentImage();
   g_current_image.create_info.viewType = VK_IMAGE_VIEW_TYPE_2D;
   g_current_image.create_info.format = window->getColorFormat();
   g_current_image.create_info.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
   g_current_image.create_info.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
   g_current_image.create_info.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
   g_current_image.create_info.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
   g_current_image.create_info.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
   g_current_image.create_info.subresourceRange.levelCount = 1;
   g_current_image.create_info.subresourceRange.layerCount = 1;

   /* The final render-pass dependency performs the color-write -> shader-read
    * barrier on RetroArch's graphics queue, so a semaphore is unnecessary. */
   g_vulkan_if->set_image(g_vulkan_if->handle, &g_current_image, 0, nullptr,
         g_vulkan_if->queue_index);
}

uint32_t vulkan_libretro_get_sync_index(void)
{
   return g_vulkan_if && g_vulkan_if->get_sync_index
         ? g_vulkan_if->get_sync_index(g_vulkan_if->handle) : 0;
}

uint32_t vulkan_libretro_get_sync_image_count(void)
{
   uint32_t mask = g_vulkan_if && g_vulkan_if->get_sync_index_mask
         ? g_vulkan_if->get_sync_index_mask(g_vulkan_if->handle) : 1;
   if (mask == 0)
      return 1;

   uint32_t count = 0;
   while (mask)
   {
      ++count;
      mask >>= 1;
   }
   return count;
}

void vulkan_libretro_wait_sync_index(void)
{
   if (g_vulkan_if && g_vulkan_if->wait_sync_index)
      g_vulkan_if->wait_sync_index(g_vulkan_if->handle);
}

VkResult vulkan_libretro_queue_submit(VkQueue queue, uint32_t submit_count,
      const VkSubmitInfo *submits, VkFence fence)
{
   if (g_vulkan_if && g_vulkan_if->lock_queue)
      g_vulkan_if->lock_queue(g_vulkan_if->handle);
   VkResult result = vkQueueSubmit(queue, submit_count, submits, fence);
   if (g_vulkan_if && g_vulkan_if->unlock_queue)
      g_vulkan_if->unlock_queue(g_vulkan_if->handle);
   return result;
}

VkResult vulkan_libretro_queue_wait_idle(VkQueue queue)
{
   if (g_vulkan_if && g_vulkan_if->lock_queue)
      g_vulkan_if->lock_queue(g_vulkan_if->handle);
   VkResult result = vkQueueWaitIdle(queue);
   if (g_vulkan_if && g_vulkan_if->unlock_queue)
      g_vulkan_if->unlock_queue(g_vulkan_if->handle);
   return result;
}

VkResult vulkan_libretro_device_wait_idle(VkDevice device)
{
   if (g_vulkan_if && g_vulkan_if->lock_queue)
      g_vulkan_if->lock_queue(g_vulkan_if->handle);
   VkResult result = vkDeviceWaitIdle(device);
   if (g_vulkan_if && g_vulkan_if->unlock_queue)
      g_vulkan_if->unlock_queue(g_vulkan_if->handle);
   return result;
}

} /* extern "C" */

/* Libretro keeps the dummy OSD core selected. Avoid pulling the standalone
 * NanoVG Vulkan backend (and its embedded fonts) into the core. */
int NanovgVulkanSetDevices(VkDevice, VkPhysicalDevice, VkRenderPass,
      VkCommandBuffer, int)
{
   return 0;
}
