#
# Copyright (C) 2011 Texas Instruments Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Include Modem vendor specific BoardConfig file
-include device/modem-vendor/BoardConfig.mk

# These two variables are set first, so they can be overridden
# by BoardConfigVendor.mk
BOARD_USES_GENERIC_AUDIO := false
USE_CAMERA_STUB := true

OMAP_ENHANCEMENT := true

ifdef OMAP_ENHANCEMENT
OMAP_ENHANCEMENT_CPCAM := true
OMAP_ENHANCEMENT_VTC := true
OMAP_ENHANCEMENT_S3D := true
endif

BLUETI_ENHANCEMENT := true
ENHANCED_DOMX := true
WITH_JIT := false
# Use the non-open-source parts, if they're present
#-include vendor/ti/omap5sevm/BoardConfigVendor.mk

TARGET_CPU_ABI := armeabi-v7a
TARGET_CPU_ABI2 := armeabi
TARGET_CPU_SMP := true
TARGET_ARCH_VARIANT := armv7-a-neon
ARCH_ARM_HAVE_TLS_REGISTER := true

BOARD_HAVE_BLUETOOTH := true
TARGET_NO_BOOTLOADER := true

# Recovery
TARGET_RECOVERY_PIXEL_FORMAT := "BGRA_8888"
# TARGET_RECOVERY_UI_LIB := librecovery_ui_omap5sevm
# device-specific extensions to the updater binary
TARGET_RELEASETOOLS_EXTENSIONS := device/ti/omap5sevm

BOARD_KERNEL_BASE := 0x80000000
# BOARD_KERNEL_CMDLINE

TARGET_NO_RADIOIMAGE := true
TARGET_BOARD_PLATFORM := omap5
TARGET_BOOTLOADER_BOARD_NAME := omap5sevm

BOARD_EGL_CFG := device/ti/omap5sevm/egl.cfg

#BOARD_USES_HGL := true
#BOARD_USES_OVERLAY := true
USE_OPENGL_RENDERER := true
BOARD_USES_SECURE_SERVICES := true

TARGET_USERIMAGES_USE_EXT4 := true
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 536870912
#BOARD_SYSTEMIMAGE_PARTITION_SIZE := 16777216
BOARD_USERDATAIMAGE_PARTITION_SIZE := 536870912
BOARD_FLASH_BLOCK_SIZE := 4096

#TARGET_PROVIDES_INIT_RC := true
#TARGET_USERIMAGES_SPARSE_EXT_DISABLED := true

#NFC
NFC_TI_DEVICE := true

# Connectivity - Wi-Fi
USES_TI_MAC80211 := true
ifdef USES_TI_MAC80211
BOARD_WPA_SUPPLICANT_DRIVER      := NL80211
WPA_SUPPLICANT_VERSION           := VER_0_8_X
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_wl12xx
BOARD_HOSTAPD_DRIVER             := NL80211
BOARD_HOSTAPD_PRIVATE_LIB        := lib_driver_cmd_wl12xx
BOARD_WLAN_DEVICE                := wl12xx_mac80211
BOARD_SOFTAP_DEVICE              := wl12xx_mac80211
WIFI_DRIVER_MODULE_PATH          := "/system/lib/modules/wlcore_sdio.ko"
WIFI_DRIVER_MODULE_NAME          := "wlcore_sdio"
WIFI_FIRMWARE_LOADER             := ""
COMMON_GLOBAL_CFLAGS += -DUSES_TI_MAC80211
endif

ifdef OMAP_ENHANCEMENT
COMMON_GLOBAL_CFLAGS += -DOMAP_ENHANCEMENT -DTARGET_OMAP4
ifdef OMAP_ENHANCEMENT_S3D
COMMON_GLOBAL_CFLAGS += -DOMAP_ENHANCEMENT_S3D
endif
ifdef NFC_TI_DEVICE
COMMON_GLOBAL_CFLAGS += -DNFC_JNI_TI_DEVICE
endif
ifdef BLUETI_ENHANCEMENT
COMMON_GLOBAL_CFLAGS += -DBLUETI_ENHANCEMENT
endif
ifdef OMAP_ENHANCEMENT_S3D
COMMON_GLOBAL_CFLAGS += -DOMAP_ENHANCEMENT_S3D
endif
ifdef OMAP_ENHANCEMENT_CPCAM
COMMON_GLOBAL_CFLAGS += -DOMAP_ENHANCEMENT_CPCAM
endif
ifdef OMAP_ENHANCEMENT_VTC
COMMON_GLOBAL_CFLAGS += -DOMAP_ENHANCEMENT_VTC
endif
endif

BOARD_LIB_DUMPSTATE := libdumpstate.omap5sevm

BOARD_VENDOR_TI_GPS_HARDWARE := omap5

# Common device independent definitions
include device/ti/common-open/BoardConfig.mk