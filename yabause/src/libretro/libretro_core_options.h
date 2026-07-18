#ifndef YABASANSHIRO_LIBRETRO_CORE_OPTIONS_H
#define YABASANSHIRO_LIBRETRO_CORE_OPTIONS_H

#include <stdlib.h>
#include <string.h>

#include <libretro.h>

static struct retro_core_option_definition option_defs_us[] = {
   {
      "yabasanshiro_force_hle_bios",
      "Force HLE BIOS (restart)",
      NULL,
      {
         { "disabled", NULL },
         { "enabled", NULL },
         { NULL, NULL },
      },
      "disabled"
   },
#ifdef HAVE_VULKAN
   {
      "yabasanshiro_renderer",
      "Graphics API",
      "Select the graphics API. Core restart required.",
      {
         { "vulkan", "Vulkan" },
         { "opengl", "OpenGL" },
         { NULL, NULL },
      },
      "vulkan"
   },
#endif
   {
      "yabasanshiro_frameskip",
      "Auto-frameskip",
      NULL,
      {
         { "enabled", NULL },
         { "disabled", NULL },
         { NULL, NULL },
      },
      "enabled"
   },
   {
      "yabasanshiro_addon_cart",
      "Addon Cartridge (restart)",
      NULL,
      {
         { "4M_extended_ram", NULL },
         { "1M_extended_ram", NULL },
         { NULL, NULL },
      },
      "4M_extended_ram"
   },
   {
      "yabasanshiro_system_language",
      "System Language (restart)",
      NULL,
      {
         { "english", NULL },
         { "deutsch", NULL },
         { "french", NULL },
         { "spanish", NULL },
         { "italian", NULL },
         { "japanese", NULL },
         { NULL, NULL },
      },
      "english"
   },
   {
      "yabasanshiro_multitap_port1",
      "6Player Adaptor on Port 1",
      NULL,
      {
         { "disabled", NULL },
         { "enabled", NULL },
         { NULL, NULL },
      },
      "disabled"
   },
   {
      "yabasanshiro_multitap_port2",
      "6Player Adaptor on Port 2",
      NULL,
      {
         { "disabled", NULL },
         { "enabled", NULL },
         { NULL, NULL },
      },
      "disabled"
   },
#ifdef DYNAREC_DEVMIYAX
   {
      "yabasanshiro_sh2coretype",
      "SH2 Core (restart)",
      NULL,
      {
         { "dynarec", NULL },
         { "interpreter", NULL },
         { NULL, NULL },
      },
      "dynarec"
   },
#endif
   {
      "yabasanshiro_sh2_cache",
      "SH2 Cache Emulation (restart)",
      NULL,
      {
         { "enabled", NULL },
         { "disabled", NULL },
         { NULL, NULL },
      },
      "enabled"
   },
   {
      "yabasanshiro_video_filter",
      "Video Filter",
      NULL,
      {
         { "none", NULL },
         { "bilinear", NULL },
         { "fxaa", NULL },
         { "scanlines", NULL },
         { NULL, NULL },
      },
      "none"
   },
   {
      "yabasanshiro_rotate_screen",
      "Rotate Screen (native resolution only)",
      NULL,
      {
         { "disabled", NULL },
         { "enabled", NULL },
         { NULL, NULL },
      },
      "disabled"
   },
   {
      "yabasanshiro_scsp_main_mode",
      "SCSP Threading (restart)",
      NULL,
      {
         { "synced", NULL },
         { "realtime", NULL },
         { NULL, NULL },
      },
      "synced"
   },
   {
      "yabasanshiro_scsp_sync_per_frame",
      "SCSP Syncs Per Frame (restart)",
      NULL,
      {
         { "1", NULL },
         { "2", NULL },
         { "4", NULL },
         { "8", NULL },
         { NULL, NULL },
      },
      "1"
   },
   {
      "yabasanshiro_polygon_mode",
      "Polygon Mode (restart)",
      NULL,
      {
         { "perspective_correction", NULL },
         { "gpu_tesselation", NULL },
         { "cpu_tesselation", NULL },
         { NULL, NULL },
      },
      "perspective_correction"
   },
   {
      "yabasanshiro_resolution_mode",
      "Resolution Mode (restart)",
      NULL,
      {
         { "original", NULL },
         { "2x", NULL },
         { "4x", NULL },
         { "720p", NULL },
         { "1080p", NULL },
         { "4k", NULL },
         { NULL, NULL },
      },
      "original"
   },
   {
      "yabasanshiro_rbg_resolution_mode",
      "RGB resolution mode",
      NULL,
      {
         { "original", NULL },
         { "2x", NULL },
         { "720p", NULL },
         { "1080p", NULL },
         { "Fit_to_emulation", NULL },
         { NULL, NULL },
      },
      "original"
   },
#if !defined(__APPLE__)
   {
      "yabasanshiro_rbg_use_compute_shader",
      "RGB use compute shader for RGB",
      NULL,
      {
         { "disabled", NULL },
         { "enabled", NULL },
         { NULL, NULL },
      },
      "disabled"
   },
#endif
   { NULL, NULL, NULL, {{0}}, NULL },
};

static void libretro_set_core_options(retro_environment_t environ_cb)
{
   unsigned version = 0;

   if (!environ_cb)
      return;

   if (environ_cb(RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION, &version) &&
       version >= 1)
   {
      environ_cb(RETRO_ENVIRONMENT_SET_CORE_OPTIONS, option_defs_us);
      return;
   }

   size_t num_options = 0;
   size_t i;
   struct retro_variable *variables = NULL;
   char **values_buf = NULL;

   while (option_defs_us[num_options].key)
      num_options++;

   variables = (struct retro_variable *)calloc(
         num_options + 1, sizeof(*variables));
   values_buf = (char **)calloc(num_options, sizeof(*values_buf));
   if (!variables || !values_buf)
      goto cleanup;

   for (i = 0; i < num_options; i++)
   {
      const struct retro_core_option_definition *option = &option_defs_us[i];
      size_t default_index = 0;
      size_t num_values = 0;
      size_t buffer_length = strlen(option->desc) + 3;
      size_t j;

      while (option->values[num_values].value)
      {
         const char *value = option->values[num_values].value;

         if (option->default_value &&
             strcmp(value, option->default_value) == 0)
            default_index = num_values;

         buffer_length += strlen(value) + 1;
         num_values++;
      }

      values_buf[i] = (char *)calloc(buffer_length, sizeof(char));
      if (!values_buf[i])
         goto cleanup;

      strcpy(values_buf[i], option->desc);
      strcat(values_buf[i], "; ");
      strcat(values_buf[i], option->values[default_index].value);

      for (j = 0; j < num_values; j++)
      {
         if (j == default_index)
            continue;
         strcat(values_buf[i], "|");
         strcat(values_buf[i], option->values[j].value);
      }

      variables[i].key = option->key;
      variables[i].value = values_buf[i];
   }

   environ_cb(RETRO_ENVIRONMENT_SET_VARIABLES, variables);

cleanup:
   if (values_buf)
   {
      for (i = 0; i < num_options; i++)
         free(values_buf[i]);
      free(values_buf);
   }
   free(variables);
}

#endif
