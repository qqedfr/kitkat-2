# This file lists the firmware, software that are specific to
# WiLink connectivity chip on OMAPx platforms.

PRODUCT_PACKAGES += uim-sysfs \
	bt_sco_app \
	kfmapp     \
        BluetoothSCOApp \
        FmRxApp \
        FmTxApp \
        FmService \
        libfmradio \
        fmradioif \
        com.ti.fm.fmradioif.xml

#copy firmware
_all_fw_files := $(wildcard device/ti/proprietary-open/wl12xx/wpan/*/*.bts)
PRODUCT_COPY_FILES += $(foreach f,$(_all_fw_files),$(f):system/etc/firmware/$(notdir $(f)) ) \
	system/bluetooth/data/main.conf:system/etc/bluetooth/main.conf
