/* -----------------------------------------------------
This source code is public domain ( CC0 )
The code is provided as-is without limitations, requirements and responsibilities.
Creators and contributors to this source code are provided as a token of appreciation
and no one associated with this source code can be held responsible for any possible
damages or losses of any kind.

Original file creator:  Niko Kauppi (Code maintenance)
Contributors:
----------------------------------------------------- */
/*
        Copyright 2021 devMiyax(smiyaxdev@gmail.com)

This file is part of YabaSanshiro.

        YabaSanshiro is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

YabaSanshiro is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
along with YabaSanshiro; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

#if defined (_WINDOWS)
#define VK_USE_PLATFORM_WIN32_KHR 1
#endif

#include "Window.h"
#include "Renderer.h"
#include "Shared.h"

#include <array>
#include <assert.h>

#include "VulkanTools.h"

#if !defined(__LIBRETRO__)

Window::Window(Renderer *renderer, uint32_t size_x, uint32_t size_y, std::string name, void *nativeWindow) {

  _renderer = renderer;
  _surface_size_x = size_x;
  _surface_size_y = size_y;
  _window_name = name;
#if defined(__ANDROID__)
  window = (ANativeWindow *)nativeWindow;
#endif
#if defined(__RETORO_ARENA__)  
    window = (SDL_Window*)nativeWindow;
#endif
	_InitOSWindow();
	_InitSurface();
	_InitSwapchain();
	_InitSwapchainImages();
	_InitDepthStencilImage();
	_InitRenderPass();
	_InitFramebuffers();
	_InitSynchronizations();
}

Window::~Window() {
  vkQueueWaitIdle(_renderer->GetVulkanQueue());
  _DeInitSynchronizations();
  _DeInitFramebuffers();
  _DeInitRenderPass();
  _DeInitDepthStencilImage();
  _DeInitSwapchainImages();
  _DeInitSwapchain();
  _DeInitSurface();
  _DeInitOSWindow();
}

void Window::resize(int width, int height) {

  LOGI("%s %d %d", "resize called", width, height);
  // if (_surface_size_x == width && _surface_size_y == height) {
  //   return;
  // }
  cleanupSwapChain();
}

void Window::cleanupSwapChain() {

  VkDevice device = _renderer->GetVulkanDevice();
  vkDeviceWaitIdle(device);

  _DeInitFramebuffers();
  _DeInitDepthStencilImage();
  _DeInitSwapchainImages();
  _DeInitSwapchain();
  _DeInitSurface();

  _InitSurface();
  _InitSwapchain();
  _InitSwapchainImages();
  _InitDepthStencilImage();
  _InitFramebuffers();
}

void Window::Close() { _window_should_run = false; }

bool Window::Update() {
  _UpdateOSWindow();
  return _window_should_run;
}

int Window::BeginRender() {
  ErrorCheck(vkAcquireNextImageKHR(_renderer->GetVulkanDevice(), _swapchain, UINT64_MAX, VK_NULL_HANDLE,
                                   _swapchain_image_available, &_active_swapchain_image_id));
  ErrorCheck(vkWaitForFences(_renderer->GetVulkanDevice(), 1, &_swapchain_image_available, VK_TRUE, UINT64_MAX));
  ErrorCheck(vkResetFences(_renderer->GetVulkanDevice(), 1, &_swapchain_image_available));
  // ErrorCheck( vkQueueWaitIdle( _renderer->GetVulkanQueue() ) );

  return _active_swapchain_image_id;
}

void Window::EndRender(std::vector<VkSemaphore> wait_semaphores) {
  VkResult present_result = VkResult::VK_RESULT_MAX_ENUM;
  VkResult funcResult;

  VkPresentInfoKHR present_info{};
  present_info.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
  present_info.waitSemaphoreCount = wait_semaphores.size();
  present_info.pWaitSemaphores = wait_semaphores.data();
  present_info.swapchainCount = 1;
  present_info.pSwapchains = &_swapchain;
  present_info.pImageIndices = &_active_swapchain_image_id;
  present_info.pResults = &present_result;

  funcResult = vkQueuePresentKHR(_renderer->GetVulkanQueue(), &present_info);
  // ErrorCheck( vkQueueWaitIdle( _renderer->GetVulkanQueue() ) );
  ErrorCheck(funcResult);
  ErrorCheck(present_result);
  if (funcResult == VK_SUBOPTIMAL_KHR) {
    cleanupSwapChain();
  }
}

VkRenderPass Window::GetVulkanRenderPass() { return _render_pass; }

VkRenderPass Window::GetVulkanKeepRenderPass() { return _render_pass_keep; }

VkFramebuffer Window::GetVulkanActiveFramebuffer() { return _framebuffers[_active_swapchain_image_id]; }

VkExtent2D Window::GetVulkanSurfaceSize() { return {_surface_size_x, _surface_size_y}; }

void Window::_InitSurface() {
  _InitOSSurface();

  auto gpu = _renderer->GetVulkanPhysicalDevice();

  uint32_t findex = _renderer->GetVulkanGraphicsQueueFamilyIndex();

  LOGI("%s %d", "vkGetPhysicalDeviceSurfaceSupportKHR in ", findex);
  VkBool32 WSI_supported = false;

  ErrorCheck(vkGetPhysicalDeviceSurfaceSupportKHR(gpu, findex, _surface,
                                                  &WSI_supported));
  if (!WSI_supported) {
    assert(0 && "WSI not supported");
    std::exit(-1);
  }

  LOGI("%s", "vkGetPhysicalDeviceSurfaceSupportKHR out");

  LOGI("%s", "vkGetPhysicalDeviceSurfaceCapabilitiesKHR in");
  ErrorCheck(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(gpu, _surface, &_surface_capabilities));

  /*
  uint32_t width = _surface_capabilities.currentExtent.width;
  uint32_t height = _surface_capabilities.currentExtent.height;
  if (_surface_capabilities.currentTransform & VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR ||
    _surface_capabilities.currentTransform & VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR) {
    _surface_capabilities.currentExtent.height = width;
    _surface_capabilities.currentExtent.width = height;
  }
  */
  if (_surface_capabilities.currentExtent.width < UINT32_MAX) {
    _surface_size_x = _surface_capabilities.currentExtent.width;
    _surface_size_y = _surface_capabilities.currentExtent.height;
  }

  uint32_t width = _surface_capabilities.currentExtent.width;
  uint32_t height = _surface_capabilities.currentExtent.height;
  if (_surface_capabilities.currentTransform & VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR ||
      _surface_capabilities.currentTransform & VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR) {
    // Swap to get identity width and height
    _surface_capabilities.currentExtent.height = width;
    _surface_capabilities.currentExtent.width = height;
  }

  LOGI("%s %d,%d,%08X", "vkGetPhysicalDeviceSurfaceCapabilitiesKHR out", _surface_size_x, _surface_size_y,
       _surface_capabilities.currentTransform);

  {
    uint32_t format_count = 0;
    LOGI("%s", "vkGetPhysicalDeviceSurfaceFormatsKHR In");
    ErrorCheck(vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, _surface, &format_count, nullptr));
    if (format_count == 0) {
      assert(0 && "Surface formats missing.");
      std::exit(-1);
    }
    LOGI("%s %d", "vkGetPhysicalDeviceSurfaceFormatsKHR out", format_count);

    std::vector<VkSurfaceFormatKHR> formats(format_count);

    LOGI("%s", "vkGetPhysicalDeviceSurfaceFormatsKHR In");
    ErrorCheck(vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, _surface, &format_count, formats.data()));
    if (formats[0].format == VK_FORMAT_UNDEFINED) {
      _surface_format.format = VK_FORMAT_B8G8R8A8_UNORM;
      _surface_format.colorSpace = VK_COLORSPACE_SRGB_NONLINEAR_KHR;
    } else {
      _surface_format = formats[0];
    }
    LOGI("%s", "vkGetPhysicalDeviceSurfaceFormatsKHR OUT");
  }
}

void Window::_DeInitSurface() { vkDestroySurfaceKHR(_renderer->GetVulkanInstance(), _surface, nullptr); }

void Window::_InitSwapchain() {
  // This code is old and the fixed one is below
  // if( _swapchain_image_count > _surface_capabilities.maxImageCount ) _swapchain_image_count =
  // _surface_capabilities.maxImageCount; if( _swapchain_image_count < _surface_capabilities.minImageCount + 1 )
  // _swapchain_image_count = _surface_capabilities.minImageCount + 1;

  // The code above will work just fine in our tutorials and likely on every possible implementation of vulkan as well
  // so this change isn't that important. Just to be absolutely sure we don't go over or below the given limits we
  // should check this a little bit different though. maxImageCount can actually be zero in which case the amount of
  // swapchain images do not have an upper limit other than available memory. It's also possible that the swapchain
  // image amount is locked to a certain value on certain systems. The code below takes into consideration both of these
  // possibilities.
  if (_swapchain_image_count < _surface_capabilities.minImageCount + 1)
    _swapchain_image_count = _surface_capabilities.minImageCount + 1;
  if (_surface_capabilities.maxImageCount > 0) {
    if (_swapchain_image_count > _surface_capabilities.maxImageCount)
      _swapchain_image_count = _surface_capabilities.maxImageCount;
  }

  VkPresentModeKHR present_mode = VK_PRESENT_MODE_FIFO_KHR;
  {
    uint32_t present_mode_count = 0;
    ErrorCheck(vkGetPhysicalDeviceSurfacePresentModesKHR(_renderer->GetVulkanPhysicalDevice(), _surface,
                                                         &present_mode_count, nullptr));
    std::vector<VkPresentModeKHR> present_mode_list(present_mode_count);
    ErrorCheck(vkGetPhysicalDeviceSurfacePresentModesKHR(_renderer->GetVulkanPhysicalDevice(), _surface,
                                                         &present_mode_count, present_mode_list.data()));
    for (auto m : present_mode_list) {
      if (m == this->presentMode)
        present_mode = m;
    }
  }

  VkSwapchainCreateInfoKHR swapchain_create_info{};
  swapchain_create_info.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
  swapchain_create_info.surface = _surface;
  swapchain_create_info.minImageCount = _swapchain_image_count;
  swapchain_create_info.imageFormat = _surface_format.format;
  swapchain_create_info.imageColorSpace = _surface_format.colorSpace;
  swapchain_create_info.imageExtent.width = _surface_capabilities.currentExtent.width;   //_surface_size_x;
  swapchain_create_info.imageExtent.height = _surface_capabilities.currentExtent.height; //_surface_size_y;
  swapchain_create_info.imageArrayLayers = 1;
  swapchain_create_info.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
  swapchain_create_info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
  swapchain_create_info.queueFamilyIndexCount = 0;
  swapchain_create_info.pQueueFamilyIndices = nullptr;
  swapchain_create_info.preTransform = _surface_capabilities.currentTransform;
  if (_surface_capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) {
    swapchain_create_info.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
  } else {
    swapchain_create_info.compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
  }
  swapchain_create_info.presentMode = present_mode;
  swapchain_create_info.clipped = VK_TRUE;
  swapchain_create_info.oldSwapchain = VK_NULL_HANDLE;

  //VkSurfaceFullScreenExclusiveInfoEXT fullScreenExclusiveInfo = {};
  //fullScreenExclusiveInfo.sType = VK_STRUCTURE_TYPE_SURFACE_FULL_SCREEN_EXCLUSIVE_INFO_EXT;
  //fullScreenExclusiveInfo.fullScreenExclusive = VK_FULL_SCREEN_EXCLUSIVE_ALLOWED_EXT;
  //swapchain_create_info.pNext = &fullScreenExclusiveInfo;

  ErrorCheck(vkCreateSwapchainKHR(_renderer->GetVulkanDevice(), &swapchain_create_info, nullptr, &_swapchain));

  ErrorCheck(vkGetSwapchainImagesKHR(_renderer->GetVulkanDevice(), _swapchain, &_swapchain_image_count, nullptr));
}

void Window::_DeInitSwapchain() { vkDestroySwapchainKHR(_renderer->GetVulkanDevice(), _swapchain, nullptr); }

void Window::_InitSwapchainImages() {
  _swapchain_images.resize(_swapchain_image_count);
  _swapchain_image_views.resize(_swapchain_image_count);

  ErrorCheck(vkGetSwapchainImagesKHR(_renderer->GetVulkanDevice(), _swapchain, &_swapchain_image_count,
                                     _swapchain_images.data()));

  for (uint32_t i = 0; i < _swapchain_image_count; ++i) {
    VkImageViewCreateInfo image_view_create_info{};
    image_view_create_info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    image_view_create_info.image = _swapchain_images[i];
    image_view_create_info.viewType = VK_IMAGE_VIEW_TYPE_2D;
    image_view_create_info.format = _surface_format.format;
    image_view_create_info.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
    image_view_create_info.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
    image_view_create_info.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
    image_view_create_info.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
    image_view_create_info.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    image_view_create_info.subresourceRange.baseMipLevel = 0;
    image_view_create_info.subresourceRange.levelCount = 1;
    image_view_create_info.subresourceRange.baseArrayLayer = 0;
    image_view_create_info.subresourceRange.layerCount = 1;

    ErrorCheck(
        vkCreateImageView(_renderer->GetVulkanDevice(), &image_view_create_info, nullptr, &_swapchain_image_views[i]));
  }
}

void Window::_DeInitSwapchainImages() {
  for (auto view : _swapchain_image_views) {
    vkDestroyImageView(_renderer->GetVulkanDevice(), view, nullptr);
  }
}

void Window::_InitDepthStencilImage() {
  {
    std::vector<VkFormat> try_formats{
        //VK_FORMAT_D32_SFLOAT_S8_UINT,
        VK_FORMAT_D24_UNORM_S8_UINT,
        VK_FORMAT_D16_UNORM_S8_UINT,
        // VK_FORMAT_D32_SFLOAT,
        // VK_FORMAT_D16_UNORM
    };
    for (auto f : try_formats) {
      VkFormatProperties format_properties{};
      vkGetPhysicalDeviceFormatProperties(_renderer->GetVulkanPhysicalDevice(), f, &format_properties);
      if (format_properties.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) {
        _depth_stencil_format = f;
        break;
      }
    }
    if (_depth_stencil_format == VK_FORMAT_UNDEFINED) {
      assert(0 && "Depth stencil format not selected.");
      std::exit(-1);
    }
   
    if( ( _depth_stencil_format == VK_FORMAT_D32_SFLOAT_S8_UINT ) ||
      ( _depth_stencil_format == VK_FORMAT_D24_UNORM_S8_UINT ) ||
      ( _depth_stencil_format == VK_FORMAT_D16_UNORM_S8_UINT ) ||
      ( _depth_stencil_format == VK_FORMAT_S8_UINT ) ) {
      _stencil_available = true;
    }
  }

  VkImageCreateInfo image_create_info{};
  image_create_info.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
  image_create_info.flags = 0;
  image_create_info.imageType = VK_IMAGE_TYPE_2D;
  image_create_info.format = _depth_stencil_format;
  image_create_info.extent.width = _surface_capabilities.currentExtent.width;   //_surface_size_x;
  image_create_info.extent.height = _surface_capabilities.currentExtent.height; //_surface_size_y;
  image_create_info.extent.depth = 1;
  image_create_info.mipLevels = 1;
  image_create_info.arrayLayers = 1;
  image_create_info.samples = VK_SAMPLE_COUNT_1_BIT;
  image_create_info.tiling = VK_IMAGE_TILING_OPTIMAL;
  image_create_info.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
  image_create_info.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  image_create_info.queueFamilyIndexCount = VK_QUEUE_FAMILY_IGNORED;
  image_create_info.pQueueFamilyIndices = nullptr;
  image_create_info.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;

  ErrorCheck(vkCreateImage(_renderer->GetVulkanDevice(), &image_create_info, nullptr, &_depth_stencil_image));
  //printf("_depth_stencil_image = %llx\n", _depth_stencil_image);
  vkDebugNameObject(_renderer->GetVulkanDevice(), VK_OBJECT_TYPE_IMAGE, (uint64_t)_depth_stencil_image, "Window _depth_stencil_image");

  VkMemoryRequirements image_memory_requirements{};
  vkGetImageMemoryRequirements(_renderer->GetVulkanDevice(), _depth_stencil_image, &image_memory_requirements);

  uint32_t memory_index = FindMemoryTypeIndex(&_renderer->GetVulkanPhysicalDeviceMemoryProperties(),
                                              &image_memory_requirements, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

  VkMemoryAllocateInfo memory_allocate_info{};
  memory_allocate_info.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  memory_allocate_info.allocationSize = image_memory_requirements.size;
  memory_allocate_info.memoryTypeIndex = memory_index;

  ErrorCheck(
      vkAllocateMemory(_renderer->GetVulkanDevice(), &memory_allocate_info, nullptr, &_depth_stencil_image_memory));
  ErrorCheck(vkBindImageMemory(_renderer->GetVulkanDevice(), _depth_stencil_image, _depth_stencil_image_memory, 0));

  VkImageViewCreateInfo image_view_create_info{};
  image_view_create_info.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
  image_view_create_info.image = _depth_stencil_image;
  image_view_create_info.viewType = VK_IMAGE_VIEW_TYPE_2D;
  image_view_create_info.format = _depth_stencil_format;
  image_view_create_info.components.r = VK_COMPONENT_SWIZZLE_IDENTITY;
  image_view_create_info.components.g = VK_COMPONENT_SWIZZLE_IDENTITY;
  image_view_create_info.components.b = VK_COMPONENT_SWIZZLE_IDENTITY;
  image_view_create_info.components.a = VK_COMPONENT_SWIZZLE_IDENTITY;
  image_view_create_info.subresourceRange.aspectMask =
      VK_IMAGE_ASPECT_DEPTH_BIT | (_stencil_available ? VK_IMAGE_ASPECT_STENCIL_BIT : 0);
  image_view_create_info.subresourceRange.baseMipLevel = 0;
  image_view_create_info.subresourceRange.levelCount = 1;
  image_view_create_info.subresourceRange.baseArrayLayer = 0;
  image_view_create_info.subresourceRange.layerCount = 1;

  ErrorCheck(
      vkCreateImageView(_renderer->GetVulkanDevice(), &image_view_create_info, nullptr, &_depth_stencil_image_view));
}

void Window::_DeInitDepthStencilImage() {
  vkDestroyImageView(_renderer->GetVulkanDevice(), _depth_stencil_image_view, nullptr);
  vkDestroyImage(_renderer->GetVulkanDevice(), _depth_stencil_image, nullptr);
  vkFreeMemory(_renderer->GetVulkanDevice(), _depth_stencil_image_memory, nullptr);
}

void Window::_InitRenderPass() {
  std::array<VkAttachmentDescription, 2> attachments{};

  attachments[0].flags = 0;
  attachments[0].format = _surface_format.format;
  attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
  attachments[0].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
  attachments[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  attachments[0].initialLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
  attachments[0].finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

  attachments[1].flags = 0;
  attachments[1].format = _depth_stencil_format;
  attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
  attachments[1].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
  attachments[1].storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
  attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
  attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_STORE;
  attachments[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
  attachments[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

	attachments[ 1 ].flags						= 0;
	attachments[ 1 ].format						= _depth_stencil_format;
	attachments[ 1 ].samples					= VK_SAMPLE_COUNT_1_BIT;
	attachments[ 1 ].loadOp						= VK_ATTACHMENT_LOAD_OP_CLEAR;
	attachments[ 1 ].storeOp					= VK_ATTACHMENT_STORE_OP_DONT_CARE;
	attachments[ 1 ].stencilLoadOp				= VK_ATTACHMENT_LOAD_OP_CLEAR;
	attachments[ 1 ].stencilStoreOp				= VK_ATTACHMENT_STORE_OP_STORE;
	attachments[ 1 ].initialLayout				= VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
	attachments[ 1 ].finalLayout				= VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

  VkAttachmentReference sub_pass_0_depth_stencil_attachment{};
  sub_pass_0_depth_stencil_attachment.attachment = 1;
  sub_pass_0_depth_stencil_attachment.layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

  std::array<VkAttachmentReference, 1> sub_pass_0_color_attachments{};
  sub_pass_0_color_attachments[0].attachment = 0;
  sub_pass_0_color_attachments[0].layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
  
  std::array<VkSubpassDescription, 1> sub_passes{};
  sub_passes[0].pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
  sub_passes[0].colorAttachmentCount = sub_pass_0_color_attachments.size();
  sub_passes[0].pColorAttachments = sub_pass_0_color_attachments.data(); // layout(location=0) out vec4 FinalColor;
  sub_passes[0].pDepthStencilAttachment = &sub_pass_0_depth_stencil_attachment;

  VkRenderPassCreateInfo render_pass_create_info{};
  render_pass_create_info.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
  render_pass_create_info.attachmentCount = attachments.size();
  render_pass_create_info.pAttachments = attachments.data();
  render_pass_create_info.subpassCount = sub_passes.size();
  render_pass_create_info.pSubpasses = sub_passes.data();

  ErrorCheck(vkCreateRenderPass(_renderer->GetVulkanDevice(), &render_pass_create_info, nullptr, &_render_pass));

  attachments[0].loadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
  attachments[1].loadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
  attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_LOAD;

  ErrorCheck(vkCreateRenderPass(_renderer->GetVulkanDevice(), &render_pass_create_info, nullptr, &_render_pass_keep));
}

void Window::_DeInitRenderPass() {
  vkDestroyRenderPass(_renderer->GetVulkanDevice(), _render_pass, nullptr);
  vkDestroyRenderPass(_renderer->GetVulkanDevice(), _render_pass_keep, nullptr);
}

void Window::_InitFramebuffers() {
  _framebuffers.resize(_swapchain_image_count);
  for (uint32_t i = 0; i < _swapchain_image_count; ++i) {
    std::array<VkImageView, 2> attachments{};
    attachments[0] = _swapchain_image_views[i];
    attachments[1] = _depth_stencil_image_view;

    VkFramebufferCreateInfo framebuffer_create_info{};
    framebuffer_create_info.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    framebuffer_create_info.renderPass = _render_pass;
    framebuffer_create_info.attachmentCount = attachments.size();
    framebuffer_create_info.pAttachments = attachments.data();
    framebuffer_create_info.width = _surface_capabilities.currentExtent.width;   //_surface_size_x;
    framebuffer_create_info.height = _surface_capabilities.currentExtent.height; //_surface_size_y;
    framebuffer_create_info.layers = 1;

    ErrorCheck(vkCreateFramebuffer(_renderer->GetVulkanDevice(), &framebuffer_create_info, nullptr, &_framebuffers[i]));
  }
}

void Window::_DeInitFramebuffers() {
  for (auto f : _framebuffers) {
    vkDestroyFramebuffer(_renderer->GetVulkanDevice(), f, nullptr);
  }
}

void Window::_InitSynchronizations() {
  VkFenceCreateInfo fence_create_info{};
  fence_create_info.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
  vkCreateFence(_renderer->GetVulkanDevice(), &fence_create_info, nullptr, &_swapchain_image_available);
}

void Window::_DeInitSynchronizations() {
  vkDestroyFence(_renderer->GetVulkanDevice(), _swapchain_image_available, nullptr);
}

#else  // __LIBRETRO__

#include "vulkan_libretro.h"

// ---------------------------------------------------------------------------
// libretro backing: no OS surface / swapchain. RetroArch owns presentation;
// the core renders into N offscreen color images and hands the current one to
// the frontend via retro_vulkan_image (set_image). The render pass leaves the
// color image in SHADER_READ_ONLY_OPTIMAL so the frontend can sample it.
// ---------------------------------------------------------------------------

static uint32_t lr_findMemoryType(Renderer * r, uint32_t typeFilter, VkMemoryPropertyFlags props) {
  VkPhysicalDeviceMemoryProperties mp = r->GetVulkanPhysicalDeviceMemoryProperties();
  for (uint32_t i = 0; i < mp.memoryTypeCount; i++)
    if ((typeFilter & (1 << i)) && (mp.memoryTypes[i].propertyFlags & props) == props)
      return i;
  assert(0 && "No compatible Vulkan memory type.");
  return UINT32_MAX;
}

Window::Window(Renderer * renderer, uint32_t size_x, uint32_t size_y, std::string name, void * nativeWindow) {
  _renderer = renderer;
  _surface_size_x = size_x ? size_x : 512;
  _surface_size_y = size_y ? size_y : 512;
  _window_name = name;
  _swapchain_image_count = vulkan_libretro_get_sync_image_count();
  _active_swapchain_image_id = UINT32_MAX;
  _surface_format.format = VK_FORMAT_R8G8B8A8_UNORM;
  _surface_format.colorSpace = VK_COLORSPACE_SRGB_NONLINEAR_KHR;
  _surface_capabilities.currentExtent.width = _surface_size_x;
  _surface_capabilities.currentExtent.height = _surface_size_y;
  _surface_capabilities.currentTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;

  _InitSwapchainImages();
  _InitDepthStencilImage();
  _InitRenderPass();
  _InitFramebuffers();
  _InitSynchronizations();
}

Window::~Window() {
  YabVkDeviceWaitIdle(_renderer->GetVulkanDevice());
  _DeInitSynchronizations();
  _DeInitFramebuffers();
  _DeInitRenderPass();
  _DeInitDepthStencilImage();
  _DeInitSwapchainImages();
}

void Window::Close() { _window_should_run = false; }
bool Window::Update() { return _window_should_run; }

int Window::BeginRender() {
  /* One image is kept for every frontend sync index. wait_sync_index() makes
   * it legal to reuse the image RetroArch previously sampled at this index. */
  vulkan_libretro_wait_sync_index();

  uint32_t image_count = vulkan_libretro_get_sync_image_count();
  if (image_count != _swapchain_image_count) {
    YabVkDeviceWaitIdle(_renderer->GetVulkanDevice());
    _DeInitFramebuffers();
    _DeInitDepthStencilImage();
    _DeInitSwapchainImages();
    _swapchain_image_count = image_count;
    _InitSwapchainImages();
    _InitDepthStencilImage();
    _InitFramebuffers();
  }

  _active_swapchain_image_id = vulkan_libretro_get_sync_index();
  if (_active_swapchain_image_id >= _swapchain_image_count)
    _active_swapchain_image_id %= _swapchain_image_count;
  return _active_swapchain_image_id;
}

// The frontend presents; nothing to do here.
void Window::EndRender(std::vector<VkSemaphore> wait_semaphores) { (void)wait_semaphores; }

VkRenderPass  Window::GetVulkanRenderPass()       { return _render_pass; }
VkRenderPass  Window::GetVulkanKeepRenderPass()   { return _render_pass_keep; }
VkFramebuffer Window::GetVulkanActiveFramebuffer(){ return _framebuffers[_active_swapchain_image_id]; }
VkExtent2D    Window::GetVulkanSurfaceSize()      { return { _surface_size_x, _surface_size_y }; }

void Window::resize(int width, int height) {
  if (width <= 0 || height <= 0) return;
  if (_surface_size_x == static_cast<uint32_t>(width) &&
      _surface_size_y == static_cast<uint32_t>(height)) return;
  _surface_size_x = width;
  _surface_size_y = height;
  _surface_capabilities.currentExtent.width = width;
  _surface_capabilities.currentExtent.height = height;
  cleanupSwapChain();
}

void Window::cleanupSwapChain() {
  YabVkDeviceWaitIdle(_renderer->GetVulkanDevice());
  _DeInitFramebuffers();
  _DeInitDepthStencilImage();
  _DeInitSwapchainImages();
  _InitSwapchainImages();
  _InitDepthStencilImage();
  _InitFramebuffers();
}

void Window::_InitSwapchainImages() {
  VkDevice device = _renderer->GetVulkanDevice();
  _swapchain_images.resize(_swapchain_image_count);
  _swapchain_image_views.resize(_swapchain_image_count);
  _offscreen_image_memory.resize(_swapchain_image_count);

  for (uint32_t i = 0; i < _swapchain_image_count; ++i) {
    VkImageCreateInfo ici{};
    ici.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    ici.flags = VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT;
    ici.imageType = VK_IMAGE_TYPE_2D;
    ici.format = _surface_format.format;
    ici.extent = { _surface_size_x, _surface_size_y, 1 };
    ici.mipLevels = 1;
    ici.arrayLayers = 1;
    ici.samples = VK_SAMPLE_COUNT_1_BIT;
    ici.tiling = VK_IMAGE_TILING_OPTIMAL;
    ici.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    ici.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    ErrorCheck(vkCreateImage(device, &ici, nullptr, &_swapchain_images[i]));

    VkMemoryRequirements mr;
    vkGetImageMemoryRequirements(device, _swapchain_images[i], &mr);
    VkMemoryAllocateInfo mai{};
    mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    mai.allocationSize = mr.size;
    mai.memoryTypeIndex = lr_findMemoryType(_renderer, mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    ErrorCheck(vkAllocateMemory(device, &mai, nullptr, &_offscreen_image_memory[i]));
    ErrorCheck(vkBindImageMemory(device, _swapchain_images[i], _offscreen_image_memory[i], 0));

    VkImageViewCreateInfo ivci{};
    ivci.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    ivci.image = _swapchain_images[i];
    ivci.viewType = VK_IMAGE_VIEW_TYPE_2D;
    ivci.format = _surface_format.format;
    ivci.components = { VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                        VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY };
    ivci.subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };
    ErrorCheck(vkCreateImageView(device, &ivci, nullptr, &_swapchain_image_views[i]));
  }
}

void Window::_DeInitSwapchainImages() {
  VkDevice device = _renderer->GetVulkanDevice();
  for (uint32_t i = 0; i < _swapchain_image_count; ++i) {
    if (_swapchain_image_views[i]) vkDestroyImageView(device, _swapchain_image_views[i], nullptr);
    if (_swapchain_images[i])      vkDestroyImage(device, _swapchain_images[i], nullptr);
    if (_offscreen_image_memory[i]) vkFreeMemory(device, _offscreen_image_memory[i], nullptr);
  }
  _swapchain_image_views.clear();
  _swapchain_images.clear();
  _offscreen_image_memory.clear();
}

void Window::_InitDepthStencilImage() {
  VkDevice device = _renderer->GetVulkanDevice();
  const VkFormat candidates[] = {
    VK_FORMAT_D24_UNORM_S8_UINT,
    VK_FORMAT_D16_UNORM_S8_UINT,
    VK_FORMAT_D32_SFLOAT_S8_UINT
  };
  _depth_stencil_format = VK_FORMAT_UNDEFINED;
  for (VkFormat candidate : candidates) {
    VkFormatProperties properties{};
    vkGetPhysicalDeviceFormatProperties(_renderer->GetVulkanPhysicalDevice(), candidate, &properties);
    if (properties.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) {
      _depth_stencil_format = candidate;
      break;
    }
  }
  assert(_depth_stencil_format != VK_FORMAT_UNDEFINED);
  _stencil_available = true;

  VkImageCreateInfo ici{};
  ici.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
  ici.imageType = VK_IMAGE_TYPE_2D;
  ici.format = _depth_stencil_format;
  ici.extent = { _surface_size_x, _surface_size_y, 1 };
  ici.mipLevels = 1;
  ici.arrayLayers = 1;
  ici.samples = VK_SAMPLE_COUNT_1_BIT;
  ici.tiling = VK_IMAGE_TILING_OPTIMAL;
  ici.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
  ici.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
  ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
  ErrorCheck(vkCreateImage(device, &ici, nullptr, &_depth_stencil_image));

  VkMemoryRequirements mr;
  vkGetImageMemoryRequirements(device, _depth_stencil_image, &mr);
  VkMemoryAllocateInfo mai{};
  mai.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
  mai.allocationSize = mr.size;
  mai.memoryTypeIndex = lr_findMemoryType(_renderer, mr.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
  ErrorCheck(vkAllocateMemory(device, &mai, nullptr, &_depth_stencil_image_memory));
  ErrorCheck(vkBindImageMemory(device, _depth_stencil_image, _depth_stencil_image_memory, 0));

  VkImageViewCreateInfo ivci{};
  ivci.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
  ivci.image = _depth_stencil_image;
  ivci.viewType = VK_IMAGE_VIEW_TYPE_2D;
  ivci.format = _depth_stencil_format;
  ivci.subresourceRange = { VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT, 0, 1, 0, 1 };
  ErrorCheck(vkCreateImageView(device, &ivci, nullptr, &_depth_stencil_image_view));
}

void Window::_DeInitDepthStencilImage() {
  VkDevice device = _renderer->GetVulkanDevice();
  if (_depth_stencil_image_view) vkDestroyImageView(device, _depth_stencil_image_view, nullptr);
  if (_depth_stencil_image)      vkDestroyImage(device, _depth_stencil_image, nullptr);
  if (_depth_stencil_image_memory) vkFreeMemory(device, _depth_stencil_image_memory, nullptr);
  _depth_stencil_image_view = VK_NULL_HANDLE;
  _depth_stencil_image = VK_NULL_HANDLE;
  _depth_stencil_image_memory = VK_NULL_HANDLE;
}

static VkRenderPass lr_makeRenderPass(VkDevice device, VkFormat color, VkFormat depth,
                                      VkAttachmentLoadOp loadOp, VkImageLayout initColor) {
  VkAttachmentDescription atts[2] = {};
  atts[0].format = color;
  atts[0].samples = VK_SAMPLE_COUNT_1_BIT;
  atts[0].loadOp = loadOp;
  atts[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  atts[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
  atts[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
  atts[0].initialLayout = initColor;
  atts[0].finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

  atts[1].format = depth;
  atts[1].samples = VK_SAMPLE_COUNT_1_BIT;
  atts[1].loadOp = (loadOp == VK_ATTACHMENT_LOAD_OP_CLEAR) ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
  atts[1].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  atts[1].stencilLoadOp = (loadOp == VK_ATTACHMENT_LOAD_OP_CLEAR) ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
  atts[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_STORE;
  atts[1].initialLayout = (loadOp == VK_ATTACHMENT_LOAD_OP_CLEAR) ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
  atts[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

  VkAttachmentReference colorRef{ 0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL };
  VkAttachmentReference depthRef{ 1, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL };
  VkSubpassDescription sub{};
  sub.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
  sub.colorAttachmentCount = 1;
  sub.pColorAttachments = &colorRef;
  sub.pDepthStencilAttachment = &depthRef;

  VkSubpassDependency deps[2] = {};
  deps[0].srcSubpass = VK_SUBPASS_EXTERNAL;
  deps[0].dstSubpass = 0;
  deps[0].srcStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
  deps[0].dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
  deps[0].srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
  deps[0].dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
  deps[1].srcSubpass = 0;
  deps[1].dstSubpass = VK_SUBPASS_EXTERNAL;
  deps[1].srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
  deps[1].dstStageMask = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
  deps[1].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
  deps[1].dstAccessMask = VK_ACCESS_SHADER_READ_BIT;

  VkRenderPassCreateInfo rpci{};
  rpci.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
  rpci.attachmentCount = 2;
  rpci.pAttachments = atts;
  rpci.subpassCount = 1;
  rpci.pSubpasses = &sub;
  rpci.dependencyCount = 2;
  rpci.pDependencies = deps;

  VkRenderPass rp;
  ErrorCheck(vkCreateRenderPass(device, &rpci, nullptr, &rp));
  return rp;
}

void Window::_InitRenderPass() {
  VkDevice device = _renderer->GetVulkanDevice();
  _render_pass      = lr_makeRenderPass(device, _surface_format.format, _depth_stencil_format,
                                        VK_ATTACHMENT_LOAD_OP_CLEAR, VK_IMAGE_LAYOUT_UNDEFINED);
  _render_pass_keep = lr_makeRenderPass(device, _surface_format.format, _depth_stencil_format,
                                        VK_ATTACHMENT_LOAD_OP_LOAD, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
}

void Window::_DeInitRenderPass() {
  VkDevice device = _renderer->GetVulkanDevice();
  if (_render_pass)      vkDestroyRenderPass(device, _render_pass, nullptr);
  if (_render_pass_keep) vkDestroyRenderPass(device, _render_pass_keep, nullptr);
  _render_pass = VK_NULL_HANDLE;
  _render_pass_keep = VK_NULL_HANDLE;
}

void Window::_InitFramebuffers() {
  VkDevice device = _renderer->GetVulkanDevice();
  _framebuffers.resize(_swapchain_image_count);
  for (uint32_t i = 0; i < _swapchain_image_count; ++i) {
    VkImageView attachments[2] = { _swapchain_image_views[i], _depth_stencil_image_view };
    VkFramebufferCreateInfo fci{};
    fci.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    fci.renderPass = _render_pass;
    fci.attachmentCount = 2;
    fci.pAttachments = attachments;
    fci.width = _surface_size_x;
    fci.height = _surface_size_y;
    fci.layers = 1;
    ErrorCheck(vkCreateFramebuffer(device, &fci, nullptr, &_framebuffers[i]));
  }
}

void Window::_DeInitFramebuffers() {
  VkDevice device = _renderer->GetVulkanDevice();
  for (auto fb : _framebuffers)
    if (fb) vkDestroyFramebuffer(device, fb, nullptr);
  _framebuffers.clear();
}

void Window::_InitSynchronizations() {}
void Window::_DeInitSynchronizations() {}

#endif  // __LIBRETRO__
